package uk.senyasenyavski.veilark.helper

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrustEndpointBootstrapTest {
  private val source = """
    vpn_mode = "general"
    [endpoint]
    hostname = "vpn.example.test"
    addresses = ["vpn.example.test:2087"]
    username = "synthetic-user"
    password = "synthetic-password"
    client_random = "synthetic-prefix"
    anti_dpi = false
    skip_verification = false
    has_ipv6 = false
    [listener.tun]
    mtu_size = 1350
  """.trimIndent()

  @Test fun `bootstrap replaces only endpoint address retaining TLS identity and credentials`() {
    val helper = TrustEndpointBootstrap(resolve = { host, budget ->
      assertEquals("vpn.example.test", host); assertTrue(budget in 1..3000)
      TrustDnsAddresses(listOf("192.0.2.1", "2001:db8::1"))
    })
    val result = helper.prepare(source)
    assertEquals(source.replace("addresses = [\"vpn.example.test:2087\"]",
      "addresses = [\"192.0.2.1:2087\"]"), result)
  }

  @Test fun `positive cache expires and never negatively caches failed resolution`() {
    var time = 0L
    var calls = 0
    val helper = TrustEndpointBootstrap(resolve = { _, _ ->
      calls++; if (calls == 1) TrustDnsAddresses(emptyList()) else TrustDnsAddresses(listOf("192.0.2.1"), 2000)
    }, now = { time })
    assertEquals(source, helper.prepare(source))
    assertTrue(helper.prepare(source).contains("192.0.2.1:2087"))
    helper.prepare(source); assertEquals(2, calls)
    time = 2000
    helper.prepare(source); assertEquals(3, calls)
  }

  @Test fun `invalid answers and resolver exceptions retain unchanged native fallback`() {
    for (resolver in listOf<(String, Long) -> TrustDnsAddresses>(
      { _, _ -> TrustDnsAddresses(listOf("bad.example", "999.1.1.1", "::bad::")) },
      { _, _ -> throw IllegalStateException("synthetic DNS failure") },
    )) assertEquals(source, TrustEndpointBootstrap(resolve = resolver).prepare(source))
  }

  @Test fun `literal addresses relay and unknown representations are native owned`() {
    val helper = TrustEndpointBootstrap(resolve = { _, _ -> error("must not resolve") })
    for (address in listOf("192.0.2.1:2087", "[2001:db8::1]:2087", "|relay.example.test:2087")) {
      val input = source.replace("vpn.example.test:2087", address)
      assertEquals(input, helper.prepare(input))
    }
    assertEquals(source.replace("addresses = [", "addresses = unknown ["),
      helper.prepare(source.replace("addresses = [", "addresses = unknown [")))
  }

  @Test fun `multiline array and comments parse while unrelated tables are preserved`() {
    val helper = TrustEndpointBootstrap(resolve = { _, _ -> TrustDnsAddresses(listOf("192.0.2.2")) })
    val input = source.replace("addresses = [\"vpn.example.test:2087\"]",
      "addresses = [\n  'vpn.example.test:2087', # endpoint\n]") +
      "\n[other]\naddresses = [\"other.example.test:443\"]\n"
    val result = helper.prepare(input)
    assertTrue(result.contains("addresses = [\"192.0.2.2:2087\"]"))
    assertTrue(result.contains("[other]\naddresses = [\"other.example.test:443\"]"))
  }

  @Test fun `total resolution budget is shared across endpoint hosts`() {
    var time = 0L
    var calls = 0
    val helper = TrustEndpointBootstrap(resolve = { _, budget ->
      calls++; assertEquals(3000, budget); time += 3000; TrustDnsAddresses(emptyList())
    }, now = { time })
    val input = source.replace("addresses = [\"vpn.example.test:2087\"]",
      "addresses = [\"one.example.test:2087\", \"two.example.test:2087\"]")
    assertEquals(input, helper.prepare(input))
    assertEquals(1, calls)
  }

  @Test fun `cache and address counts are bounded`() {
    var calls = 0
    val helper = TrustEndpointBootstrap(resolve = { _, _ ->
      calls++; TrustDnsAddresses((1..30).map { "192.0.2.$it" })
    })
    for (i in 0..128) helper.prepare(source.replace("vpn.example.test:2087", "node$i.example.test:2087"))
    assertEquals(129, calls)
    helper.prepare(source.replace("vpn.example.test:2087", "node0.example.test:2087"))
    assertEquals(130, calls)
    assertFalse(helper.prepare(source).contains("192.0.2.9:2087"))
  }

  @Test fun `DoH uses pinned connection with normal hostname validation and only public query`() {
    val command = TrustBootstrapDns.command(Path.of("curl.exe"), "vpn.example.test", 2000)
    assertTrue(command.contains("cloudflare-dns.com:443:1.1.1.1"))
    assertTrue(command.contains("Accept: application/dns-json"))
    assertEquals("https://cloudflare-dns.com/dns-query?name=vpn.example.test&type=A", command.last())
    assertFalse(command.any { it in listOf("--insecure", "-k") })
    assertFalse(command.joinToString(" ").contains("synthetic-password"))
  }

  @Test fun `private reserved and malformed names are not sent to external DoH`() {
    for (host in listOf("vpn.local", "vpn.internal", "vpn.test", "localhost", "router.home.arpa", "bad&name=secret.test"))
      assertFalse(TrustBootstrapDns.isDohEligible(host))
    assertTrue(TrustBootstrapDns.isDohEligible("cloudflare.com"))
  }

  @Test fun `DoH schema validates question status and TTL rather than caching arbitrary data`() {
    val answer = """{"Status":0,"Question":[{"name":"vpn.example.test.","type":1}],"Answer":[{"name":"vpn.example.test.","type":5,"data":"alias.example.test.","TTL":200},{"name":"alias.example.test.","type":1,"data":"192.0.2.4","TTL":20},{"name":"unrelated.example.test.","type":1,"data":"192.0.2.99","TTL":1}]}"""
    assertEquals(TrustDnsAddresses(listOf("192.0.2.4"), 20_000), TrustBootstrapDns.parseDoh(answer, "vpn.example.test"))
    assertTrue(TrustBootstrapDns.parseDoh(answer.replace("\"Status\":0", "\"Status\":3"), "vpn.example.test").addresses.isEmpty())
    assertTrue(TrustBootstrapDns.parseDoh(answer, "other.example.test").addresses.isEmpty())
    assertTrue(TrustBootstrapDns.parseDoh("not JSON", "vpn.example.test").addresses.isEmpty())
  }
}

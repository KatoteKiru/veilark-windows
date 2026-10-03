package uk.senyasenyavski.veilark.helper

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import java.util.Base64
import java.nio.file.Path
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlinx.coroutines.runBlocking

class OwnedWinTunCleanupTest {
  private val tunnel = ReadyTunnel(19, 14918723521478656, "Veilark", "sing-tun Tunnel")
  private val adapter = NetworkAdapter(tunnel.index, tunnel.luid, tunnel.alias, tunnel.description, false, 1280, 0, 0)

  @Test fun `cleanup identity is exact and cannot select other adapters`() {
    assertTrue(OwnedWinTunCleanup.sameAdapter(tunnel, adapter))
    assertFalse(OwnedWinTunCleanup.sameAdapter(tunnel, adapter.copy(index = 20)))
    assertFalse(OwnedWinTunCleanup.sameAdapter(tunnel, adapter.copy(luid = tunnel.luid + 1)))
    assertFalse(OwnedWinTunCleanup.sameAdapter(tunnel, adapter.copy(alias = "Other VPN")))
    assertFalse(OwnedWinTunCleanup.sameAdapter(tunnel, adapter.copy(description = "Intel Ethernet")))
    assertFalse(OwnedWinTunCleanup.eligible(tunnel.copy(alias = "Other VPN")))
    assertFalse(OwnedWinTunCleanup.eligible(tunnel.copy(description = "WireGuard Tunnel")))
    assertFalse(OwnedWinTunCleanup.eligible(tunnel.copy(index = 0)))
  }

  @Test fun `only literal WinTun GUID instances are accepted`() {
    assertTrue(OwnedWinTunCleanup.INSTANCE_ID.matches("SWD\\WINTUN\\{DDD8E7A7-D78F-E80D-1F18-1120D0F2573E}"))
    assertFalse(OwnedWinTunCleanup.INSTANCE_ID.matches("SWD\\WINTUN\\*"))
    assertFalse(OwnedWinTunCleanup.INSTANCE_ID.matches("ROOT\\NET\\0001"))
    assertFalse(OwnedWinTunCleanup.INSTANCE_ID.matches("SWD\\WINTUN\\'; Remove-Item *; '"))
    assertTrue(OwnedWinTunCleanup.GUID.matches("{DDD8E7A7-D78F-E80D-1F18-1120D0F2573E}"))
    assertFalse(OwnedWinTunCleanup.GUID.matches("{DDD8E7A7-D78F-E80D-1F18-1120D0F2573E}'"))
  }

  @Test fun `encoded PowerShell preserves registry quoting and Unicode exactly`() {
    val script = OwnedWinTunCleanup.ghostLookupScript("{DDD8E7A7-D78F-E80D-1F18-1120D0F2573E}") + "\n# Проверка кавычек"
    val decoded = String(Base64.getDecoder().decode(OwnedWinTunCleanup.encodedCommand(script)), Charsets.UTF_16LE)
    assertEquals(script, decoded)
  }

  @Test fun `actual Windows argument transport parses cleanup without executing it`() {
    assumeTrue(System.getProperty("os.name").startsWith("Windows"))
    val script = OwnedWinTunCleanup.ghostLookupScript("{DDD8E7A7-D78F-E80D-1F18-1120D0F2573E}")
    val payload = OwnedWinTunCleanup.encodedCommand(script)
    // Parse only: this never executes the registry lookup, PnP query or removal.
    val parseOnly = "\$source = [Text.Encoding]::Unicode.GetString([Convert]::FromBase64String('$payload')); " +
      "[scriptblock]::Create(\$source) | Out-Null; Write-Output 'PARSED'"
    val executable = Path.of(System.getenv("SystemRoot") ?: "C:\\Windows", "System32", "WindowsPowerShell", "v1.0", "powershell.exe")
    val result = ProcessBuilder(listOf(executable.toString()) + OwnedWinTunCleanup.commandArguments(parseOnly))
      .redirectErrorStream(true).start().capture(5_000)
    assertTrue(result.succeeded, "Parse-only PowerShell helper failed")
    assertEquals("PARSED", result.output.trim())
  }

  @Test fun `cleanup waits for owned adapter state propagation without relaxing ownership`() = runBlocking {
    var observations = 0
    var pauses = 0
    assertTrue(OwnedWinTunCleanup.awaitStoppedAdapter(tunnel,
      lookup = { adapter.copy(operational = observations++ < 2) }, pause = { pauses++ }))
    assertEquals(3, observations)
    assertEquals(2, pauses)
    observations = 0
    assertFalse(OwnedWinTunCleanup.awaitStoppedAdapter(tunnel,
      lookup = { observations++; adapter.copy(operational = true) }, pause = {}))
    assertEquals(21, observations)
    assertFalse(OwnedWinTunCleanup.awaitStoppedAdapter(tunnel,
      lookup = { adapter.copy(luid = tunnel.luid + 1) }, pause = {}))
    assertTrue(OwnedWinTunCleanup.awaitStoppedAdapter(tunnel, lookup = { null }, pause = {}))
  }
}

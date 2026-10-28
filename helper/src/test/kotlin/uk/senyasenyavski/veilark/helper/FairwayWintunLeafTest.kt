package uk.senyasenyavski.veilark.helper

import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.IPHlpAPI
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.UUID
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Actual driver lifecycle only. No Core authentication, addresses, routes or DNS. */
class FairwayWintunLeafTest {
  @Test fun `actual custom Wintun adapter is identified and removed on isolated CI`() {
    assumeTrue(System.getenv("VEILARK_TEST_FAIRWAY_WINTUN_LEAF") == "1")
    check(System.getenv("GITHUB_ACTIONS") == "true") { "Only GitHub Actions is allowed" }
    check(System.getenv("RUNNER_ENVIRONMENT") == "github-hosted") { "Only a disposable GitHub-hosted runner is allowed" }
    check(System.getProperty("os.name").startsWith("Windows"))
    check(ElevationManager().isElevated()) { "Driver lifecycle requires an elevated CI runner" }
    val path = Path.of(requireNotNull(System.getenv("VEILARK_TEST_WINTUN_DLL"))).toAbsolutePath()
    val digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))
      .joinToString("") { "%02x".format(it) }
    assertEquals("e5da8447dc2c320edc0fc52fa01885c103de8c118481f683643cacc3220dafce", digest)
    check(WindowsNetwork.tunnels().none { it.operational }) { "An isolated runner is required" }
    val guid = UUID.randomUUID()
    val identity = FairwayAdapterIdentity(guid)
    check(WindowsNetwork.adapters().none { identity.matchesWindowsGuid(it.interfaceGuid) })
    NativeLibrary.getInstance(path.toString()).use { library ->
      fun function(name: String) = library.getFunction(name, Function.ALT_CONVENTION)
      val create = function("WintunCreateAdapter")
      val start = function("WintunStartSession")
      val end = function("WintunEndSession")
      val close = function("WintunCloseAdapter")
      val adapter = Memory(16).use { bytes ->
        bytes.write(0, identity.windowsGuidBytes(), 0, 16)
        requireNotNull(create.invokePointer(arrayOf(WString("Veilark Core beta"), WString("Veilark"), bytes))) {
          "WintunCreateAdapter failed: os=${Native.getLastError()}"
        }
      }
      var session: Pointer? = null
      var ownedLuid = 0L
      try {
        Memory(8).use { output ->
          output.clear()
          function("WintunGetAdapterLUID").invokeVoid(arrayOf(adapter, output))
          ownedLuid = output.getLong(0)
          check(ownedLuid != 0L) { "Wintun did not return an adapter LUID" }
        }
        session = requireNotNull(start.invokePointer(arrayOf(adapter, 0x400000))) {
          "WintunStartSession failed: os=${Native.getLastError()}"
        }
        val actual = awaitOwnedAdapter(identity)
        assertEquals(ownedLuid, actual.luid)
        assertEquals("Veilark Tunnel", actual.description)
        assertTrue(actual.isTunnel, "The generic active-VPN check must detect the native custom type")
        assertTrue(actual.isOwnedFairway(identity), "The product readiness check must accept its actual native adapter")
        val refreshed = requireNotNull(WindowsNetwork.refresh(actual.luid))
        assertTrue(refreshed.isOwnedFairway(identity))
        assertTrue(refreshed.bytesIn >= 0 && refreshed.bytesOut >= 0, "OS counters must be readable")
        println("Actual Wintun leaf: custom type, exact GUID, operational state and counters verified; no IP/routes/DNS configured")
      } finally {
        try { session?.let { end.invokeVoid(arrayOf(it)) } }
        finally { close.invokeVoid(arrayOf(adapter)) }
      }
      val deadline = System.nanoTime() + 10_000_000_000L
      while (true) {
        val row = IPHlpAPI.MIB_IF_ROW2().apply { InterfaceLuid = ownedLuid }
        val result = IPHlpAPI.INSTANCE.GetIfEntry2(row)
        if (result == 2 || result == 1168) break // ERROR_FILE_NOT_FOUND / ERROR_NOT_FOUND.
        check(result == 0) { "Cannot prove adapter absence: GetIfEntry2=$result" }
        check(System.nanoTime() < deadline) { "Owned Wintun adapter remains after EndSession/CloseAdapter" }
        Thread.sleep(100)
      }
      check(WindowsNetwork.adapters().none { identity.matchesWindowsGuid(it.interfaceGuid) })
      println("Actual Wintun leaf: EndSession/CloseAdapter and Windows table absence verified; this is not a full Fairway Internet test")
    }
  }

  private fun awaitOwnedAdapter(identity: FairwayAdapterIdentity): NetworkAdapter {
    val deadline = System.nanoTime() + 10_000_000_000L
    while (true) {
      WindowsNetwork.adapters().singleOrNull { it.isOwnedFairway(identity) }?.let { return it }
      check(System.nanoTime() < deadline) { "Custom Wintun adapter did not become operational in the Windows table" }
      Thread.sleep(100)
    }
  }
}

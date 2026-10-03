package uk.senyasenyavski.veilark.helper

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NativeProcessDiagnosticsTest {
  @Test
  fun `missing executable records safe stage filename and Win32 code`() {
    val entries = mutableListOf<String>()

    val error = assertFailsWith<VpnStartException> {
      NativeProcessDiagnostics.launch(
        NativeProcessDiagnostics.Executable.TRUSTTUNNEL_CLIENT,
        NativeProcessDiagnostics.Stage.CLIENT_START,
        start = { throw IOException("CreateProcess error=2, secret tt://user:pass@example.invalid") },
        diagnostic = entries::add,
      )
    }

    assertTrue(error.message.orEmpty().contains("stage=client-start"))
    assertTrue(error.message.orEmpty().contains("executable=trusttunnel_client.exe"))
    assertTrue(error.message.orEmpty().contains("win32=0x00000002"))
    assertFalse(error.message.orEmpty().contains("tt://"))
    assertEquals(1, entries.size)
    assertEquals(error.message, entries.single())
  }

  @Test
  fun `unlaunchable wizard preserves access denied code without argument values`() {
    val entries = mutableListOf<String>()

    assertFailsWith<VpnStartException> {
      NativeProcessDiagnostics.launch(
        NativeProcessDiagnostics.Executable.SETUP_WIZARD,
        NativeProcessDiagnostics.Stage.WIZARD_START,
        start = { throw IOException("CreateProcess error=5, denied secret tt://token") },
        diagnostic = entries::add,
      )
    }

    assertTrue(entries.single().contains("stage=wizard-start"))
    assertTrue(entries.single().contains("executable=setup_wizard.exe"))
    assertTrue(entries.single().contains("win32=0x00000005"))
    assertFalse(entries.single().contains("token"))
  }

  @Test
  fun `diagnostic writer failure does not replace native launch failure`() {
    val error = assertFailsWith<VpnStartException> {
      NativeProcessDiagnostics.launch(
        NativeProcessDiagnostics.Executable.TRUSTTUNNEL_CLIENT,
        NativeProcessDiagnostics.Stage.CLIENT_START,
        start = { throw IOException("CreateProcess error=2") },
        diagnostic = { throw IOException("log disk unavailable") },
      )
    }

    assertTrue(error.message.orEmpty().contains("stage=client-start"))
    assertTrue(error.message.orEmpty().contains("win32=0x00000002"))
  }

  @Test
  fun `nonzero and timed out version checks record exit status in hex`() {
    val entries = mutableListOf<String>()

    NativeProcessDiagnostics.recordExitFailure(
      NativeProcessDiagnostics.Executable.TRUSTTUNNEL_CLIENT,
      NativeProcessDiagnostics.Stage.CLIENT_VERSION,
      exitCode = 193,
      timedOut = false,
      diagnostic = entries::add,
    )
    NativeProcessDiagnostics.recordExitFailure(
      NativeProcessDiagnostics.Executable.TRUSTTUNNEL_CLIENT,
      NativeProcessDiagnostics.Stage.CLIENT_VERSION,
      exitCode = null,
      timedOut = true,
      diagnostic = entries::add,
    )

    assertEquals(
      "Native process failed stage=client-version executable=trusttunnel_client.exe exit=0x000000C1",
      entries[0],
    )
    assertEquals(
      "Native process failed stage=client-version executable=trusttunnel_client.exe exit=timeout",
      entries[1],
    )
  }

  @Test
  fun `exit failure diagnostic writer failure is ignored`() {
    NativeProcessDiagnostics.recordExitFailure(
      NativeProcessDiagnostics.Executable.SETUP_WIZARD,
      NativeProcessDiagnostics.Stage.WIZARD_START,
      exitCode = 5,
      timedOut = false,
      diagnostic = { throw IOException("log ACL denied") },
    )
  }
}

package uk.senyasenyavski.veilark.helper

import uk.senyasenyavski.veilark.model.VpnStatusCode
import java.util.Locale

/** Safe, bounded diagnostics for native executable startup, without argv or path data. */
internal object NativeProcessDiagnostics {
  enum class Executable(val fileName: String) {
    TRUSTTUNNEL_CLIENT("trusttunnel_client.exe"),
    SETUP_WIZARD("setup_wizard.exe"),
  }

  enum class Stage(val label: String) {
    CLIENT_START("client-start"),
    WIZARD_START("wizard-start"),
    CLIENT_VERSION("client-version"),
  }

  fun <T> launch(
    executable: Executable,
    stage: Stage,
    diagnostic: (String) -> Unit = SafeLog::write,
    start: () -> T,
  ): T = try {
    start()
  } catch (error: Exception) {
    val entry = "Native launch failed stage=${stage.label} executable=${executable.fileName} " +
      "win32=${win32Code(error)}"
    runCatching { diagnostic(entry) }
    throw VpnStartException(
      code = VpnStatusCode.CORE_START_FAILED,
      message = entry,
      detail = "TrustTunnel/${stage.label}",
      cause = error,
    )
  }

  fun recordExitFailure(
    executable: Executable,
    stage: Stage,
    exitCode: Int?,
    timedOut: Boolean,
    diagnostic: (String) -> Unit = SafeLog::write,
  ) {
    val status = if (timedOut) "timeout" else exitCode?.let(::exitCodeHex) ?: "unknown"
    runCatching {
      diagnostic(
        "Native process failed stage=${stage.label} executable=${executable.fileName} exit=$status",
      )
    }
  }

  private fun win32Code(error: Throwable): String {
    generateSequence(error) { it.cause }.forEach { current ->
      val reflected = runCatching {
        current.javaClass.methods
          .firstOrNull { it.name == "getErrorCode" && it.parameterCount == 0 }
          ?.invoke(current) as? Number
      }.getOrNull()?.toInt()
      if (reflected != null) return exitCodeHex(reflected)

      val fromMessage = WIN32_ERROR.find(current.message.orEmpty())?.groupValues?.get(1)?.toIntOrNull()
      if (fromMessage != null) return exitCodeHex(fromMessage)
    }
    return "unknown"
  }

  fun exitCodeHex(code: Int): String = "0x" + java.lang.Long.toHexString(code.toLong() and 0xffff_ffffL)
    .uppercase(Locale.ROOT).padStart(8, '0')

  private val WIN32_ERROR = Regex("(?i)\\berror\\s*=\\s*(\\d+)")
}

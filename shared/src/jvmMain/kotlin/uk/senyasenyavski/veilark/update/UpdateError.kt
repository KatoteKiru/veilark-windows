package uk.senyasenyavski.veilark.update

import org.json.JSONException

/** Stable, language-independent OTA failure categories rendered by the UI. */
enum class UpdateErrorCode {
  CheckFailed,
  HttpStatus,
  ManifestInvalid,
  SignatureInvalid,
  UntrustedAddress,
  DownloadFailed,
  DownloadIncomplete,
  ChecksumMismatch,
  InstallerMissing,
  InstallFailed,
}

/** [detail] carries only non-translatable data, such as an HTTP status code. */
data class UpdateError(val code: UpdateErrorCode, val detail: String = "")

/**
 * OTA failure with a stable [error]. It remains an [IllegalArgumentException]
 * so existing callers and the verification contract are unchanged; the
 * message is technical journal text and is never shown or parsed by the UI.
 */
class UpdateException(
  val error: UpdateError,
  message: String,
  cause: Throwable? = null,
) : IllegalArgumentException(message, cause) {
  constructor(code: UpdateErrorCode, message: String, detail: String = "") :
    this(UpdateError(code, detail), message)
}

/** Maps any failure to a UI category without inspecting localized text. */
fun Throwable.toUpdateError(fallback: UpdateErrorCode): UpdateError = when (this) {
  is UpdateException -> error
  is JSONException -> UpdateError(UpdateErrorCode.ManifestInvalid)
  else -> (cause as? UpdateException)?.error ?: UpdateError(fallback)
}

internal inline fun ensureUpdate(condition: Boolean, code: UpdateErrorCode, message: () -> String) {
  if (!condition) throw UpdateException(code, message())
}

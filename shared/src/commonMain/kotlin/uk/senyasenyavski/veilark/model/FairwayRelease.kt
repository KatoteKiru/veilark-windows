package uk.senyasenyavski.veilark.model

/** Immutable release metadata. Never read these values from a subscription,
 * environment variable, command line or a file next to the native library.
 * Only promote together with the accepted native artifact and enrollment path.
 */
object FairwayRelease {
  const val accepted = true
  const val nativeSize = 4500480L
  const val nativeSha256 = "699c6fad51ffe6341cacb76f378d3af0a019f155edca4a8d8573443a0048c308"
}

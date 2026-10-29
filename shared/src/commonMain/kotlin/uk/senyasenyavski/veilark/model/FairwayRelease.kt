package uk.senyasenyavski.veilark.model

/** Immutable release metadata. Never read these values from a subscription,
 * environment variable, command line or a file next to the native library.
 * Only promote together with the accepted native artifact and enrollment path.
 */
object FairwayRelease {
  const val accepted = true
  const val nativeSize = 4597261L
  const val nativeSha256 = "c7d8190754a3ad066a0d15a03d2e36cc746397addd757e9f874e75ee48ba8d1d"
}

package uk.senyasenyavski.veilark.model

/** Immutable release metadata. Never read these values from a subscription,
 * environment variable, command line or a file next to the native library.
 * Only promote together with the accepted native artifact and enrollment path.
 */
object FairwayRelease {
  const val accepted = true
  const val nativeSize = 4501504L
  const val nativeSha256 = "2af7d100a4b3bdadd1eba503bd77a11652ecd0d55838bde2b0bb2de1890f879f"
}

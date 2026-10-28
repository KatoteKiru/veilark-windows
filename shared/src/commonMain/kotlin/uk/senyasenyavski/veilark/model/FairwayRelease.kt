package uk.senyasenyavski.veilark.model

/** Immutable release metadata. Never read these values from a subscription,
 * environment variable, command line or a file next to the native library.
 * Only promote together with the accepted native artifact and enrollment path.
 */
object FairwayRelease {
  const val accepted = true
  const val nativeSize = 4567446L
  const val nativeSha256 = "fa516bfda35a1d5f743ec96a82ecc386f2a0ca7f20b8eb74f603a075c38855b6"
}

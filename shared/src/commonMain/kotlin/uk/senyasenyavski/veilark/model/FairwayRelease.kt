package uk.senyasenyavski.veilark.model

/** Immutable release metadata. Never read these values from a subscription,
 * environment variable, command line or a file next to the native library.
 * Only promote together with the accepted native artifact and enrollment path.
 */
object FairwayRelease {
  const val accepted = true
  const val nativeSize = 4604148L
  const val nativeSha256 = "8363d72f8f76c9b660f05a527b52ad15e1c70751013a07f3f108689a647a9041"
}

package uk.senyasenyavski.veilark.model

/** Immutable release metadata. Never read these values from a subscription,
 * environment variable, command line or a file next to the native library.
 * Only promote together with the accepted native artifact and enrollment path.
 */
object FairwayRelease {
  const val accepted = true
  const val nativeSize = 4600586L
  const val nativeSha256 = "cf42c32ffafb34a5c6a4715790d55c74d075742172e9821728586367decab8d4"
}

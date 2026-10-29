package uk.senyasenyavski.veilark.model

/** Immutable release metadata. Never read these values from a subscription,
 * environment variable, command line or a file next to the native library.
 * Only promote together with the accepted native artifact and enrollment path.
 */
object FairwayRelease {
  const val accepted = true
  const val nativeSize = 4605525L
  const val nativeSha256 = "c3a748ae6b98ca779d26770e81b6019d80161e0f3cbed051b4d9ffde86004623"
}

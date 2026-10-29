package uk.senyasenyavski.veilark.model

/** Immutable release metadata. Never read these values from a subscription,
 * environment variable, command line or a file next to the native library.
 * Only promote together with the accepted native artifact and enrollment path.
 */
object FairwayRelease {
  const val accepted = true
  const val nativeSize = 4590113L
  const val nativeSha256 = "a77e09e4588b246be5fc1ae1366f9b12954030789d448b2fb4ff76730a49013a"
}

package uk.senyasenyavski.veilark.helper

import uk.senyasenyavski.veilark.model.FairwayRelease

/** Caller supplies immutable release/build metadata, NEVER a profile or sibling-file pin.
 * Registration remains disabled until the product release and actual host gates pass.
 */
internal object FairwayControllerFactory {
  fun productControllers(): List<EngineController> = buildList {
    add(SingBoxProcessController())
    add(TrustTunnelProcessController())
    if (FairwayRelease.accepted) {
      // Validate metadata at construction, load DLL only during actual start.
      add(create(NativeArtifactPin(FairwayRelease.nativeSize, FairwayRelease.nativeSha256)))
    }
  }

  fun create(trustedReleasePin: NativeArtifactPin): FairwayEngineController = FairwayEngineController(
    loadLibrary = {
      FairwayNativeLibrary.load(RuntimeResourceLocator.requireFile("veilark_windows_native.dll"), trustedReleasePin)
    },
    prepareHost = { endpoint ->
      val uplink = WindowsNetwork.coreUplink(endpoint)
      val driver = RuntimeResourceLocator.requireFile("wintun.dll")
      val identity = FairwayAdapterIdentityStore().loadOrCreate()
      FairwayHostInputs(driver, uplink, identity)
    },
  )
}

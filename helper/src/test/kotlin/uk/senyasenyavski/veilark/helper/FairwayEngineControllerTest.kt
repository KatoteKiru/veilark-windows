package uk.senyasenyavski.veilark.helper

import com.sun.jna.Platform
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlin.test.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import uk.senyasenyavski.veilark.model.*

class FairwayEngineControllerTest {
  private fun profile() = Profile("core", "Fairway", VpnEngine.VeilarkCore, "unparsed synthetic config", emptyList(), "test")

  @Test fun `product gate rejects before DLL or host preparation`() = runBlocking {
    val controller = FairwayEngineController(
      loadLibrary = { error("Must not load") }, prepareHost = { error("Must not prepare host") },
    )
    val failure = assertFailsWith<VpnStartException> { controller.start(profile()) }
    assertEquals(VpnStatusCode.CORE_NOT_READY, failure.code)
    assertFalse(controller.hasOwnedResources())
    controller.stop()
    assertFalse(controller.isAlive())
    assertNull(controller.statistics())
  }

  @Test fun `auth only DLL cannot create owner or prepare host even with explicit app test gate`() = runBlocking {
    val dll = System.getenv("VEILARK_NATIVE_TEST_DLL")
    assumeTrue(Platform.isWindows() && !dll.isNullOrBlank(), "Explicit private development DLL required")
    val controller = FairwayEngineController(
      loadLibrary = { FairwayNativeLibrary.load(Path.of(dll!!), NativeArtifactPin(32_646_791,
        "8fd89a1791b40feeeba06b852be8f686cf0b492d6dfb641ebbbd6139cb1d46dd")) },
      prepareHost = { error("Must not prepare host") }, productAccepted = { true },
    )
    try {
      val failure = assertFailsWith<VpnStartException> { controller.start(profile()) }
      assertEquals(VpnStatusCode.CORE_NOT_READY, failure.code)
      assertTrue(controller.hasOwnedResources()) // Loaded DLL retained until explicit stop.
      assertFalse(controller.isAlive())
      val cancelled = launch {
        try { awaitCancellation() } finally { controller.stop() }
      }
      yield()
      cancelled.cancelAndJoin()
      assertFalse(controller.hasOwnedResources())
    } finally { controller.stop() }
    assertFalse(controller.hasOwnedResources())
    controller.stop()
  }
}

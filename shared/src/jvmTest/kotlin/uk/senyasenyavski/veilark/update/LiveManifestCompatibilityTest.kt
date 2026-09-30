package uk.senyasenyavski.veilark.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Pins OTA compatibility to the manifest actually served to installed clients:
 * the production trust key, host and port must keep accepting it byte for byte.
 */
class LiveManifestCompatibilityTest {
  private val liveManifest: String = checkNotNull(
    javaClass.getResource("/ota/live-manifest-0.3.21.json"),
  ).readText(Charsets.UTF_8)

  @Test
  fun `published 0_3_21 manifest verifies with the production key`() {
    val update = assertNotNull(UpdateClient(currentVersionCode = 320).parseAvailableUpdate(liveManifest))
    assertEquals(321, update.versionCode)
    assertEquals("0.3.21", update.versionName)
    assertEquals(130592256L, update.size)
  }

  @Test
  fun `published manifest is not offered to an up-to-date client`() {
    assertNull(UpdateClient(currentVersionCode = 321).parseAvailableUpdate(liveManifest))
  }
}

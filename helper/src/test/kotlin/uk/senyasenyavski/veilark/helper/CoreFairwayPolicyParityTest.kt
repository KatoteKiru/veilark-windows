package uk.senyasenyavski.veilark.helper

import kotlin.test.*

class CoreFairwayPolicyParityTest {
  private fun asset() = checkNotNull(javaClass.getResourceAsStream("/${CoreFairwayRuPolicy.ASSET}"))
    .use { it.readBytes() }

  @Test fun `Windows ships every exact Android policy prefix without legacy Geo conversion`() {
    val raw = asset()
    val expected = raw.toString(Charsets.US_ASCII).trimEnd('\n').split('\n')
    assertEquals(expected, CoreFairwayRuPolicy.bundledPrefixes())
    val policy = CoreFairwayRuPolicy.prepare(raw)
    assertEquals(8662, policy.networks.size)
    assertEquals(45_223_232L, policy.directRuIpv4)
  }

  @Test fun `modified policy is rejected even after the valid policy was cached`() {
    CoreFairwayRuPolicy.bundledPrefixes()
    val changed = asset().also { it[0] = (it[0].toInt() xor 1).toByte() }
    assertFailsWith<IllegalArgumentException> { CoreFairwayRuPolicy.prepare(changed) }
  }
}

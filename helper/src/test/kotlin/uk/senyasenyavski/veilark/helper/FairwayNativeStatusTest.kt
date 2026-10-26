package uk.senyasenyavski.veilark.helper

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FairwayNativeStatusTest {
  private fun status(phase: Int, outcome: Int = 0, alive: Boolean = true) = FairwayNativeLibrary.Status(
    phase, outcome, alive, true, 0, false, byteArrayOf(10, 0, 0, 2), 1360, 0, 0, 0,
  )

  @Test fun `terminal error wins over live worker and otherwise matching startup or running phase`() {
    for (phase in listOf(3, 4)) {
      for (outcome in listOf(1, 35, Int.MAX_VALUE)) {
        assertFalse(status(phase, outcome).isActivePhase(phase), "phase=$phase outcome=$outcome")
      }
    }
  }

  @Test fun `authentication and running remain distinct and require a live worker`() {
    assertTrue(status(3).isActivePhase(3))
    assertTrue(status(4).isActivePhase(4))
    assertFalse(status(3).isActivePhase(4))
    assertFalse(status(4, alive = false).isActivePhase(4))
  }
}

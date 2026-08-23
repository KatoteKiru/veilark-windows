package uk.senyasenyavski.veilark.diagnostics

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HealthCheckerTest {
  @Test
  fun `successful and redirect responses are reachable`() {
    assertTrue(HealthChecker.isReachable(204, emptySet()))
    assertTrue(HealthChecker.isReachable(302, emptySet()))
  }

  @Test
  fun `declared authentication failures prove API reachability`() {
    assertTrue(HealthChecker.isReachable(401, setOf(401, 403)))
    assertTrue(HealthChecker.isReachable(403, setOf(401, 403)))
    assertFalse(HealthChecker.isReachable(500, setOf(401, 403)))
  }
}

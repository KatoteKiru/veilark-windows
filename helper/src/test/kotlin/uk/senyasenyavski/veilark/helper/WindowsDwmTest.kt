package uk.senyasenyavski.veilark.helper

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WindowsDwmTest {
  @Test
  fun `non-windows and old builds request nothing`() {
    assertEquals(emptyList(), WindowsDwm.plan(null, dark = true))
    assertEquals(emptyList(), WindowsDwm.plan(17_134, dark = true))
  }

  @Test
  fun `windows 10 gets only the dark caption with the legacy fallback`() {
    val plan = WindowsDwm.plan(19_045, dark = true)
    assertEquals(
      listOf(WindowsDwm.AttributeRequest(20, 1, fallback = 19)),
      plan,
    )
    assertEquals(listOf(WindowsDwm.AttributeRequest(19, 0)), WindowsDwm.plan(18_363, dark = false))
  }

  @Test
  fun `windows 11 22H2 adds rounded corners and mica`() {
    assertEquals(1, WindowsDwm.plan(22_000, dark = false).size)
    val plan = WindowsDwm.plan(22_631, dark = false)
    assertEquals(listOf(20, 33, 38), plan.map { it.attribute })
    assertEquals(listOf(0, 2, 2), plan.map { it.value })
  }

  @Test
  fun `applying to a headless or non-windows window never throws`() {
    if (java.awt.GraphicsEnvironment.isHeadless()) return
    val frame = java.awt.Frame()
    try {
      assertTrue(WindowsDwm.apply(frame, dark = true) >= 0)
    } finally {
      frame.dispose()
    }
  }
}

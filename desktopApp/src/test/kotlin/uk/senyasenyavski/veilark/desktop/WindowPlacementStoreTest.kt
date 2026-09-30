package uk.senyasenyavski.veilark.desktop

import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WindowPlacementStoreTest {
  private val primary = Rectangle(0, 0, 1920, 1040)
  private val secondary = Rectangle(1920, 0, 1280, 1024)

  @Test
  fun `visible placement is kept`() {
    val saved = SavedWindowBounds(100, 80, 560, 720)
    assertEquals(saved, WindowPlacementStore.sanitize(saved, listOf(primary), 460, 480))
  }

  @Test
  fun `placement on a disconnected monitor falls back to defaults`() {
    val saved = SavedWindowBounds(2100, 100, 560, 720)
    assertEquals(saved, WindowPlacementStore.sanitize(saved, listOf(primary, secondary), 460, 480))
    assertNull(WindowPlacementStore.sanitize(saved, listOf(primary), 460, 480))
  }

  @Test
  fun `size is clamped to the minimum and to the screen`() {
    val tiny = WindowPlacementStore.sanitize(SavedWindowBounds(10, 10, 100, 100), listOf(primary), 460, 480)
    assertEquals(460, tiny?.width)
    assertEquals(480, tiny?.height)
    val huge = WindowPlacementStore.sanitize(SavedWindowBounds(0, 0, 5000, 5000, maximized = true), listOf(primary), 460, 480)
    assertEquals(SavedWindowBounds(0, 0, 1920, 1040, maximized = true), huge)
  }

  @Test
  fun `title bar above the screen is moved back into reach`() {
    val result = WindowPlacementStore.sanitize(SavedWindowBounds(100, -10, 560, 720), listOf(primary), 460, 480)
    assertEquals(0, result?.y)
    assertNull(WindowPlacementStore.sanitize(SavedWindowBounds(100, -400, 560, 720), listOf(primary), 460, 480))
    val partly = WindowPlacementStore.sanitize(SavedWindowBounds(100, 1030, 560, 720), listOf(primary), 460, 480)
    assertEquals(1040 - 32, partly?.y)
  }

  @Test
  fun `store round-trips through its persistence functions`() {
    var stored: SavedWindowBounds? = null
    val store = WindowPlacementStore(read = { stored }, write = { stored = it })
    assertNull(store.load())
    store.save(SavedWindowBounds(1, 2, 600, 700, maximized = true))
    assertEquals(SavedWindowBounds(1, 2, 600, 700, maximized = true), store.load())
    val broken = WindowPlacementStore(read = { error("registry unavailable") }, write = { error("read-only") })
    assertNull(broken.load())
    broken.save(SavedWindowBounds(1, 2, 600, 700))
  }
}

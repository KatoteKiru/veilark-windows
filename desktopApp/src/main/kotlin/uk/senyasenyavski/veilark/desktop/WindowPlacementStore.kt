package uk.senyasenyavski.veilark.desktop

import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.util.prefs.Preferences

/** Last window bounds in AWT user-space pixels (DPI-independent on Windows). */
internal data class SavedWindowBounds(
  val x: Int,
  val y: Int,
  val width: Int,
  val height: Int,
  val maximized: Boolean = false,
)

/**
 * Remembers window size and position between launches. Stores only geometry
 * in the current user's Preferences (HKCU), never any VPN data.
 */
internal class WindowPlacementStore(
  private val read: () -> SavedWindowBounds?,
  private val write: (SavedWindowBounds) -> Unit,
) {
  fun load(): SavedWindowBounds? = runCatching { read() }.getOrNull()

  fun save(placement: SavedWindowBounds) {
    runCatching { write(placement) }
  }

  companion object {
    private const val NODE = "uk/senyasenyavski/veilark/window"

    fun persistent(): WindowPlacementStore {
      val prefs = runCatching { Preferences.userRoot().node(NODE) }.getOrNull()
      return WindowPlacementStore(
        read = {
          prefs?.takeIf { it.getInt("width", -1) > 0 }?.let {
            SavedWindowBounds(
              x = it.getInt("x", 0),
              y = it.getInt("y", 0),
              width = it.getInt("width", 0),
              height = it.getInt("height", 0),
              maximized = it.getBoolean("maximized", false),
            )
          }
        },
        write = { placement ->
          prefs?.apply {
            putInt("x", placement.x)
            putInt("y", placement.y)
            putInt("width", placement.width)
            putInt("height", placement.height)
            putBoolean("maximized", placement.maximized)
            flush()
          }
        },
      )
    }

    fun screenBounds(): List<Rectangle> = runCatching {
      if (GraphicsEnvironment.isHeadless()) return@runCatching emptyList()
      GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map { it.defaultConfiguration.bounds }
    }.getOrDefault(emptyList())

    /**
     * Returns a placement that is at least [minWidth] x [minHeight], no larger
     * than the screen it is on, and whose title bar is reachable on one of
     * [screens]. Returns `null` (use defaults) if it would be off-screen, for
     * example after a monitor was disconnected.
     */
    fun sanitize(
      placement: SavedWindowBounds,
      screens: List<Rectangle>,
      minWidth: Int,
      minHeight: Int,
    ): SavedWindowBounds? {
      if (placement.width <= 0 || placement.height <= 0 || screens.isEmpty()) return null
      val titleBar = Rectangle(placement.x, placement.y, placement.width, TITLE_BAR_PROBE)
      val screen = screens.maxByOrNull { overlapArea(it, titleBar) } ?: return null
      val visible = titleBar.intersection(screen)
      if (visible.isEmpty || visible.width < MIN_VISIBLE_TITLE_BAR) return null
      val width = placement.width.coerceIn(minOf(minWidth, screen.width), screen.width)
      val height = placement.height.coerceIn(minOf(minHeight, screen.height), screen.height)
      val x = placement.x.coerceIn(screen.x - width + MIN_VISIBLE_TITLE_BAR, screen.x + screen.width - MIN_VISIBLE_TITLE_BAR)
      val y = placement.y.coerceIn(screen.y, screen.y + screen.height - TITLE_BAR_PROBE)
      return placement.copy(x = x, y = y, width = width, height = height)
    }

    private fun overlapArea(a: Rectangle, b: Rectangle): Long {
      val intersection = a.intersection(b)
      return if (intersection.isEmpty) 0L else intersection.width.toLong() * intersection.height
    }

    private const val TITLE_BAR_PROBE = 32
    private const val MIN_VISIBLE_TITLE_BAR = 96
  }
}

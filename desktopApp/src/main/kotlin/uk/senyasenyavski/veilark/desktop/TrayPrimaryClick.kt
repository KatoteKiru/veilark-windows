package uk.senyasenyavski.veilark.desktop

import kotlinx.coroutines.delay
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

/**
 * Compose's `Tray` only exposes AWT's action event, which Windows fires on a
 * double-click. Windows users expect a single left click to open the app, so
 * a mouse listener is attached to this process's tray icon once Compose has
 * added it. Runs on the Swing/EDT dispatcher of the application scope.
 */
internal object TrayPrimaryClick {
  private class Listener(private val onPrimaryClick: () -> Unit) : MouseAdapter() {
    override fun mouseClicked(event: MouseEvent) {
      if (event.button == MouseEvent.BUTTON1 && event.clickCount == 1) onPrimaryClick()
    }
  }

  suspend fun install(onPrimaryClick: () -> Unit) {
    if (!runCatching { SystemTray.isSupported() }.getOrDefault(false)) return
    // The icon is added by Compose right after the first composition; poll
    // briefly instead of relying on effect ordering.
    repeat(ATTEMPTS) {
      val icons = runCatching { SystemTray.getSystemTray().trayIcons.toList() }.getOrDefault(emptyList())
      if (icons.isNotEmpty()) {
        icons.forEach { attach(it, onPrimaryClick) }
        return
      }
      delay(POLL_MILLIS)
    }
  }

  internal fun attach(icon: TrayIcon, onPrimaryClick: () -> Unit) {
    if (icon.mouseListeners.none { it is Listener }) icon.addMouseListener(Listener(onPrimaryClick))
  }

  private const val ATTEMPTS = 40
  private const val POLL_MILLIS = 250L
}

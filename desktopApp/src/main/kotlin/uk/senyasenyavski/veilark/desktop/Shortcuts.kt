package uk.senyasenyavski.veilark.desktop

import androidx.compose.ui.input.key.Key

/** Window-level keyboard shortcuts (documented in DESIGN.md). */
internal sealed interface DesktopShortcut {
  data class Navigate(val destination: Destination) : DesktopShortcut
  /** Ctrl+Shift+C: connect, or stop the current connection/attempt. */
  data object ToggleConnection : DesktopShortcut
  /** Ctrl+W: hide the window to the tray; the VPN keeps running. */
  data object HideToTray : DesktopShortcut
}

/**
 * Pure mapping so it is unit-testable. Only Ctrl-chords are used, so typing in
 * text fields is never intercepted; Alt-chords stay with Windows.
 */
internal fun desktopShortcut(key: Key, ctrl: Boolean, shift: Boolean, alt: Boolean): DesktopShortcut? {
  if (!ctrl || alt) return null
  if (shift) return if (key == Key.C) DesktopShortcut.ToggleConnection else null
  val index = NAVIGATION_KEYS.indexOf(key)
  // Visual order: bottom bar left to right, then the "More" menu.
  if (index >= 0) return (PrimaryDestinations + OverflowDestinations).getOrNull(index)?.let(DesktopShortcut::Navigate)
  return if (key == Key.W) DesktopShortcut.HideToTray else null
}

private val NAVIGATION_KEYS = listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six)

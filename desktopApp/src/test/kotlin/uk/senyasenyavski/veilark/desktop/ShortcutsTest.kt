package uk.senyasenyavski.veilark.desktop

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ShortcutsTest {
  @Test
  fun `ctrl digits navigate in tab order`() {
    assertEquals(DesktopShortcut.Navigate(Destination.Home), desktopShortcut(Key.One, ctrl = true, shift = false, alt = false))
    assertEquals(DesktopShortcut.Navigate(Destination.Updates), desktopShortcut(Key.Four, ctrl = true, shift = false, alt = false))
    assertEquals(DesktopShortcut.Navigate(Destination.Diagnostics), desktopShortcut(Key.Five, ctrl = true, shift = false, alt = false))
    assertEquals(DesktopShortcut.Navigate(Destination.Logs), desktopShortcut(Key.Six, ctrl = true, shift = false, alt = false))
  }

  @Test
  fun `connection and tray chords`() {
    assertEquals(DesktopShortcut.ToggleConnection, desktopShortcut(Key.C, ctrl = true, shift = true, alt = false))
    assertEquals(DesktopShortcut.HideToTray, desktopShortcut(Key.W, ctrl = true, shift = false, alt = false))
  }

  @Test
  fun `plain typing, copy and alt chords are never intercepted`() {
    assertNull(desktopShortcut(Key.One, ctrl = false, shift = false, alt = false))
    assertNull(desktopShortcut(Key.C, ctrl = true, shift = false, alt = false))
    assertNull(desktopShortcut(Key.V, ctrl = true, shift = false, alt = false))
    assertNull(desktopShortcut(Key.One, ctrl = true, shift = false, alt = true))
  }
}

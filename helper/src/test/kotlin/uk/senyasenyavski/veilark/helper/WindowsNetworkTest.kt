package uk.senyasenyavski.veilark.helper

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercises the native interface table against the running system. These checks
 * guard the hand-written `MIB_IF_TABLE2` layout: a wrong row offset or stride
 * would silently yield garbage adapter names and make tunnel detection fail.
 */
class WindowsNetworkTest {
  private val onWindows = System.getProperty("os.name").orEmpty().startsWith("Windows", true)

  @Test
  fun `the interface table is readable`() {
    if (!onWindows) return
    val adapters = WindowsNetwork.adapters()

    assertTrue(adapters.isNotEmpty(), "Windows always reports at least a loopback adapter")
    assertTrue(adapters.all { it.index > 0 }, "every adapter has a positive interface index")
    assertTrue(
      adapters.all { it.luid != 0L },
      "every adapter has a LUID, which the row offset must decode correctly",
    )
  }

  @Test
  fun `adapter names are decoded as strings rather than raw buffers`() {
    if (!onWindows) return
    val adapters = WindowsNetwork.adapters()

    assertTrue(
      adapters.none { it.alias.contains('\u0000') || it.description.contains('\u0000') },
      "fixed-size name buffers must be cut at their terminator",
    )
    assertTrue(
      adapters.any { it.description.isNotBlank() },
      "descriptions must decode to readable text",
    )
  }

  @Test
  fun `stacked filter adapters are not reported`() {
    if (!onWindows) return
    val adapters = WindowsNetwork.adapters()

    // NDIS exposes one pseudo adapter per lightweight filter. They copy the
    // alias and counters of their parent and would double-count traffic.
    assertTrue(
      adapters.none { Regex("""-\d{4}$""").containsMatchIn(it.description) },
      "filter pseudo adapters must be filtered out",
    )
  }

  @Test
  fun `an adapter can be looked up again by its luid`() {
    if (!onWindows) return
    val expected = WindowsNetwork.adapters().firstOrNull() ?: return

    val refreshed = WindowsNetwork.refresh(expected.luid)

    assertEquals(expected.index, refreshed?.index)
    assertEquals(expected.alias, refreshed?.alias)
    assertEquals(expected.description, refreshed?.description)
  }
}

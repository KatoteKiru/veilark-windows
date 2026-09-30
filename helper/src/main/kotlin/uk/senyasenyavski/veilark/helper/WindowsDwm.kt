package uk.senyasenyavski.veilark.helper

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinReg
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions

/**
 * Windows 11 title-bar integration through `DwmSetWindowAttribute`:
 * immersive dark caption that follows the app theme, rounded corners and the
 * Mica system backdrop. Every call is best-effort: on non-Windows systems and
 * on builds that do not know an attribute nothing happens and nothing throws.
 */
object WindowsDwm {
  /** Windows 10 1809 introduced the pre-release dark caption attribute (19). */
  const val DARK_MODE_MIN_BUILD = 17_763

  /** Windows 10 2004 documents DWMWA_USE_IMMERSIVE_DARK_MODE as 20. */
  const val DARK_MODE_ATTRIBUTE_BUILD = 19_041

  /** Windows 11 22H2: DWMWA_SYSTEMBACKDROP_TYPE and documented corner preference. */
  const val MICA_MIN_BUILD = 22_621

  internal const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
  internal const val DWMWA_USE_IMMERSIVE_DARK_MODE_BEFORE_20H1 = 19
  internal const val DWMWA_WINDOW_CORNER_PREFERENCE = 33
  internal const val DWMWA_SYSTEMBACKDROP_TYPE = 38
  internal const val DWMWCP_ROUND = 2
  internal const val DWMSBT_MAINWINDOW = 2

  /** One requested attribute: [fallback] is tried only if [attribute] is rejected. */
  internal data class AttributeRequest(val attribute: Int, val value: Int, val fallback: Int? = null)

  /** Pure policy, unit-tested on every platform. */
  internal fun plan(build: Int?, dark: Boolean): List<AttributeRequest> {
    if (build == null || build < DARK_MODE_MIN_BUILD) return emptyList()
    val darkValue = if (dark) 1 else 0
    val darkRequest = if (build >= DARK_MODE_ATTRIBUTE_BUILD) {
      AttributeRequest(DWMWA_USE_IMMERSIVE_DARK_MODE, darkValue, DWMWA_USE_IMMERSIVE_DARK_MODE_BEFORE_20H1)
    } else {
      AttributeRequest(DWMWA_USE_IMMERSIVE_DARK_MODE_BEFORE_20H1, darkValue)
    }
    if (build < MICA_MIN_BUILD) return listOf(darkRequest)
    return listOf(
      darkRequest,
      AttributeRequest(DWMWA_WINDOW_CORNER_PREFERENCE, DWMWCP_ROUND),
      AttributeRequest(DWMWA_SYSTEMBACKDROP_TYPE, DWMSBT_MAINWINDOW),
    )
  }

  private interface DwmApi : StdCallLibrary {
    @Suppress("FunctionName")
    fun DwmSetWindowAttribute(hwnd: WinDef.HWND, attribute: Int, value: IntByReference, size: Int): Int
  }

  private val isWindows: Boolean =
    System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

  private val dwm: DwmApi? by lazy {
    if (!isWindows) return@lazy null
    runCatching { Native.load("dwmapi", DwmApi::class.java, W32APIOptions.DEFAULT_OPTIONS) }.getOrNull()
  }

  /** Current Windows build (e.g. 22631), or `null` outside Windows or when unreadable. */
  val build: Int? by lazy {
    if (!isWindows) return@lazy null
    runCatching {
      Advapi32Util.registryGetStringValue(
        WinReg.HKEY_LOCAL_MACHINE,
        "SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion",
        "CurrentBuildNumber",
      ).trim().toInt()
    }.getOrNull()
  }

  /**
   * Applies the plan to an AWT window. Must be called after the window is
   * displayable (it has a native peer). Returns how many attributes the
   * system accepted; 0 is the expected result on Windows 10 without
   * dark-caption support or on other platforms.
   */
  fun apply(window: java.awt.Window, dark: Boolean): Int {
    val api = dwm ?: return 0
    val requests = plan(build, dark)
    if (requests.isEmpty()) return 0
    return runCatching {
      val pointer: Pointer = Native.getWindowPointer(window) ?: return 0
      val hwnd = WinDef.HWND(pointer)
      requests.count { request ->
        set(api, hwnd, request.attribute, request.value) ||
          (request.fallback != null && set(api, hwnd, request.fallback, request.value))
      }
    }.getOrDefault(0)
  }

  private fun set(api: DwmApi, hwnd: WinDef.HWND, attribute: Int, value: Int): Boolean =
    runCatching { api.DwmSetWindowAttribute(hwnd, attribute, IntByReference(value), Int.SIZE_BYTES) == 0 }
      .getOrDefault(false)
}

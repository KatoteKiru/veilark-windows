package uk.senyasenyavski.veilark.helper

import com.sun.jna.Platform
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import java.nio.file.Path

/**
 * Registers the `veilark:` URL protocol for the current user so that browsers and
 * messengers hand `veilark://import?url=...` links to the installed launcher.
 *
 * The Compose/jpackage MSI cannot declare a URL protocol, so the installed application
 * self-registers under `HKCU\Software\Classes\veilark` on every start. The routine is
 * idempotent: each value is read first and only written when it is missing or differs,
 * which also repairs the command after the installation directory changes.
 */
class UrlProtocolRegistration(
  private val store: RegistryStore = WindowsRegistryStore,
  private val executableProvider: () -> Path? = {
    ProcessHandle.current().info().command().orElse(null)?.let(Path::of)
  },
) {
  /** Minimal per-user registry access, abstracted so the routine can be unit tested. */
  interface RegistryStore {
    val available: Boolean
    fun read(key: String, name: String): String?
    fun write(key: String, name: String, value: String)
  }

  data class RegistryValue(val key: String, val name: String, val value: String)

  /**
   * Writes the protocol registration when this process is the packaged launcher.
   * Returns the number of values that had to be written, or `null` when registration
   * was skipped (development run, non-Windows host) or failed.
   */
  fun ensureRegistered(): Int? {
    val executable = executableProvider() ?: return null
    if (!store.available || !isPackagedLauncher(executable)) return null
    return runCatching {
      values(executable).count { entry ->
        val current = store.read(entry.key, entry.name)
        if (current == entry.value) {
          false
        } else {
          store.write(entry.key, entry.name, entry.value)
          true
        }
      }
    }.getOrElse { error ->
      SafeLog.writeThrowable("Не удалось зарегистрировать протокол $SCHEME", error)
      null
    }
  }

  companion object {
    const val SCHEME = "veilark"
    const val ROOT_KEY = "Software\\Classes\\$SCHEME"
    const val COMMAND_KEY = "$ROOT_KEY\\shell\\open\\command"
    const val DEFAULT_VALUE = ""

    /** Pure builder of the registry values that make up a protocol handler. */
    fun values(executable: Path): List<RegistryValue> = listOf(
      RegistryValue(ROOT_KEY, DEFAULT_VALUE, "URL:Veilark Protocol"),
      RegistryValue(ROOT_KEY, "URL Protocol", ""),
      RegistryValue(COMMAND_KEY, DEFAULT_VALUE, commandLine(executable)),
    )

    /**
     * `"<exe>" "%1"`: the shell substitutes the full link for `%1`, and the quotes keep a
     * link that contains spaces after percent-decoding from being split into arguments.
     */
    fun commandLine(executable: Path): String = "\"$executable\" \"%1\""

    fun isPackagedLauncher(executable: Path): Boolean =
      executable.fileName?.toString().equals("Veilark.exe", ignoreCase = true)
  }

  private object WindowsRegistryStore : RegistryStore {
    override val available: Boolean get() = Platform.isWindows()

    override fun read(key: String, name: String): String? {
      if (!Advapi32Util.registryKeyExists(WinReg.HKEY_CURRENT_USER, key)) return null
      if (!Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, key, name)) return null
      return runCatching {
        Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, key, name)
      }.getOrNull()
    }

    override fun write(key: String, name: String, value: String) {
      if (!Advapi32Util.registryKeyExists(WinReg.HKEY_CURRENT_USER, key)) {
        Advapi32Util.registryCreateKey(WinReg.HKEY_CURRENT_USER, key)
      }
      Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, key, name, value)
    }
  }
}

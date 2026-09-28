package uk.senyasenyavski.veilark.helper

import com.sun.jna.Platform
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.Shell32
import com.sun.jna.platform.win32.WinUser
import java.nio.file.Path

class ElevationManager(
  private val elevatedCheck: () -> Boolean = {
    Platform.isWindows() && Advapi32Util.isCurrentProcessElevated()
  },
  private val commandProvider: () -> String? = {
    ProcessHandle.current().info().command().orElse(null)
  },
  private val classPathProvider: () -> String? = { System.getProperty("java.class.path") },
  private val mainCommandProvider: () -> String? = { System.getProperty("sun.java.command") },
  private val workingDirectoryProvider: () -> String? = { System.getProperty("user.dir") },
  private val launcher: (String, String, String?) -> Boolean = ::shellExecuteElevated,
) {
  fun isElevated(): Boolean = runCatching(elevatedCheck).getOrDefault(false)

  /**
   * Restarts the application with administrator rights.
   *
   * Creating a WinTUN adapter requires elevation, so this is the only way a
   * connection can succeed. Both the packaged launcher and a development run
   * are supported; the latter is rebuilt from the JVM's own class path so that
   * `gradlew :desktopApp:run` can be tested end to end.
   */
  fun relaunch(arguments: List<String>): Boolean {
    if (isElevated()) return true
    val executable = commandProvider() ?: return false
    val packaged = Path.of(executable).fileName.toString()
      .equals("Veilark.exe", ignoreCase = true)
    return if (packaged) {
      launcher(
        executable,
        arguments.joinToString(" ", transform = ::quoteArgument),
        Path.of(executable).parent?.toString(),
      )
    } else {
      val parameters = developmentParameters(arguments) ?: return false
      launcher(executable, parameters, workingDirectoryProvider())
    }
  }

  /**
   * Rebuilds the command line of a development run. `sun.java.command` holds the
   * main class followed by its arguments, and the class path is taken from the
   * running JVM, because Windows does not expose a process's own arguments
   * through [ProcessHandle].
   */
  private fun developmentParameters(arguments: List<String>): String? {
    val classPath = classPathProvider()?.takeIf(String::isNotBlank) ?: return null
    val mainClass = mainCommandProvider()
      ?.substringBefore(' ')
      ?.takeIf(String::isNotBlank)
      ?: return null
    return buildList {
      add("-Dfile.encoding=UTF-8")
      add("-cp")
      add(classPath)
      add(mainClass)
      addAll(arguments)
    }.joinToString(" ", transform = ::quoteArgument)
  }

  private companion object {
    fun shellExecuteElevated(
      executable: String,
      parameters: String,
      directory: String?,
    ): Boolean {
      if (!Platform.isWindows()) return false
      val result = Shell32.INSTANCE.ShellExecute(
        null,
        "runas",
        executable,
        parameters,
        directory,
        WinUser.SW_SHOWNORMAL,
      )
      return result.toInt() > 32
    }

    /**
     * Quotes an argument for the Windows command line. A class path is not
     * quoted when it needs no quoting, because `ShellExecute` passes the string
     * through unchanged and a stray backslash escape would corrupt it.
     */
    fun quoteArgument(value: String): String {
      if (value.isNotEmpty() && value.none { it.isWhitespace() || it == '"' }) return value
      return buildString {
        append('"')
        var backslashes = 0
        value.forEach { char ->
          when (char) {
            '\\' -> backslashes++
            '"' -> {
              repeat(backslashes * 2 + 1) { append('\\') }
              append('"')
              backslashes = 0
            }
            else -> {
              repeat(backslashes) { append('\\') }
              append(char)
              backslashes = 0
            }
          }
        }
        repeat(backslashes * 2) { append('\\') }
        append('"')
      }
    }
  }
}

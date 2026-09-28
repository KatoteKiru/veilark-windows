package uk.senyasenyavski.veilark.helper

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ElevationManagerTest {
  @Test
  fun `packaged app is relaunched from its installation directory`() {
    var launched: Triple<String, String, String?>? = null
    val manager = ElevationManager(
      elevatedCheck = { false },
      commandProvider = { "C:\\Apps\\Veilark\\Veilark.exe" },
      launcher = { executable, arguments, directory ->
        launched = Triple(executable, arguments, directory)
        true
      },
    )

    assertTrue(manager.relaunch(listOf("--connect")))
    assertEquals("C:\\Apps\\Veilark\\Veilark.exe", launched?.first)
    assertEquals("--connect", launched?.second)
    assertEquals("C:\\Apps\\Veilark", launched?.third)
  }

  @Test
  fun `development run is relaunched with the current class path`() {
    var launched: Triple<String, String, String?>? = null
    val manager = ElevationManager(
      elevatedCheck = { false },
      commandProvider = { "C:\\Java\\bin\\java.exe" },
      classPathProvider = { "C:\\out\\classes;C:\\libs\\compose.jar" },
      mainCommandProvider = { "uk.senyasenyavski.veilark.desktop.MainKt --minimized" },
      workingDirectoryProvider = { "C:\\AI-Agent\\veilark-windows" },
      launcher = { executable, arguments, directory ->
        launched = Triple(executable, arguments, directory)
        true
      },
    )

    assertTrue(manager.relaunch(listOf("--connect")))
    assertEquals("C:\\Java\\bin\\java.exe", launched?.first)
    val arguments = launched?.second.orEmpty()
    assertContains(arguments, "-cp C:\\out\\classes;C:\\libs\\compose.jar")
    assertContains(arguments, "uk.senyasenyavski.veilark.desktop.MainKt")
    assertContains(arguments, "--connect")
    assertEquals("C:\\AI-Agent\\veilark-windows", launched?.third)
  }

  @Test
  fun `paths containing spaces stay quoted as a single argument`() {
    var arguments: String? = null
    val manager = ElevationManager(
      elevatedCheck = { false },
      commandProvider = { "C:\\Java\\bin\\java.exe" },
      classPathProvider = { "C:\\Program Files\\out\\classes" },
      mainCommandProvider = { "MainKt" },
      workingDirectoryProvider = { "C:\\work" },
      launcher = { _, value, _ ->
        arguments = value
        true
      },
    )

    assertTrue(manager.relaunch(listOf("--connect")))
    assertContains(arguments.orEmpty(), "\"C:\\Program Files\\out\\classes\"")
  }

  @Test
  fun `trailing backslash is doubled before closing quote`() {
    var arguments: String? = null
    val manager = ElevationManager(
      elevatedCheck = { false },
      commandProvider = { "C:\\Apps\\Veilark\\Veilark.exe" },
      launcher = { _, value, _ -> arguments = value; true },
    )
    assertTrue(manager.relaunch(listOf("C:\\Program Files\\Veilark\\")))
    assertEquals("\"C:\\Program Files\\Veilark\\\\\"", arguments)
  }

  @Test
  fun `an already elevated process is not relaunched`() {
    val manager = ElevationManager(
      elevatedCheck = { true },
      launcher = { _, _, _ -> error("launcher must not be called") },
    )

    assertTrue(manager.relaunch(listOf("--connect")))
  }

  @Test
  fun `a development run without a resolvable class path is not relaunched`() {
    val manager = ElevationManager(
      elevatedCheck = { false },
      commandProvider = { "C:\\Java\\bin\\java.exe" },
      classPathProvider = { "" },
      mainCommandProvider = { "MainKt" },
      launcher = { _, _, _ -> error("launcher must not be called") },
    )

    assertFalse(manager.relaunch(listOf("--connect")))
  }
}

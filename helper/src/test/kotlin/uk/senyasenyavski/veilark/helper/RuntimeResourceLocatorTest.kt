package uk.senyasenyavski.veilark.helper

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RuntimeResourceLocatorTest {
  @Test
  fun `supports direct Compose resources directory`() {
    val root = Path.of("C:\\Program Files\\Veilark\\app\\resources")

    val candidates = RuntimeResourceLocator.fileCandidates(
      fileName = "trusttunnel_client.exe",
      composeResourcesDirectory = root.toString(),
      javaHome = null,
      codeSource = null,
      workingDirectory = Path.of("C:\\work"),
    )

    assertEquals(root.resolve("trusttunnel_client.exe"), candidates.first())
  }

  @Test
  fun `supports application root exposed as Compose resources directory`() {
    val root = Path.of("C:\\Program Files\\Veilark")

    val candidates = RuntimeResourceLocator.fileCandidates(
      fileName = "trusttunnel_client.exe",
      composeResourcesDirectory = root.toString(),
      javaHome = null,
      codeSource = null,
      workingDirectory = Path.of("C:\\work"),
    )

    assertTrue(candidates.contains(root.resolve("app\\resources\\trusttunnel_client.exe")))
  }

  @Test
  fun `supports application directory exposed as Compose resources directory`() {
    val applicationDirectory = Path.of("C:\\Program Files\\Veilark\\app")

    val candidates = RuntimeResourceLocator.fileCandidates(
      fileName = "trusttunnel_client.exe",
      composeResourcesDirectory = applicationDirectory.toString(),
      javaHome = null,
      codeSource = null,
      workingDirectory = Path.of("C:\\work"),
    )

    assertTrue(
      candidates.contains(applicationDirectory.resolve("resources\\trusttunnel_client.exe")),
    )
  }

  @Test
  fun `supports jpackage layout derived from runtime java home`() {
    val applicationRoot = Path.of("C:\\Program Files\\Veilark")

    val candidates = RuntimeResourceLocator.fileCandidates(
      fileName = "sing-box.exe",
      composeResourcesDirectory = null,
      javaHome = applicationRoot.resolve("runtime").toString(),
      codeSource = null,
      workingDirectory = Path.of("C:\\work"),
    )

    assertTrue(candidates.contains(applicationRoot.resolve("app\\resources\\sing-box.exe")))
  }

  @Test
  fun `keeps explicit override first`() {
    val override = Path.of("D:\\Veilark\\trusted\\setup_wizard.exe")

    val candidates = RuntimeResourceLocator.fileCandidates(
      fileName = "setup_wizard.exe",
      overridePath = override,
      environmentValue = "E:\\Veilark\\setup_wizard.exe",
      composeResourcesDirectory = null,
      javaHome = null,
      codeSource = null,
      workingDirectory = Path.of("C:\\work"),
    )

    assertEquals(override, candidates.first())
  }
}

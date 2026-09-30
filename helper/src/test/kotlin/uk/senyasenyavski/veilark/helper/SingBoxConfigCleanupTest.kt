package uk.senyasenyavski.veilark.helper

import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import uk.senyasenyavski.veilark.model.Profile
import uk.senyasenyavski.veilark.model.VpnEngine

class SingBoxConfigCleanupTest {
  @Test fun `rejected pre-spawn config is deleted from isolated runtime directory`() = runBlocking {
    val directory = Files.createTempDirectory("veilark-config-cleanup")
    val executable = Path.of(System.getProperty("java.home"), "bin",
      if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java")
    val controller = SingBoxProcessController(executable, directory)
    try {
      // java rejects the sing-box check command before any TUN/core startup path.
      assertFailsWith<IllegalStateException> {
        controller.start(Profile("test", "test", VpnEngine.SingBox, "{}", emptyList(), "test"))
      }
      val leftovers = Files.list(directory).use { it.map { path -> path.fileName.toString() }.toList() }
      assertEquals(emptyList(), leftovers)
      assertFalse(controller.isAlive())
    } finally {
      Files.list(directory).use { entries -> entries.forEach(Files::deleteIfExists) }
      Files.deleteIfExists(directory)
    }
  }
}

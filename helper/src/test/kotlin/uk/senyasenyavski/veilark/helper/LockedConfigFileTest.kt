package uk.senyasenyavski.veilark.helper

import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalPathApi::class)
class LockedConfigFileTest {
  private val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

  @Test
  fun `config gets a fresh name, exact content and is removed on close`() {
    val directory = Files.createTempDirectory("veilark-locked-config")
    try {
      val first = LockedConfigFile.create(directory, "active-", ".json", "{\"a\":\"секрет\"}")
      val firstPath = first.path
      assertEquals("{\"a\":\"секрет\"}", Files.readString(firstPath, Charsets.UTF_8))
      first.close()
      first.close()
      assertFalse(Files.exists(firstPath))

      LockedConfigFile.create(directory, "active-", ".json", "{}").use { second ->
        assertNotEquals(firstPath, second.path)
        assertTrue(second.path.fileName.toString().startsWith("active-"))
      }
      assertEquals(0L, Files.list(directory).use { it.count() })
    } finally {
      directory.deleteRecursively()
    }
  }

  @Test
  fun `stale configs from an aborted run are removed but foreign files are kept`() {
    val directory = Files.createTempDirectory("veilark-locked-config")
    try {
      val stale = directory.resolve("active-0f8fad5b-d9cb-469f-a165-70867728950e.json")
      val foreign = directory.resolve("active-notes.json")
      Files.writeString(stale, "old credentials")
      Files.writeString(foreign, "keep")
      LockedConfigFile.create(directory, "active-", ".json", "{}").use { }
      assertFalse(Files.exists(stale))
      assertTrue(Files.exists(foreign))
    } finally {
      directory.deleteRecursively()
    }
  }

  @Test
  fun `windows handle denies replacement while readers still work`() {
    if (!isWindows) return
    val directory = Files.createTempDirectory("veilark-locked-config")
    try {
      LockedConfigFile.create(directory, "active-", ".json", "{\"ok\":true}").use { config ->
        assertTrue(config.writeLocked, "jdk.unsupported share-mode options must be available")
        // Readers such as sing-box/TrustTunnel share read+write access.
        assertEquals("{\"ok\":true}", Files.readString(config.path))
        assertFailsWith<FileSystemException> {
          Files.newOutputStream(config.path, StandardOpenOption.WRITE).use { it.write(1) }
        }
        assertFailsWith<FileSystemException> { Files.delete(config.path) }
        assertFailsWith<FileSystemException> {
          Files.move(config.path, directory.resolve("moved.json"))
        }
        assertEquals("{\"ok\":true}", Files.readString(config.path))
      }
    } finally {
      directory.deleteRecursively()
    }
  }
}

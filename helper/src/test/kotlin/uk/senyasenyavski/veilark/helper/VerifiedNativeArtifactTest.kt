package uk.senyasenyavski.veilark.helper

import com.sun.jna.Platform
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

class VerifiedNativeArtifactTest {
  @Test fun `verification holds a Windows deny write delete handle through callback`() = artifact { path, pin ->
    val result = VerifiedNativeArtifact.withVerifiedPath(path, pin) { canonical ->
      assertEquals(path.toRealPath(), canonical)
      assertFailsWith<java.io.IOException> { Files.write(path, byteArrayOf(1)) }
      assertFailsWith<java.io.IOException> { Files.delete(path) }
      "loaded"
    }
    assertEquals("loaded", result)
    Files.delete(path)
    assertFalse(Files.exists(path))
  }

  @Test fun `wrong size or hash never reaches loader and releases handle`() = artifact { path, pin ->
    var loaded = false
    listOf(NativeArtifactPin(pin.size + 1, pin.sha256), NativeArtifactPin(pin.size, "00".repeat(32))).forEach { wrong ->
      assertFailsWith<IllegalStateException> {
        VerifiedNativeArtifact.withVerifiedPath(path, wrong) { loaded = true }
      }
    }
    assertFalse(loaded)
    Files.write(path, byteArrayOf(2))
  }

  @Test fun `loader failure releases lock and keeps original failure`() = artifact { path, pin ->
    val failure = assertFailsWith<UnsupportedOperationException> {
      VerifiedNativeArtifact.withVerifiedPath(path, pin) { throw UnsupportedOperationException("synthetic") }
    }
    assertEquals("synthetic", failure.message)
    Files.delete(path)
  }

  @Test fun `relative paths directories and malformed pins fail before loading`() = artifact { path, pin ->
    assertFailsWith<IllegalArgumentException> { VerifiedNativeArtifact.withVerifiedPath(Path.of("test.dll"), pin) {} }
    assertFailsWith<IllegalArgumentException> { VerifiedNativeArtifact.withVerifiedPath(path.parent, pin) {} }
    assertFailsWith<IllegalArgumentException> { NativeArtifactPin(0, pin.sha256) }
    assertFailsWith<IllegalArgumentException> { NativeArtifactPin(pin.size, "not-a-hash") }
    assertTrue(Files.exists(path))
  }

  private fun artifact(block: (Path, NativeArtifactPin) -> Unit) {
    assumeTrue(Platform.isWindows())
    val directory = Files.createTempDirectory("veilark-artifact-")
    val path = directory.resolve("synthetic.dll")
    val bytes = ByteArray(150_000) { (it * 31).toByte() }
    Files.write(path, bytes)
    val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    try { block(path, NativeArtifactPin(bytes.size.toLong(), hash)) }
    finally { Files.deleteIfExists(path); Files.delete(directory) }
  }
}

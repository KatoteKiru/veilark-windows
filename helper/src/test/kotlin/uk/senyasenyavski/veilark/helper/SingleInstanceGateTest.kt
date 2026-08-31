package uk.senyasenyavski.veilark.helper

import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SingleInstanceGateTest {
  @Test
  fun `secondary instance activates primary and lock is reusable`() {
    val directory = Files.createTempDirectory("veilark-instance-test")
    val lockPath = directory.resolve("instance.lock")
    try {
      val activated = CountDownLatch(1)
      SingleInstanceGate(lockPath).use { primary ->
        assertTrue(primary.isPrimary)
        primary.setActivationHandler { activated.countDown() }

        SingleInstanceGate(lockPath).use { secondary ->
          assertFalse(secondary.isPrimary)
          assertTrue(secondary.notifyPrimary())
        }

        assertTrue(activated.await(3, TimeUnit.SECONDS))
      }

      SingleInstanceGate(lockPath).use { replacement ->
        assertTrue(replacement.isPrimary)
      }
    } finally {
      Files.deleteIfExists(lockPath.resolveSibling("${lockPath.fileName}.endpoint"))
      Files.deleteIfExists(lockPath)
      Files.deleteIfExists(directory)
    }
  }

  @Test
  fun `activation received before handler registration is delivered`() {
    val directory = Files.createTempDirectory("veilark-instance-pending-test")
    val lockPath = directory.resolve("instance.lock")
    try {
      val activated = CountDownLatch(1)
      SingleInstanceGate(lockPath).use { primary ->
        assertTrue(primary.isPrimary)

        SingleInstanceGate(lockPath).use { secondary ->
          assertFalse(secondary.isPrimary)
          assertTrue(secondary.notifyPrimary())
        }

        primary.setActivationHandler { activated.countDown() }
        assertTrue(activated.await(3, TimeUnit.SECONDS))
      }
    } finally {
      Files.deleteIfExists(lockPath.resolveSibling("${lockPath.fileName}.endpoint"))
      Files.deleteIfExists(lockPath)
      Files.deleteIfExists(directory)
    }
  }
}

package uk.senyasenyavski.veilark.helper

import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.swing.SwingUtilities
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SingleInstanceGateTest {
  @BeforeTest
  fun warmUpEventDispatchThread() {
    // notifyPrimary confirms socket delivery; activation itself is queued on the EDT.
    if (!SwingUtilities.isEventDispatchThread()) {
      SwingUtilities.invokeAndWait { }
    }
  }

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
  fun `secondary instance forwards a validated import link to the primary`() {
    val directory = Files.createTempDirectory("veilark-instance-import-test")
    val lockPath = directory.resolve("instance.lock")
    val link = "https://provider.example/sub?token=opaque"
    try {
      val delivered = CountDownLatch(1)
      val received = AtomicReference<String?>("unset")
      SingleInstanceGate(lockPath).use { primary ->
        primary.setActivationHandler { importLink ->
          received.set(importLink)
          delivered.countDown()
        }

        SingleInstanceGate(lockPath).use { secondary ->
          assertTrue(secondary.notifyPrimary(link))
        }

        assertTrue(delivered.await(3, TimeUnit.SECONDS))
        assertEquals(link, received.get())
      }
    } finally {
      Files.deleteIfExists(lockPath.resolveSibling("${lockPath.fileName}.endpoint"))
      Files.deleteIfExists(lockPath)
      Files.deleteIfExists(directory)
    }
  }

  @Test
  fun `import link received before handler registration is kept and plain activation stays null`() {
    val directory = Files.createTempDirectory("veilark-instance-import-pending-test")
    val lockPath = directory.resolve("instance.lock")
    val link = "tt://?AAECAwQFBgcICQoLDA0ODw_-"
    try {
      val delivered = CountDownLatch(1)
      val received = AtomicReference<String?>("unset")
      SingleInstanceGate(lockPath).use { primary ->
        SingleInstanceGate(lockPath).use { secondary ->
          assertTrue(secondary.notifyPrimary(link))
          // A later plain activation must not erase the queued import payload.
          assertTrue(secondary.notifyPrimary())
        }
        // Give the loopback listener time to process both connections.
        Thread.sleep(300)

        primary.setActivationHandler { importLink ->
          received.set(importLink)
          delivered.countDown()
        }
        assertTrue(delivered.await(3, TimeUnit.SECONDS))
        assertEquals(link, received.get())

        val plain = CountDownLatch(1)
        val plainPayload = AtomicReference<String?>("unset")
        primary.setActivationHandler { importLink ->
          plainPayload.set(importLink)
          plain.countDown()
        }
        SingleInstanceGate(lockPath).use { secondary ->
          // Rejected payloads are dropped on both sides and degrade to a plain activation.
          assertTrue(secondary.notifyPrimary("http://provider.example/sub"))
        }
        assertTrue(plain.await(3, TimeUnit.SECONDS))
        assertNull(plainPayload.get())
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

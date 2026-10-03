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
  @Test fun `unresponsive UI cannot acknowledge activation or lose its live lock`() {
    val directory = Files.createTempDirectory("veilark-instance-blocked-ui")
    val path = directory.resolve("instance.lock")
    val blocked = CountDownLatch(1)
    val release = CountDownLatch(1)
    try {
      SingleInstanceGate(path).use { primary ->
        primary.setActivationHandler { }
        SwingUtilities.invokeLater { blocked.countDown(); release.await(5, TimeUnit.SECONDS) }
        assertTrue(blocked.await(2, TimeUnit.SECONDS))
        SingleInstanceGate(path).use { secondary ->
          assertFalse(secondary.notifyPrimary(timeoutMillis = 100))
          assertFalse(secondary.tryBecomePrimary())
          release.countDown()
          assertTrue(secondary.notifyPrimary())
        }
      }
    } finally {
      release.countDown()
      Files.deleteIfExists(path.resolveSibling("instance.lock.endpoint"))
      Files.deleteIfExists(path)
      Files.deleteIfExists(directory)
    }
  }

  @Test fun `secondary may recover only after former primary releases OS lock`() {
    val directory = Files.createTempDirectory("veilark-instance-recover")
    val path = directory.resolve("instance.lock")
    try {
      val primary = SingleInstanceGate(path)
      primary.use {
        SingleInstanceGate(path).use { secondary ->
          assertFalse(secondary.tryBecomePrimary())
          primary.close()
          assertTrue(secondary.tryBecomePrimary())
          assertTrue(secondary.isPrimary)
        }
      }
    } finally {
      Files.deleteIfExists(path.resolveSibling("instance.lock.endpoint"))
      Files.deleteIfExists(path)
      Files.deleteIfExists(directory)
    }
  }
  @Test
  fun `successful socket write without authenticated acknowledgement is not activation`() {
    val directory = Files.createTempDirectory("veilark-instance-ack")
    val path = directory.resolve("instance.lock")
    try {
      SingleInstanceGate(path).use {
        val endpoint = path.resolveSibling("instance.lock.endpoint")
        val port = Files.readAllLines(endpoint).first()
        Files.writeString(endpoint, "$port\nwrong-token\n")
        SingleInstanceGate(path).use { secondary -> assertFalse(secondary.notifyPrimary()) }
      }
    } finally {
      Files.deleteIfExists(path.resolveSibling("instance.lock.endpoint"))
      Files.deleteIfExists(path)
      Files.deleteIfExists(directory)
    }
  }
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
          assertFalse(secondary.notifyPrimary(link, timeoutMillis = 100))
          // A later plain activation must not erase the queued import payload.
          assertFalse(secondary.notifyPrimary(timeoutMillis = 100))
        }
        primary.setActivationHandler { importLink ->
          // The listener may deliver the queued import and the later plain activation
          // separately. Observe the first delivery, not a value overwritten by the EDT.
          received.compareAndSet("unset", importLink)
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
          assertFalse(secondary.notifyPrimary(timeoutMillis = 100))
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

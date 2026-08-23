package uk.senyasenyavski.veilark.helper

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.swing.SwingUtilities
import kotlin.concurrent.thread

class SingleInstanceGate(
  private val lockPath: Path = VeilarkPaths.dataDirectory.resolve("instance.lock"),
  waitForPrimaryMillis: Long = 0,
) : AutoCloseable {
  private val endpointPath = lockPath.resolveSibling("${lockPath.fileName}.endpoint")
  private val channel = FileChannel.open(
    lockPath,
    StandardOpenOption.CREATE,
    StandardOpenOption.READ,
    StandardOpenOption.WRITE,
  )
  private var lock: FileLock? = acquire(waitForPrimaryMillis)
  private var server: ServerSocket? = null
  private val activationHandler = AtomicReference<(() -> Unit)?>(null)
  private val activationPending = AtomicBoolean(false)
  private val closed = AtomicBoolean(false)
  private var token: String? = null

  val isPrimary: Boolean get() = lock != null

  init {
    if (isPrimary) startServer()
  }

  fun setActivationHandler(handler: () -> Unit) {
    activationHandler.set(handler)
    if (activationPending.getAndSet(false)) SwingUtilities.invokeLater(handler)
  }

  fun notifyPrimary(): Boolean {
    if (isPrimary) return true
    repeat(NOTIFY_ATTEMPTS) {
      val endpoint = readEndpoint()
      if (endpoint != null) {
        val sent = runCatching {
          Socket().use { socket ->
            socket.connect(
              InetSocketAddress(InetAddress.getLoopbackAddress(), endpoint.first),
              CONNECT_TIMEOUT_MILLIS,
            )
            socket.getOutputStream().bufferedWriter().use { writer ->
              writer.write(endpoint.second)
              writer.newLine()
            }
          }
          true
        }.getOrDefault(false)
        if (sent) return true
      }
      Thread.sleep(NOTIFY_RETRY_MILLIS)
    }
    return false
  }

  override fun close() {
    if (!closed.compareAndSet(false, true)) return
    runCatching { server?.close() }
    runCatching { lock?.release() }
    runCatching { channel.close() }
  }

  private fun acquire(waitMillis: Long): FileLock? {
    val deadline = System.nanoTime() + waitMillis * 1_000_000L
    do {
      val acquired = runCatching { channel.tryLock() }
        .recoverCatching {
          if (it is OverlappingFileLockException) null else throw it
        }
        .getOrNull()
      if (acquired != null) return acquired
      if (System.nanoTime() >= deadline) return null
      Thread.sleep(LOCK_RETRY_MILLIS)
    } while (true)
  }

  private fun startServer() {
    val localServer = ServerSocket().apply {
      reuseAddress = false
      bind(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 8)
    }
    server = localServer
    val activationToken = UUID.randomUUID().toString()
    token = activationToken
    val endpoint = "${localServer.localPort}\n$activationToken\n"
      .toByteArray(Charsets.UTF_8)
    java.nio.file.Files.write(endpointPath, endpoint)

    thread(name = "veilark-single-instance", isDaemon = true) {
      while (!closed.get()) {
        val accepted = runCatching { localServer.accept() }.getOrNull() ?: break
        accepted.use { socket ->
          socket.soTimeout = CONNECT_TIMEOUT_MILLIS
          val received = runCatching {
            socket.getInputStream().bufferedReader().readLine()
          }.getOrNull()
          if (received == activationToken) {
            val handler = activationHandler.get()
            if (handler == null) {
              activationPending.set(true)
            } else {
              SwingUtilities.invokeLater(handler)
            }
          }
        }
      }
    }
  }

  private fun readEndpoint(): Pair<Int, String>? = runCatching {
    val content = java.nio.file.Files.readString(endpointPath, Charsets.UTF_8)
      .lineSequence()
      .toList()
    val port = content.getOrNull(0)?.toIntOrNull()
    val activationToken = content.getOrNull(1)
    if (port !in 1..65535 || activationToken.isNullOrBlank()) null
    else port!! to activationToken
  }.getOrNull()

  private companion object {
    const val CONNECT_TIMEOUT_MILLIS = 1_000
    const val NOTIFY_ATTEMPTS = 8
    const val NOTIFY_RETRY_MILLIS = 125L
    const val LOCK_RETRY_MILLIS = 100L
  }
}

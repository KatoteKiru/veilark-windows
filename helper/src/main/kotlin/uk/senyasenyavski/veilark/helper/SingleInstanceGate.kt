package uk.senyasenyavski.veilark.helper

import com.example.veilark.profile.ImportDeepLink
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities
import kotlin.concurrent.thread

/**
 * Keeps a single Veilark process per user and lets a late secondary process hand its
 * work over to the primary one.
 *
 * The loopback protocol is line based: the first line is the activation token published
 * in the endpoint file, an optional second line carries an already validated import
 * payload (the inner URL of a `veilark://import` deep link). The primary re-validates
 * that payload before it reaches the activation handler and never logs it.
 */
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
  private val activationHandler = AtomicReference<((importLink: String?) -> Unit)?>(null)
  private val pendingActivation = AtomicReference<PendingActivation?>(null)
  private val activationStateLock = Any()
  private val closed = AtomicBoolean(false)
  private var token: String? = null

  val isPrimary: Boolean get() = lock != null

  init {
    if (isPrimary) try { startServer() } catch (error: Throwable) { close(); throw error }
  }

  /**
   * Registers the primary-side handler. It runs on the Swing event thread and receives
   * the forwarded import payload, or `null` for a plain "bring the window forward"
   * activation. An activation that arrived before registration is delivered once.
   */
  fun setActivationHandler(handler: (importLink: String?) -> Unit) {
    val pending = synchronized(activationStateLock) {
      activationHandler.set(handler)
      pendingActivation.getAndSet(null)
    }
    if (pending != null) dispatchActivation(handler, pending.importLink, pending.result)
  }

  /**
   * Asks the running primary instance to activate itself, optionally handing over a
   * validated import payload so that the deep link opens the import flow there.
   */
  fun notifyPrimary(importLink: String? = null, timeoutMillis: Long = 3_000): Boolean {
    if (isPrimary) return true
    require(timeoutMillis > 0)
    val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
    val forwarded = importLink?.let(ImportDeepLink::validatePayload)
    repeat(NOTIFY_ATTEMPTS) {
      val remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())
      if (remaining <= 0) return false
      val endpoint = readEndpoint()
      if (endpoint != null) {
        val sent = runCatching {
          Socket().use { socket ->
            socket.connect(
              InetSocketAddress(InetAddress.getLoopbackAddress(), endpoint.first),
              minOf(CONNECT_TIMEOUT_MILLIS, remaining.toInt()),
            )
            socket.soTimeout = minOf(CONNECT_TIMEOUT_MILLIS, remaining.toInt())
            socket.getOutputStream().bufferedWriter().let { writer ->
              writer.write(endpoint.second)
              writer.newLine()
              if (forwarded != null) {
                writer.write(forwarded)
                writer.newLine()
              }
              writer.flush()
            }
            socket.shutdownOutput()
            socket.getInputStream().bufferedReader().readBoundedLine(128) == ACK
          }
        }.getOrDefault(false)
        if (sent) return true
      }
      Thread.sleep(NOTIFY_RETRY_MILLIS)
    }
    return false
  }

  /** Never steal a live owner's lock, even when its activation endpoint is unresponsive. */
  fun tryBecomePrimary(): Boolean {
    check(!closed.get())
    if (isPrimary) return true
    lock = acquire(0) ?: return false
    try { startServer() } catch (error: Throwable) { close(); throw error }
    return true
  }

  override fun close() {
    if (!closed.compareAndSet(false, true)) return
    runCatching { server?.close() }
    if (isPrimary) runCatching { Files.deleteIfExists(endpointPath) }
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
    val temporary = endpointPath.resolveSibling("${endpointPath.fileName}.tmp")
    Files.write(temporary, endpoint)
    try {
      Files.move(temporary, endpointPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
      // The OS lock still belongs to us; incomplete endpoint reads are retried
      // and cannot authenticate without a complete matching token + UI ACK.
      Files.move(temporary, endpointPath, StandardCopyOption.REPLACE_EXISTING)
    } finally { Files.deleteIfExists(temporary) }

    thread(name = "veilark-single-instance", isDaemon = true) {
      while (!closed.get()) {
        val accepted = runCatching { localServer.accept() }.getOrNull() ?: break
        runCatching { accepted.use { socket ->
          socket.soTimeout = CONNECT_TIMEOUT_MILLIS
          val reader = socket.getInputStream().bufferedReader()
          val received = reader.readBoundedLine(128)
          if (received == activationToken) {
            // The payload line is optional; a plain activation closes the stream here.
            val importLink = reader.readBoundedLine(16_384)
              ?.let(ImportDeepLink::validatePayload)
            val delivered = runCatching {
              enqueueActivation(importLink).get(CONNECT_TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS)
            }.getOrDefault(false)
            if (delivered) {
              socket.getOutputStream().write("$ACK\n".toByteArray(Charsets.UTF_8))
              socket.getOutputStream().flush()
            }
          }
        } }
      }
    }
  }

  private fun enqueueActivation(importLink: String?): CompletableFuture<Boolean> {
    val result = CompletableFuture<Boolean>()
    val handler = synchronized(activationStateLock) {
      activationHandler.get()?.also {
        pendingActivation.set(null)
      } ?: run {
        // Collapse repeated activations but never drop a forwarded import payload.
        val previous = pendingActivation.get()
        previous?.result?.complete(false)
        pendingActivation.set(PendingActivation(importLink ?: previous?.importLink, result))
        null
      }
    }
    handler?.let { dispatchActivation(it, importLink, result) }
    return result
  }

  private fun dispatchActivation(handler: (String?) -> Unit, importLink: String?, result: CompletableFuture<Boolean>) {
    if (closed.get()) { result.complete(false); return }
    SwingUtilities.invokeLater {
      if (closed.get()) { result.complete(false); return@invokeLater }
      result.complete(runCatching { handler(importLink); true }.getOrDefault(false))
    }
  }

  private fun readEndpoint(): Pair<Int, String>? = runCatching {
    check(Files.size(endpointPath) in 1..256)
    val content = java.nio.file.Files.readString(endpointPath, Charsets.UTF_8)
      .lineSequence()
      .toList()
    val port = content.getOrNull(0)?.toIntOrNull()
    val activationToken = content.getOrNull(1)
    if (port !in 1..65535 || activationToken.isNullOrBlank()) null
    else port!! to activationToken
  }.getOrNull()

  private class PendingActivation(val importLink: String?, val result: CompletableFuture<Boolean>)

  private fun java.io.BufferedReader.readBoundedLine(limit: Int): String? {
    val value = StringBuilder()
    while (true) {
      val character = read()
      if (character == -1) return value.takeIf { it.isNotEmpty() }?.toString()
      if (character == '\n'.code) return value.toString().removeSuffix("\r")
      require(value.length < limit) { "Activation record too long" }
      value.append(character.toChar())
    }
  }

  private companion object {
    const val ACK = "VEILARK-ACTIVATION-ACCEPTED"
    const val CONNECT_TIMEOUT_MILLIS = 1_000
    const val NOTIFY_ATTEMPTS = 8
    const val NOTIFY_RETRY_MILLIS = 125L
    const val LOCK_RETRY_MILLIS = 100L
  }
}

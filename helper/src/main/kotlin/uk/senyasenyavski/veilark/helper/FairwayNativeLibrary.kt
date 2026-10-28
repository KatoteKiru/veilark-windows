package uk.senyasenyavski.veilark.helper

import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinDef.HMODULE
import com.sun.jna.ptr.LongByReference
import com.sun.jna.win32.StdCallLibrary
import java.io.Closeable
import java.nio.ByteOrder
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Exact consumer of frozen veilark_windows_native.h v1 (private Core 940801c7).
 * AUTH-only libraries may be inspected/allocated but cannot start a product tunnel.
 * No library path discovery, download, credentials, routes or UI activation here.
 */
internal class FairwayNativeLibrary private constructor(
  private val lease: VerifiedNativeArtifact.Lease,
  private val module: HMODULE,
  private val functions: Map<String, Function>,
  val capabilities: Long,
) : Closeable {
  private val monitor = Any()
  private val owners = mutableSetOf<Long>()
  private var calls = 0
  private var closed = false
  private var leaseFinished = false

  val supportsFullTunnel: Boolean get() = capabilities and CAP_FULL_TUN != 0L
  val supportsHostConfiguration: Boolean get() = functions.containsKey("configure_host")
  /** Static MAY-pin policy; a logical FreeLibrary does not prove physical unload. */
  val requiresProcessRestartForUpdate: Boolean get() = capabilities and CAP_PROCESS_PIN != 0L

  /** Consumes/erases the supplied key even on validation/native failure. No secret toString. */
  fun create(
    clientKey: ByteArray, serverPin: ByteArray, adapterGuid: ByteArray,
    endpointIpv4: ByteArray, endpointPort: Int, mtu: Int, uplinkLuid: Long,
  ): Owner {
    try {
      require(clientKey.size == 32 && serverPin.size == 32 && adapterGuid.size == 16 && endpointIpv4.size == 4)
      require(endpointPort in 1..65535 && mtu in 1280..1360 && uplinkLuid != 0L)
      return call {
        Memory(CONFIG_SIZE).use { config ->
          config.clear()
          try {
            config.setInt(0, CONFIG_SIZE.toInt())
            config.setInt(4, ABI)
            config.write(8, clientKey, 0, 32)
            config.write(40, serverPin, 0, 32)
            config.write(72, adapterGuid, 0, 16)
            config.write(88, endpointIpv4, 0, 4)
            config.setShort(92, endpointPort.toShort())
            config.setInt(96, mtu)
            config.setLong(104, uplinkLuid)
            val output = LongByReference(0)
            requireOk("create", int("create", config, output))
            val id = output.value
            check(id != 0L) { "Native create returned an invalid owner" }
            synchronized(monitor) { owners.add(id) }
            Owner(id)
          } finally { config.clear() }
        }
      }
    } finally { clientKey.fill(0) }
  }

  inner class Owner internal constructor(private val id: Long) {
    private val joinRelease = Any()
    private val teardown = Mutex()
    @Volatile private var released = false

    /** Worker liveness must never be substituted for this custody check. */
    fun hasOwnedResources(): Boolean = !released

    /** Copies all caller data into private buffers, held immutable until native returns.
     * Configuration is accepted once, before auth; this call does not start networking.
     */
    fun configureHost(wintunPath: Path, dnsIpv4: List<ByteArray>, directCidrs: List<String>) = ownedCall {
      check(supportsHostConfiguration) { "Native host configuration export is unavailable" }
      require(wintunPath.isAbsolute) { "Wintun path must be absolute" }
      val path = wintunPath.toString()
      require(path.length in 1..32767 && '\u0000' !in path) { "Invalid Wintun path" }
      require(dnsIpv4.size in 1..4) { "Invalid native DNS count" }
      val dns = dnsIpv4.map { it.copyOf() }
      require(dns.all { ip ->
        ip.size == 4 && (ip[0].toInt() and 255) in 1..223 && ip[0] != 127.toByte()
      } && dns.map { it.toList() }.distinct().size == dns.size) { "Invalid native DNS addresses" }
      val cidrs = FairwayIpv4Routes.compact(directCidrs)
      Memory(path.length * 2L).use { pathBuffer ->
        for (index in path.indices) pathBuffer.setShort(index * 2L, path[index].code.toShort())
        Memory(dns.size * 4L).use { dnsBuffer ->
          dns.forEachIndexed { index, ip -> dnsBuffer.write(index * 4L, ip, 0, 4) }
          val prefixBuffer = if (cidrs.isEmpty()) null else Memory(cidrs.size * 8L)
          prefixBuffer.use { prefixes ->
            cidrs.forEachIndexed { index, cidr ->
              val ip = cidr.substringBefore('/').split('.').map { it.toInt().toByte() }.toByteArray()
              prefixes!!.write(index * 8L, ip, 0, 4)
              prefixes.setInt(index * 8L + 4, cidr.substringAfter('/').toInt())
            }
            Memory(HOST_CONFIG_SIZE).use { host ->
              host.clear()
              host.setInt(0, HOST_CONFIG_SIZE.toInt())
              host.setInt(4, ABI)
              host.setPointer(8, pathBuffer)
              host.setInt(16, path.length)
              host.setPointer(24, dnsBuffer)
              host.setInt(32, dns.size)
              host.setInt(36, cidrs.size)
              host.setPointer(40, prefixes)
              requireOk("configure_host", int("configure_host", id, host))
            }
          }
        }
      }
    }

    fun status(): Status = ownedCall {
      Memory(STATUS_SIZE).use { output ->
        output.clear()
        output.setInt(0, STATUS_SIZE.toInt())
        output.setInt(4, ABI)
        requireOk("status", int("status", id, output))
        check(output.getInt(0) == STATUS_SIZE.toInt() && output.getInt(4) == ABI && output.getInt(60) == 0)
        Status(
          output.getInt(8), output.getInt(12), output.getInt(16) != 0,
          output.getInt(20) != 0, output.getInt(24), output.getInt(28) != 0,
          output.getByteArray(32, 4), output.getInt(36), output.getLong(40),
          output.getLong(48), output.getInt(56).toLong() and 0xffff_ffffL,
        )
      }
    }

    fun beginAuthForTunnel() = ownedCall {
      check(supportsFullTunnel) { "Native full tunnel capability is unavailable" }
      requireOk("begin_auth", int("begin_auth", id))
    }

    fun startHost() = ownedCall {
      check(supportsFullTunnel) { "Native full tunnel capability is unavailable" }
      requireOk("start_host", int("start_host", id))
    }

    fun requestStop() = ownedCall { requireOk("request_stop", int("request_stop", id)) }

    /** Application teardown boundary: survives connect cancellation, never waits
     * on the UI dispatcher, and retains the owner on timeout/cleanup failure.
     * No polling or retries without another explicit call. Library lifetime is
     * still separate: other owners/inflight calls may keep it loaded.
     * timeoutMs bounds native Join, not dispatcher scheduling or mutex queueing.
     */
    suspend fun stopAndRelease(timeoutMs: Int) {
      require(timeoutMs in 0..30_000)
      withContext(NonCancellable + Dispatchers.IO) {
        teardown.withLock {
          if (released) return@withLock
          requestStop()
          if (!join(timeoutMs)) throw NativeCallException("join", JOIN_TIMEOUT, status())
          val state = status()
          if (!state.canRelease || state.workerAlive || state.resourceMask != 0) {
            throw NativeCallException("cleanup", CLEANUP_REQUIRED, state)
          }
          release()
        }
      }
    }

    /** Use a background dispatcher for waits. Timeout is a result, not proof of cleanup. */
    fun join(timeoutMs: Int): Boolean = synchronized(joinRelease) {
      require(timeoutMs in 0..30_000)
      ownedCall {
        val result = int("join", id, timeoutMs)
        if (result == JOIN_TIMEOUT) false else { requireOk("join", result); true }
      }
    }

    /** Failure retains the same owner and DLL; caller can retry stop/join/release. */
    fun release() = synchronized(joinRelease) {
      if (!released) ownedCall {
        requireOk("release", int("release", id))
        synchronized(monitor) { owners.remove(id); released = true }
      }
    }

    private fun <T> ownedCall(body: () -> T): T = call {
      check(!released) { "Native owner has been released" }
      body()
    }

    override fun toString() = "FairwayNativeOwner([redacted])"
  }

  class Status(
    val phase: Int, val terminalOutcome: Int, val workerAlive: Boolean,
    val ownerAlive: Boolean, val resourceMask: Int, val canRelease: Boolean,
    internalIpv4: ByteArray, val mtu: Int, val txBytes: Long, val rxBytes: Long,
    val nativeError: Long,
  ) {
    private val ipv4 = internalIpv4.copyOf()
    fun internalIpv4(): ByteArray = ipv4.copyOf()
    fun isActivePhase(expected: Int): Boolean = phase == expected && workerAlive && terminalOutcome == 0
    // AUTHENTICATED is never RUNNING and capability alone is not traffic acceptance.
    override fun toString() = "FairwayNativeStatus(phase=$phase,outcome=$terminalOutcome,worker=$workerAlive,owner=$ownerAlive)"
  }

  override fun close() = synchronized(monitor) {
    if (closed) { finishLease(); return@synchronized }
    check(owners.isEmpty() && calls == 0) { "Native library still owns handles or calls" }
    check(Kernel32.INSTANCE.FreeLibrary(module)) { "Cannot unload native library" }
    closed = true
    finishLease()
  }

  private fun finishLease() {
    if (leaseFinished) return
    finishArtifactLease(lease, requiresProcessRestartForUpdate)
    leaseFinished = true
  }

  private fun <T> call(body: () -> T): T {
    synchronized(monitor) { check(!closed) { "Native library is closed" }; calls++ }
    try { return body() } finally { synchronized(monitor) { calls-- } }
  }

  private fun int(name: String, vararg arguments: Any?): Int =
    functions.getValue(name).invokeInt(arguments)

  companion object {
    const val ABI = 1
    const val CONFIG_SIZE = 112L
    const val STATUS_SIZE = 64L
    const val HOST_CONFIG_SIZE = 48L
    const val CAP_FULL_TUN = 2L
    const val CAP_PROCESS_PIN = 8L
    private const val JOIN_TIMEOUT = -5
    private const val CLEANUP_REQUIRED = -6
    private const val LOAD_LIBRARY_SEARCH_SYSTEM32 = 0x800
    private val names = listOf("abi_version", "capabilities", "create", "begin_auth", "start_host", "request_stop", "join", "release", "status")
    private val rejectedMonitor = Any()
    private val rejectedLoads = mutableListOf<RejectedLoad>()
    // Deliberately never closed: callback code may remain pinned in this image
    // after all logical owners are released. Windows closes handles at exit.
    private val processPinnedLeases = mutableMapOf<Pair<Path, String>, VerifiedNativeArtifact.Lease>()

    private fun finishArtifactLease(lease: VerifiedNativeArtifact.Lease, processPinned: Boolean) {
      if (processPinned) synchronized(processPinnedLeases) {
        val identity = lease.path to lease.sha256
        val existing = processPinnedLeases[identity]
        if (existing == null) processPinnedLeases[identity] = lease
        else if (existing !== lease) lease.close()
      }
      else lease.close()
    }

    /** No timer: a later load or an explicit cleanup retry retains failures in custody. */
    fun retryRejectedLoads(): Int = synchronized(rejectedMonitor) {
      val iterator = rejectedLoads.iterator()
      while (iterator.hasNext()) {
        val rejected = iterator.next()
        try { rejected.close(); iterator.remove() } catch (_: Throwable) { /* Retain for retry. */ }
      }
      rejectedLoads.size
    }

    private class RejectedLoad(
      private var module: HMODULE?, private val lease: VerifiedNativeArtifact.Lease,
      private val processPinned: Boolean,
    ) {
      fun close() {
        module?.let {
          check(Kernel32.INSTANCE.FreeLibrary(it)) { "Cannot unload rejected native library" }
          module = null
        }
        finishArtifactLease(lease, processPinned)
      }
    }

    private interface Symbols : StdCallLibrary {
      fun GetProcAddress(module: HMODULE, name: String): Pointer?
    }

    fun load(path: Path, pin: NativeArtifactPin): FairwayNativeLibrary {
      check(Platform.isWindows() && Native.POINTER_SIZE == 8 && ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN)
      check(retryRejectedLoads() == 0) { "Previous native library cleanup requires retry" }
      val lease = VerifiedNativeArtifact.open(path, pin)
      var module: HMODULE? = null
      var processPinned = false
      try {
        module = Kernel32.INSTANCE.LoadLibraryEx(lease.path.toString(), null, LOAD_LIBRARY_SEARCH_SYSTEM32)
        check(module != null && Pointer.nativeValue(module.pointer) != 0L) { "Cannot load pinned native library" }
        // Until capabilities are known, an export/ABI rejection cannot prove
        // that the loaded image is unloadable. Keep unknown images conservative.
        processPinned = true
        val symbols = Native.load("kernel32", Symbols::class.java)
        val functions = names.associateWith { name ->
          val address = symbols.GetProcAddress(module, "vwc_$name")
          check(address != null) { "Native export is missing" }
          Function.getFunction(address, Function.C_CONVENTION)
        }.toMutableMap()
        symbols.GetProcAddress(module, "vwc_configure_host")?.let { address ->
          functions["configure_host"] = Function.getFunction(address, Function.C_CONVENTION)
        }
        check(functions.getValue("abi_version").invokeInt(emptyArray()) == ABI) { "Native ABI mismatch" }
        val capabilities = functions.getValue("capabilities").invokeLong(emptyArray())
        processPinned = capabilities and CAP_PROCESS_PIN != 0L
        return FairwayNativeLibrary(lease, module, functions, capabilities)
      } catch (error: Throwable) {
        val loaded = module?.takeIf { Pointer.nativeValue(it.pointer) != 0L }
        val rejected = RejectedLoad(loaded, lease, processPinned)
        synchronized(rejectedMonitor) {
          // Register custody before attempting fallible cleanup, even on ABI/export rejection.
          rejectedLoads.add(rejected)
          try { rejected.close(); rejectedLoads.remove(rejected) }
          catch (closeError: Throwable) { error.addSuppressed(closeError) }
        }
        throw error
      }
    }

    private fun requireOk(operation: String, result: Int) {
      if (result != 0) throw NativeCallException(operation, result)
    }
  }
}

internal class NativeCallException(
  operation: String,
  val result: Int,
  state: FairwayNativeLibrary.Status? = null,
) : IllegalStateException(buildString {
  append("Native $operation failed ($result)")
  state?.let {
    append(" phase=${it.phase} outcome=${it.terminalOutcome} worker=${it.workerAlive}")
    append(" resources=${it.resourceMask} releasable=${it.canRelease} os=${it.nativeError}")
  }
})

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

  val supportsFullTunnel: Boolean get() = capabilities and CAP_FULL_TUN != 0L

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
    @Volatile private var released = false

    /** Worker liveness must never be substituted for this custody check. */
    fun hasOwnedResources(): Boolean = !released

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
    // AUTHENTICATED is never RUNNING and capability alone is not traffic acceptance.
    override fun toString() = "FairwayNativeStatus(phase=$phase,outcome=$terminalOutcome,worker=$workerAlive,owner=$ownerAlive)"
  }

  override fun close() = synchronized(monitor) {
    if (closed) { lease.close(); return@synchronized }
    check(owners.isEmpty() && calls == 0) { "Native library still owns handles or calls" }
    check(Kernel32.INSTANCE.FreeLibrary(module)) { "Cannot unload native library" }
    closed = true
    lease.close()
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
    const val CAP_FULL_TUN = 2L
    private const val JOIN_TIMEOUT = -5
    private const val LOAD_LIBRARY_SEARCH_SYSTEM32 = 0x800
    private val names = listOf("abi_version", "capabilities", "create", "begin_auth", "start_host", "request_stop", "join", "release", "status")
    private val rejectedMonitor = Any()
    private val rejectedLoads = mutableListOf<RejectedLoad>()

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
    ) {
      fun close() {
        module?.let {
          check(Kernel32.INSTANCE.FreeLibrary(it)) { "Cannot unload rejected native library" }
          module = null
        }
        lease.close()
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
      try {
        module = Kernel32.INSTANCE.LoadLibraryEx(lease.path.toString(), null, LOAD_LIBRARY_SEARCH_SYSTEM32)
        check(module != null && Pointer.nativeValue(module.pointer) != 0L) { "Cannot load pinned native library" }
        val symbols = Native.load("kernel32", Symbols::class.java)
        val functions = names.associateWith { name ->
          val address = symbols.GetProcAddress(module, "vwc_$name")
          check(address != null) { "Native export is missing" }
          Function.getFunction(address, Function.C_CONVENTION)
        }
        check(functions.getValue("abi_version").invokeInt(emptyArray()) == ABI) { "Native ABI mismatch" }
        val capabilities = functions.getValue("capabilities").invokeLong(emptyArray())
        return FairwayNativeLibrary(lease, module, functions, capabilities)
      } catch (error: Throwable) {
        val loaded = module?.takeIf { Pointer.nativeValue(it.pointer) != 0L }
        val rejected = RejectedLoad(loaded, lease)
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

internal class NativeCallException(operation: String, val result: Int) :
  IllegalStateException("Native $operation failed ($result)")

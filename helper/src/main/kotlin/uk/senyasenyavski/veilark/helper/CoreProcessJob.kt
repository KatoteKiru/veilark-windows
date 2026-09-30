package uk.senyasenyavski.veilark.helper

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions

/**
 * A Windows Job object with JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE. Native cores
 * assigned to it are terminated by Windows when the last handle closes, i.e.
 * when Veilark exits or crashes, so an elevated core can no longer outlive the
 * app that owns its tunnel. Best-effort: outside Windows, on 32-bit JVMs or if
 * a call fails, [assign] returns false and startup continues unchanged
 * ([CoreProcessJanitor] still sweeps orphans before the next connect).
 */
class CoreProcessJob private constructor(private val handle: WinNT.HANDLE?) : AutoCloseable {
  private interface JobApi : StdCallLibrary {
    fun CreateJobObjectW(attributes: Pointer?, name: WString?): WinNT.HANDLE?
    fun SetInformationJobObject(job: WinNT.HANDLE, infoClass: Int, info: Pointer, length: Int): Boolean
    fun AssignProcessToJobObject(job: WinNT.HANDLE, process: WinNT.HANDLE): Boolean
  }

  val active: Boolean get() = handle != null

  fun assign(process: Process): Boolean {
    val job = handle ?: return false
    val api = api ?: return false
    return runCatching {
      val target = Kernel32.INSTANCE.OpenProcess(
        PROCESS_SET_QUOTA or PROCESS_TERMINATE,
        false,
        process.pid().toInt(),
      ) ?: return false
      try {
        api.AssignProcessToJobObject(job, target)
      } finally {
        Kernel32.INSTANCE.CloseHandle(target)
      }
    }.getOrDefault(false)
  }

  /** Closing the job terminates every process still assigned to it. */
  override fun close() {
    handle?.let { runCatching { Kernel32.INSTANCE.CloseHandle(it) } }
  }

  companion object {
    private const val JOB_OBJECT_EXTENDED_LIMIT_INFORMATION = 9
    private const val JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE = 0x2000
    private const val PROCESS_SET_QUOTA = 0x0100
    private const val PROCESS_TERMINATE = 0x0001

    // JOBOBJECT_EXTENDED_LIMIT_INFORMATION on x64: 144 bytes; LimitFlags at 16.
    private const val EXTENDED_LIMIT_SIZE_X64 = 144
    private const val LIMIT_FLAGS_OFFSET = 16

    private val api: JobApi? by lazy {
      val windows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
      if (!windows || Native.POINTER_SIZE != 8) return@lazy null
      runCatching { Native.load("kernel32", JobApi::class.java, W32APIOptions.DEFAULT_OPTIONS) }.getOrNull()
    }

    fun create(): CoreProcessJob {
      val api = api ?: return CoreProcessJob(null)
      return runCatching {
        val job = api.CreateJobObjectW(null, null) ?: return CoreProcessJob(null)
        val info = Memory(EXTENDED_LIMIT_SIZE_X64.toLong()).apply {
          clear()
          setInt(LIMIT_FLAGS_OFFSET.toLong(), JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE)
        }
        if (!api.SetInformationJobObject(job, JOB_OBJECT_EXTENDED_LIMIT_INFORMATION, info, EXTENDED_LIMIT_SIZE_X64)) {
          Kernel32.INSTANCE.CloseHandle(job)
          CoreProcessJob(null)
        } else {
          CoreProcessJob(job)
        }
      }.getOrDefault(CoreProcessJob(null))
    }

    /** Process-lifetime job shared by all cores; its handle closes with the JVM. */
    val shared: CoreProcessJob by lazy { create() }

    fun assignToShared(process: Process, engineName: String) {
      if (shared.active && !shared.assign(process)) {
        SafeLog.write("Не удалось привязать $engineName к объекту задания Windows")
      }
    }
  }
}

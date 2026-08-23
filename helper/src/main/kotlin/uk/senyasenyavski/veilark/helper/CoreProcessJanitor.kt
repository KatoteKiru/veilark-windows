package uk.senyasenyavski.veilark.helper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Terminates a VPN core left running by a previous Veilark session.
 *
 * An abandoned core keeps holding its WinTUN adapter and its listening ports, so
 * the next connection attempt fails even though nothing is visibly running.
 * Matching is by the exact executable path Veilark itself resolved, which means
 * an unrelated product shipping the same core is never touched.
 */
internal object CoreProcessJanitor {
  suspend fun terminateOrphans(executable: Path) = withContext(Dispatchers.IO) {
    val target = runCatching { executable.toRealPath() }.getOrDefault(executable.toAbsolutePath())
    val current = ProcessHandle.current().pid()
    val orphans = runCatching {
      ProcessHandle.allProcesses()
        .filter { it.pid() != current }
        .filter { handle ->
          handle.info().command()
            .map { runCatching { Path.of(it).toRealPath() }.getOrNull() == target }
            .orElse(false)
        }
        .toList()
    }.getOrDefault(emptyList())
    if (orphans.isEmpty()) return@withContext

    SafeLog.write(
      "Найдены незавершённые процессы ${target.fileName}: ${orphans.size}. Останавливаю.",
    )
    orphans.forEach { handle ->
      runCatching { handle.destroy() }
      runCatching { handle.onExit().toCompletableFuture().get(3, TimeUnit.SECONDS) }
      if (handle.isAlive) runCatching { handle.destroyForcibly() }
    }
  }
}

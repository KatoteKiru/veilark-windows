package uk.senyasenyavski.veilark.helper

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** UI-owned preparation, before the native session owns a controller. */
class ConnectionPreparation(private val scope: CoroutineScope) {
  private val lock = Any()
  private var job: Job? = null
  private val mutableBusy = MutableStateFlow(false)
  val busy: StateFlow<Boolean> = mutableBusy

  fun start(action: suspend () -> Unit, onFailure: suspend (Throwable) -> Unit): Boolean = synchronized(lock) {
    if (job != null) return false
    lateinit var launched: Job
    launched = scope.launch(start = CoroutineStart.LAZY) {
      try {
        currentCoroutineContext().ensureActive()
        action()
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (error: Exception) {
        onFailure(error)
      } finally {
        synchronized(lock) { if (job === launched) { job = null; mutableBusy.value = false } }
      }
    }
    job = launched
    mutableBusy.value = true
    launched.invokeOnCompletion {
      synchronized(lock) { if (job === launched) { job = null; mutableBusy.value = false } }
    }
    launched.start()
    true
  }

  fun cancel() { synchronized(lock) { job?.cancel() } }

  suspend fun cancelAndJoin() {
    val pending = synchronized(lock) { job?.also { it.cancel() } }
    pending?.join()
  }
}

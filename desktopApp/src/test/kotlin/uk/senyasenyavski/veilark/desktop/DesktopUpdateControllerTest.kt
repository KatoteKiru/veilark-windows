package uk.senyasenyavski.veilark.desktop

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import uk.senyasenyavski.veilark.helper.ScheduledWindowsUpdate
import uk.senyasenyavski.veilark.update.AppUpdate
import uk.senyasenyavski.veilark.update.UpdateDownloadCancellation
import uk.senyasenyavski.veilark.update.UpdateErrorCode
import uk.senyasenyavski.veilark.update.UpdateException
import java.nio.file.Path
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DesktopUpdateControllerTest {
  private val update = AppUpdate(322, "0.3.22", "https://example.test/Veilark-0.3.22.exe", "A".repeat(64), 10, "notes")
  private val installerPath = Path.of("Veilark-0.3.22.exe")

  private fun controller(
    check: () -> AppUpdate? = { update },
    download: (AppUpdate, UpdateDownloadCancellation, (Long, Long) -> Unit) -> Path = { _, _, _ -> installerPath },
    install: suspend (AppUpdate, Path, suspend () -> Unit) -> ScheduledWindowsUpdate = { _, _, _ -> error("not scheduled") },
  ) = DesktopUpdateController(check, download, install, housekeeping = { null })

  @Test
  fun `foreground check reports a stable error code and background failure is silent`() = runBlocking {
    var fail = false
    val updates = controller(check = {
      if (fail) throw UpdateException(UpdateErrorCode.SignatureInvalid, "bad signature") else null
    })
    updates.check()
    assertEquals(DesktopUpdateState.Current, updates.state.value)
    fail = true
    updates.check(background = true)
    assertEquals(DesktopUpdateState.Current, updates.state.value)
    updates.check()
    val failed = assertIs<DesktopUpdateState.Failed>(updates.state.value)
    assertEquals(UpdateErrorCode.SignatureInvalid, failed.error.code)
  }

  @Test
  fun `http status is carried as a non-localized detail`() = runBlocking {
    val updates = controller(check = { throw UpdateException(UpdateErrorCode.HttpStatus, "HTTP 503", "503") })
    updates.check()
    val failed = assertIs<DesktopUpdateState.Failed>(updates.state.value)
    assertEquals("503", failed.error.detail)
    assertFalse(updateErrorText(failed.error, UiLanguage.English).any { it in 'А'..'я' })
  }

  @Test
  fun `cancelled check propagates and restores the previous state`() = runBlocking {
    var slow = false
    val updates = controller(check = {
      if (slow) Thread.sleep(300)
      null
    })
    updates.check()
    slow = true
    val job = launch { updates.check() }
    delay(50)
    job.cancel()
    job.join()
    assertTrue(job.isCancelled)
    assertEquals(DesktopUpdateState.Current, updates.state.value)
  }

  @Test
  fun `user cancel returns to available without cancelling the caller`() = runBlocking {
    val updates = controller(download = { _, token, _ ->
      while (!token.isCancelled()) Thread.sleep(5)
      throw CancellationException("Update download cancelled")
    })
    val job = launch { updates.download(update) }
    while (updates.state.value !is DesktopUpdateState.Downloading) delay(5)
    updates.cancelDownload()
    job.join()
    assertFalse(job.isCancelled)
    assertEquals(DesktopUpdateState.Available(update), updates.state.value)
  }

  @Test
  fun `coroutine cancellation during download is rethrown`() = runBlocking {
    val updates = controller(download = { _, _, _ ->
      Thread.sleep(300)
      installerPath
    })
    val job = launch(start = CoroutineStart.UNDISPATCHED) { updates.download(update) }
    delay(50)
    job.cancel()
    job.join()
    assertTrue(job.isCancelled)
    assertEquals(DesktopUpdateState.Available(update), updates.state.value)
  }

  @Test
  fun `download failure keeps the update and a stable code`() = runBlocking {
    val updates = controller(download = { _, _, progress ->
      progress(5, 10)
      throw UpdateException(UpdateErrorCode.ChecksumMismatch, "mismatch")
    })
    updates.download(update)
    val failed = assertIs<DesktopUpdateState.Failed>(updates.state.value)
    assertEquals(UpdateErrorCode.ChecksumMismatch, failed.error.code)
    assertEquals(update, failed.update)
  }

  @Test
  fun `disconnect timeout is an install failure, not caller cancellation`() = runBlocking {
    val updates = controller(install = { _, _, disconnect ->
      withTimeout(20) { disconnect() }
      error("unreachable")
    })
    val ready = DesktopUpdateState.Ready(update, installerPath)
    val result = updates.install(ready) { delay(1_000) }
    assertTrue(result.exceptionOrNull() is TimeoutCancellationException)
    val failed = assertIs<DesktopUpdateState.Failed>(updates.state.value)
    assertEquals(UpdateErrorCode.InstallFailed, failed.error.code)
  }

  @Test
  fun `cancelled install caller keeps the verified download installable`() = runBlocking {
    val updates = controller(install = { _, _, _ ->
      delay(1_000)
      error("unreachable")
    })
    val ready = DesktopUpdateState.Ready(update, installerPath)
    val job = launch { updates.install(ready) {} }
    delay(50)
    job.cancel()
    job.join()
    assertTrue(job.isCancelled)
    assertEquals(ready, updates.state.value)
  }

  @Test
  fun `background schedule checks soon after start and then periodically`() = runBlocking {
    val calls = AtomicInteger()
    val updates = controller(check = { calls.incrementAndGet(); null })
    val job = launch { updates.runBackgroundChecks(firstDelayMillis = 20, intervalMillis = 20) }
    delay(10)
    assertEquals(0, calls.get())
    while (calls.get() < 3) delay(5)
    job.cancel()
    job.join()
    assertTrue(DesktopUpdateController.FIRST_BACKGROUND_CHECK_DELAY_MS in 60_000L..120_000L)
  }
}

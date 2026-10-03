package uk.senyasenyavski.veilark.helper

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConnectionPreparationTest {
  @Test fun `preparation owns busy state immediately rejects repeat and cancellation prevents late start`() = runBlocking {
    val preparation = ConnectionPreparation(this)
    val entered = CompletableDeferred<Unit>()
    var connected = false
    var errorDisplayed = false
    assertTrue(preparation.start({ entered.complete(Unit); delay(Long.MAX_VALUE); connected = true }, { errorDisplayed = true }))
    assertTrue(preparation.busy.value)
    assertFalse(preparation.start({ connected = true }, {}))
    entered.await()
    preparation.cancelAndJoin()
    assertFalse(preparation.busy.value)
    assertFalse(connected)
    assertFalse(errorDisplayed)
    assertTrue(preparation.start({ connected = true }, {}))
  }

  @Test fun `cancel before dispatched action starts releases ownership`() = runBlocking {
    val preparation = ConnectionPreparation(this)
    var started = false
    preparation.start({ started = true }, {})
    preparation.cancelAndJoin()
    assertFalse(started)
    assertFalse(preparation.busy.value)
  }
}

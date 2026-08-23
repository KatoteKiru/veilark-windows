package uk.senyasenyavski.veilark.net

import java.nio.ByteBuffer
import java.util.concurrent.ExecutionException
import java.util.concurrent.Flow
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BoundedHttpBodyTest {
  @Test
  fun `subscriber collects a response within the limit`() {
    val subscriber = BoundedByteArraySubscriber(5, "too large")
    val subscription = RecordingSubscription()
    subscriber.onSubscribe(subscription)
    subscriber.onNext(listOf(ByteBuffer.wrap(byteArrayOf(1, 2)), ByteBuffer.wrap(byteArrayOf(3))))
    subscriber.onComplete()

    assertContentEquals(byteArrayOf(1, 2, 3), subscriber.body.toCompletableFuture().get())
    assertEquals(2, subscription.requests)
  }

  @Test
  fun `subscriber cancels before retaining an oversized response`() {
    val subscriber = BoundedByteArraySubscriber(3, "bounded body rejected")
    val subscription = RecordingSubscription()
    subscriber.onSubscribe(subscription)
    subscriber.onNext(listOf(ByteBuffer.wrap(byteArrayOf(1, 2, 3, 4))))

    val failure = assertFailsWith<ExecutionException> {
      subscriber.body.toCompletableFuture().get()
    }
    assertTrue(failure.cause?.message.orEmpty().contains("bounded body rejected"))
    assertTrue(subscription.cancelled)
  }

  private class RecordingSubscription : Flow.Subscription {
    var requests = 0L
    var cancelled = false

    override fun request(value: Long) {
      requests += value
    }

    override fun cancel() {
      cancelled = true
    }
  }
}

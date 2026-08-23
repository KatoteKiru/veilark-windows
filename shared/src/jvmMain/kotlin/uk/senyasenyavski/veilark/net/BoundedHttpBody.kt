package uk.senyasenyavski.veilark.net

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.Flow

/**
 * Collects an HTTP body while enforcing the limit before bytes are retained in
 * memory. BodyHandlers.ofByteArray() applies no such bound and can exhaust the
 * desktop JVM before callers get a chance to validate ByteArray.size.
 */
internal fun boundedByteArrayHandler(
  maximumBytes: Int,
  tooLargeMessage: String,
): HttpResponse.BodyHandler<ByteArray> {
  require(maximumBytes >= 0) { "Лимит HTTP-ответа не может быть отрицательным" }
  return HttpResponse.BodyHandler {
    BoundedByteArraySubscriber(maximumBytes, tooLargeMessage)
  }
}

internal class BoundedByteArraySubscriber(
  private val maximumBytes: Int,
  private val tooLargeMessage: String,
) : HttpResponse.BodySubscriber<ByteArray> {
  private val body = CompletableFuture<ByteArray>()
  private val output = ByteArrayOutputStream(minOf(maximumBytes, INITIAL_CAPACITY))
  private var subscription: Flow.Subscription? = null

  override fun getBody(): CompletionStage<ByteArray> = body

  override fun onSubscribe(value: Flow.Subscription) {
    if (subscription != null || body.isDone) {
      value.cancel()
      return
    }
    subscription = value
    value.request(1)
  }

  override fun onNext(items: List<ByteBuffer>) {
    if (body.isDone) return
    try {
      items.forEach { buffer ->
        val count = buffer.remaining()
        if (count > maximumBytes - output.size()) {
          subscription?.cancel()
          body.completeExceptionally(IOException(tooLargeMessage))
          return
        }
        val bytes = ByteArray(count)
        buffer.get(bytes)
        output.write(bytes)
      }
      subscription?.request(1)
    } catch (error: Throwable) {
      subscription?.cancel()
      body.completeExceptionally(error)
    }
  }

  override fun onError(error: Throwable) {
    body.completeExceptionally(error)
  }

  override fun onComplete() {
    body.complete(output.toByteArray())
  }

  private companion object {
    const val INITIAL_CAPACITY = 8 * 1024
  }
}

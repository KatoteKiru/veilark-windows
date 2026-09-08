package uk.senyasenyavski.veilark.update

import org.json.JSONObject
import uk.senyasenyavski.veilark.net.boundedByteArrayHandler
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.FileTime
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.time.Duration
import java.util.Base64
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

data class AppUpdate(
  val versionCode: Int,
  val versionName: String,
  val installerUrl: String,
  val sha256: String,
  val size: Long,
  val notes: String,
)

/** Thread-safe cancellation flag for the blocking/resumable HTTP download. */
class UpdateDownloadCancellation {
  private val cancelled = AtomicBoolean(false)

  fun cancel() {
    cancelled.set(true)
  }

  fun isCancelled(): Boolean = cancelled.get()
}

class UpdateClient(
  private val manifestUri: URI = URI(DEFAULT_MANIFEST_URL),
  private val publicKeyBase64: String = PUBLIC_KEY,
  private val currentVersionCode: Int = CURRENT_VERSION_CODE,
  private val allowedHost: String = DEFAULT_UPDATE_HOST,
  private val allowedPort: Int = DEFAULT_UPDATE_PORT,
  private val client: HttpClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(12))
    .followRedirects(HttpClient.Redirect.NEVER)
    .build(),
) {
  fun check(): AppUpdate? {
    requireTrustedUri(manifestUri)
    val request = HttpRequest.newBuilder(manifestUri)
      .timeout(Duration.ofSeconds(30))
      .header("User-Agent", "Veilark-Windows/$CURRENT_VERSION_NAME")
      .header("Accept", "application/json")
      .GET()
      .build()
    val response = client.send(
      request,
      boundedByteArrayHandler(MAX_MANIFEST_SIZE, "Манифест обновления слишком большой"),
    )
    require(response.statusCode() == 200) {
      "Сервер обновлений ответил HTTP ${response.statusCode()}"
    }
    require(response.body().size <= MAX_MANIFEST_SIZE) {
      "Манифест обновления слишком большой"
    }
    return parseAvailableUpdate(response.body().toString(Charsets.UTF_8))
  }

  fun parseAvailableUpdate(jsonValue: String): AppUpdate? =
    parseAndVerify(jsonValue).takeIf { it.versionCode > currentVersionCode }

  fun parseAndVerify(jsonValue: String): AppUpdate {
    val json = JSONObject(jsonValue)
    val update = AppUpdate(
      versionCode = json.getInt("versionCode"),
      versionName = json.getString("versionName").trim(),
      installerUrl = json.getString("installerUrl").trim(),
      sha256 = json.getString("sha256").uppercase(),
      size = json.getLong("size"),
      notes = json.optString("notes").take(MAX_NOTES_LENGTH),
    )
    require(update.versionCode > 0 && update.versionName.isNotBlank()) {
      "Некорректная версия обновления"
    }
    require(update.versionName.matches(SAFE_VERSION_NAME)) {
      "Некорректное имя версии обновления"
    }
    require(update.size in 1..MAX_INSTALLER_SIZE) {
      "Некорректный размер обновления"
    }
    require(update.sha256.matches(Regex("[0-9A-F]{64}"))) {
      "Некорректная контрольная сумма"
    }
    requireTrustedUri(URI(update.installerUrl))
    verifySignature(canonicalPayload(update), json.getString("signature"))
    val notesSignature = json.optString("notesSignature").trim()
    if (update.versionCode >= SIGNED_NOTES_VERSION_CODE) {
      require(notesSignature.isNotBlank()) {
        "Манифест обновления не подписывает описание версии"
      }
      verifySignature(canonicalPayloadWithNotes(update), notesSignature)
    } else if (notesSignature.isNotBlank()) {
      verifySignature(canonicalPayloadWithNotes(update), notesSignature)
    }
    return update
  }

  fun download(
    update: AppUpdate,
    directory: Path,
    onProgress: (Long, Long) -> Unit = { _, _ -> },
    isCancelled: () -> Boolean = { false },
  ): Path {
    throwIfCancelled(isCancelled)
    require(update.size in 1..MAX_INSTALLER_SIZE) { "Некорректный размер обновления" }
    require(update.versionName.matches(SAFE_VERSION_NAME)) { "Некорректное имя версии обновления" }
    val installerUri = URI(update.installerUrl)
    requireTrustedUri(installerUri)
    Files.createDirectories(directory)
    val finalPath = directory.resolve("Veilark-${update.versionName}.exe")
    val partialPath = directory.resolve(".Veilark-${update.versionCode}.download")
    if (Files.isRegularFile(finalPath) && Files.size(finalPath) == update.size) {
      verifyDownloadedInstaller(finalPath, update)
      return finalPath
    }
    if (
      Files.exists(partialPath, LinkOption.NOFOLLOW_LINKS) &&
      !Files.isRegularFile(partialPath, LinkOption.NOFOLLOW_LINKS)
    ) {
      Files.delete(partialPath)
    }
    if (Files.exists(partialPath, LinkOption.NOFOLLOW_LINKS) && Files.size(partialPath) > update.size) {
      Files.delete(partialPath)
    }
    val offset = if (Files.isRegularFile(partialPath, LinkOption.NOFOLLOW_LINKS)) {
      Files.size(partialPath)
    } else {
      0L
    }
    if (offset == update.size) {
      verifyDownloadedInstaller(partialPath, update)
      promoteDownload(partialPath, finalPath)
      return finalPath
    }
    val requestBuilder = HttpRequest.newBuilder(installerUri)
      .timeout(Duration.ofMinutes(5))
      .header("User-Agent", "Veilark-Windows/$CURRENT_VERSION_NAME")
      .GET()
    if (offset > 0L) requestBuilder.header("Range", "bytes=$offset-")
    val response = sendCancellable(requestBuilder.build(), isCancelled)
    val append = offset > 0L && response.statusCode() == 206
    require(response.statusCode() == 200 || append) {
      response.body().close()
      "Сервер обновлений ответил HTTP ${response.statusCode()}"
    }
    val start = if (append) offset else 0L
    if (append) {
      require(contentRangeMatches(response.headers().firstValue("Content-Range").orElse(null), start, update.size)) {
        response.body().close()
        "Сервер обновлений вернул неверный диапазон"
      }
    }
    val options: Array<OpenOption> = if (append) {
      arrayOf(
        java.nio.file.StandardOpenOption.CREATE,
        java.nio.file.StandardOpenOption.APPEND,
        LinkOption.NOFOLLOW_LINKS,
      )
    } else {
      arrayOf(
        java.nio.file.StandardOpenOption.CREATE,
        java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
        LinkOption.NOFOLLOW_LINKS,
      )
    }
    response.body().use { input ->
      Files.newOutputStream(partialPath, *options).buffered(BUFFER_SIZE).use { output ->
        copyBounded(input, output, start, update.size, onProgress, isCancelled)
      }
    }
    require(Files.size(partialPath) == update.size) { "Обновление загрузилось не полностью" }
    verifyDownloadedInstaller(partialPath, update)
    promoteDownload(partialPath, finalPath)
    return finalPath
  }

  fun download(
    update: AppUpdate,
    directory: Path,
    cancellation: UpdateDownloadCancellation,
    onProgress: (Long, Long) -> Unit = { _, _ -> },
  ): Path = download(update, directory, onProgress, cancellation::isCancelled)

  /**
   * Re-checks an already downloaded installer. This is intentionally public so
   * the detached Windows update helper can be scheduled only after the exact
   * artifact from the signed manifest has been validated again.
   */
  fun verifyDownloadedInstaller(path: Path, update: AppUpdate) {
    require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
      "Установщик обновления не найден"
    }
    if (Files.size(path) != update.size) {
      Files.deleteIfExists(path)
      throw IllegalArgumentException("Обновление загрузилось не полностью")
    }
    val actual = sha256(path)
    if (!MessageDigest.isEqual(
        actual.toByteArray(Charsets.US_ASCII),
        update.sha256.toByteArray(Charsets.US_ASCII),
      )
    ) {
      Files.deleteIfExists(path)
      throw IllegalArgumentException("Контрольная сумма установщика не совпадает")
    }
  }

  /** Removes both the resumable fragment and the verified artifact on explicit cancellation. */
  fun discardDownload(update: AppUpdate, directory: Path) {
    Files.deleteIfExists(directory.resolve(".Veilark-${update.versionCode}.download"))
    if (update.versionName.matches(SAFE_VERSION_NAME)) {
      Files.deleteIfExists(directory.resolve("Veilark-${update.versionName}.exe"))
    }
  }

  /**
   * Removes abandoned downloads without touching a recent/resumable one. The
   * update directory is application-owned, and symbolic links are ignored.
   */
  fun cleanupDownloads(
    directory: Path,
    olderThan: FileTime = FileTime.fromMillis(
      System.currentTimeMillis() - DEFAULT_STALE_DOWNLOAD_AGE_MILLIS,
    ),
  ): Int {
    if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) return 0
    var removed = 0
    Files.list(directory).use { entries ->
      entries.forEach { candidate ->
        val name = candidate.fileName.toString()
        val managed = PARTIAL_DOWNLOAD.matches(name) || FINAL_INSTALLER.matches(name)
        if (
          managed &&
          Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS) &&
          Files.getLastModifiedTime(candidate, LinkOption.NOFOLLOW_LINKS) < olderThan &&
          Files.deleteIfExists(candidate)
        ) {
          removed += 1
        }
      }
    }
    return removed
  }

  fun canonicalPayload(update: AppUpdate): String = buildString {
    appendLine(update.versionCode)
    appendLine(update.versionName)
    appendLine(update.installerUrl)
    appendLine(update.sha256)
    append(update.size)
  }

  fun canonicalPayloadWithNotes(update: AppUpdate): String = buildString {
    append(canonicalPayload(update))
    append('\n')
    append(update.notes)
  }

  private fun requireTrustedUri(uri: URI) {
    require(
      uri.scheme.equals("https", ignoreCase = true) &&
        uri.host.equals(allowedHost, ignoreCase = true) &&
        effectivePort(uri) == allowedPort &&
        uri.userInfo == null,
    ) { "Недоверенный адрес обновления" }
  }

  private fun effectivePort(uri: URI): Int =
    if (uri.port >= 0) uri.port else if (uri.scheme.equals("https", true)) 443 else -1

  private fun verifySignature(payload: String, signatureValue: String) {
    val key = KeyFactory.getInstance("Ed25519").generatePublic(
      X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64)),
    )
    val verifier = Signature.getInstance("Ed25519")
    verifier.initVerify(key)
    verifier.update(payload.toByteArray(Charsets.UTF_8))
    require(verifier.verify(Base64.getDecoder().decode(signatureValue))) {
      "Подпись манифеста обновления недействительна"
    }
  }

  private fun sha256(path: Path): String {
    val digest = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(path).buffered(BUFFER_SIZE).use { input ->
      val buffer = ByteArray(BUFFER_SIZE)
      while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        digest.update(buffer, 0, count)
      }
    }
    return digest.digest().joinToString("") { "%02X".format(it) }
  }

  private fun promoteDownload(partialPath: Path, finalPath: Path) {
    try {
      Files.move(
        partialPath,
        finalPath,
        StandardCopyOption.REPLACE_EXISTING,
        StandardCopyOption.ATOMIC_MOVE,
      )
    } catch (_: AtomicMoveNotSupportedException) {
      Files.move(partialPath, finalPath, StandardCopyOption.REPLACE_EXISTING)
    }
  }

  private fun sendCancellable(
    request: HttpRequest,
    isCancelled: () -> Boolean,
  ): HttpResponse<InputStream> {
    val pending = client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
    try {
      while (true) {
        throwIfCancelled(isCancelled)
        try {
          return pending.get(CANCELLATION_POLL_MILLIS, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
          // Poll the explicit UI cancellation token while headers are pending.
        }
      }
    } catch (cancelled: CancellationException) {
      pending.cancel(true)
      throw cancelled
    } catch (interrupted: InterruptedException) {
      pending.cancel(true)
      Thread.currentThread().interrupt()
      throw CancellationException("Загрузка обновления отменена").also {
        it.initCause(interrupted)
      }
    } catch (failed: ExecutionException) {
      val cause = failed.cause ?: failed
      when (cause) {
        is RuntimeException -> throw cause
        is Error -> throw cause
        else -> throw IllegalStateException(cause.message ?: "Не удалось загрузить обновление", cause)
      }
    }
  }

  private fun copyBounded(
    input: InputStream,
    output: java.io.OutputStream,
    initial: Long,
    maximum: Long,
    onProgress: (Long, Long) -> Unit,
    isCancelled: () -> Boolean,
  ) {
    var copied = initial
    throwIfCancelled(isCancelled)
    onProgress(copied, maximum)
    val buffer = ByteArray(BUFFER_SIZE)
    while (true) {
      val count = input.read(buffer)
      if (count < 0) break
      throwIfCancelled(isCancelled)
      copied += count
      require(copied <= maximum) { "Файл обновления больше заявленного размера" }
      output.write(buffer, 0, count)
      onProgress(copied, maximum)
    }
  }

  private fun throwIfCancelled(isCancelled: () -> Boolean) {
    if (isCancelled() || Thread.currentThread().isInterrupted) {
      throw CancellationException("Загрузка обновления отменена")
    }
  }

  companion object {
    const val CURRENT_VERSION_CODE = 318
    const val CURRENT_VERSION_NAME = "0.3.18"
    val CURRENT_RELEASE_NOTES = """
      Обновлено в 0.3.18
      • Исправлено открытие скрытого или свёрнутого окна.
      • Подключение и подготовку геоданных можно отменить; при ошибке остановки доступна повторная попытка.
      • Исправлен приоритет ручных маршрутов Trust; неподдерживаемые пересечения явно отклоняются.
      • Убрана очистка чужих VPN-адаптеров. Используемые геоданные защищены от удаления.
    """.trimIndent()
    val CURRENT_RELEASE_NOTES_EN = """
      Updated in 0.3.18
      • Hidden and minimized windows reopen reliably.
      • Connection preparation is cancellable; failed teardown keeps a Retry stop action.
      • Fixed Trust manual-route priority; unsupported overlaps are rejected explicitly.
      • Foreign VPN adapters are left untouched. Active geo files are protected from cleanup.
    """.trimIndent()
    const val DEFAULT_MANIFEST_URL =
      "https://nl2.senyasenyavski.uk:2096/veilark/windows/manifest.json"
    const val DEFAULT_UPDATE_HOST = "nl2.senyasenyavski.uk"
    const val DEFAULT_UPDATE_PORT = 2096
    private const val MAX_MANIFEST_SIZE = 128 * 1024
    private const val MAX_INSTALLER_SIZE = 300L * 1024L * 1024L
    private const val MAX_NOTES_LENGTH = 4_000
    private const val SIGNED_NOTES_VERSION_CODE = 312
    private const val BUFFER_SIZE = 128 * 1024
    private const val CANCELLATION_POLL_MILLIS = 100L
    private const val DEFAULT_STALE_DOWNLOAD_AGE_MILLIS = 7L * 24L * 60L * 60L * 1_000L
    private const val PUBLIC_KEY =
      "MCowBQYDK2VwAyEA0YGkIFZV3+5QovppegKn/lxq35kKlbyLd1YW0JR6At0="
    private val CONTENT_RANGE =
      Regex("""bytes\s+(\d+)-(\d+)/(\d+)""", RegexOption.IGNORE_CASE)
    private val SAFE_VERSION_NAME = Regex("[0-9A-Za-z][0-9A-Za-z._-]{0,63}")
    private val PARTIAL_DOWNLOAD = Regex("\\.Veilark-\\d+\\.download")
    private val FINAL_INSTALLER = Regex("Veilark-[0-9A-Za-z][0-9A-Za-z._-]{0,63}\\.exe")

    internal fun contentRangeMatches(value: String?, offset: Long, total: Long): Boolean {
      val match = CONTENT_RANGE.matchEntire(value?.trim().orEmpty()) ?: return false
      return match.groupValues[1].toLongOrNull() == offset &&
        match.groupValues[2].toLongOrNull() == total - 1L &&
        match.groupValues[3].toLongOrNull() == total
    }
  }
}

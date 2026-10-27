package com.example.veilark.profile

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.ByteBuffer
import java.util.Base64

/**
 * Validates existing Android url/code/t import envelopes before the profile importer.
 *
 * Only the two resolved payload families the client can process are accepted: an HTTPS
 * subscription URL or a protocol-native TrustTunnel link (`tt://<host>...` or the
 * query-only `tt://?<payload>` form emitted by `trusttunnel_endpoint -f deeplink`).
 * The rules mirror the Android and macOS clients so that one link works everywhere.
 *
 * The inner URL is a bearer credential: it is never logged and every failure collapses
 * to `null` instead of surfacing the value through an exception message.
 */
object ImportDeepLink {
  private const val SCHEME = "veilark"
  private const val HOST = "import"
  private const val PARAM = "url"
  private const val OPERATOR_PROFILE_ORIGIN = "https://nl2.senyasenyavski.uk:2096/veilark/"
  private const val MAX_LINK_LENGTH = 32 * 1024 // a fully percent-encoded 8192-char payload is ~24 KiB
  const val MAX_PAYLOAD_LENGTH = 8192 // matches the Mini App isVeilarkImportTarget limit; tt://? payloads may embed certificates

  /** Returns the first validated import payload carried by a process command line. */
  fun fromArguments(arguments: Iterable<String>): String? =
    arguments.firstNotNullOfOrNull { argument ->
      argument.takeIf { looksLikeEnvelope(it) }?.let { raw ->
        parseUri(raw)?.let { target -> if (isOpaqueEnvelope(raw)) raw else target }
      }
    }

  /** True when a command-line argument is addressed to this handler at all. */
  fun looksLikeEnvelope(value: String): Boolean =
    value.length <= MAX_LINK_LENGTH && value.startsWith("$SCHEME:", ignoreCase = true)

  fun parseUri(raw: String): String? = parseEnvelope(raw)?.let { validateTarget(it, allowEnvelope = false) }

  private fun parseEnvelope(raw: String): String? {
    if (raw.length > MAX_LINK_LENGTH) return null
    val uri = runCatching { URI(raw) }.getOrNull() ?: return null
    if (!uri.scheme.equals(SCHEME, ignoreCase = true) ||
      !uri.host.equals(HOST, ignoreCase = true) ||
      (uri.path != null && uri.path != "" && uri.path != "/") ||
      uri.fragment != null ||
      uri.userInfo != null ||
      uri.port != -1
    ) {
      return null
    }
    val query = uri.rawQuery ?: return null
    val items = query.split('&').filter(String::isNotEmpty)
    if (items.size != 1) return null
    val item = items.single()
    val equal = item.indexOf('=')
    if (equal < 0) return null
    return when (decode(item.substring(0, equal))) {
      PARAM -> decode(item.substring(equal + 1))
      "code" -> decodeImportCode(item.substring(equal + 1))
      "t" -> decodeCompactToken(item.substring(equal + 1))
      else -> null
    }
  }

  // Exact existing Android encodings. These hide display text, not encryption.
  private fun decodeImportCode(code: String): String? = runCatching {
    require(code.isNotEmpty() && code.all { it.isAsciiBase64Url() })
    val bytes = Base64.getUrlDecoder().decode(code)
    require(bytes.size <= MAX_PAYLOAD_LENGTH)
    StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()
  }.getOrNull()

  private fun decodeCompactToken(token: String): String? = runCatching {
    require(token.length == 43 && token.all { it.isAsciiBase64Url() })
    val bytes = Base64.getUrlDecoder().decode(token)
    require(bytes.size == 32 && Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) == token)
    OPERATOR_PROFILE_ORIGIN + bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) } + ".json"
  }.getOrNull()

  private fun Char.isAsciiBase64Url() = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9' || this == '-' || this == '_'

  private fun isOpaqueEnvelope(value: String): Boolean = runCatching {
    URI(value).rawQuery?.let { it.startsWith("code=") || it.startsWith("t=") } == true
  }.getOrDefault(false)

  fun validatePayload(value: String): String? = validateTarget(value, allowEnvelope = true)

  private fun validateTarget(value: String, allowEnvelope: Boolean): String? {
    if (value.isEmpty() || value.length > MAX_PAYLOAD_LENGTH ||
      value.any { it == '\u0000' || it == '\r' || it == '\n' || it.isISOControl() }
    ) {
      return null
    }
    val payload = runCatching { URI(value) }.getOrNull() ?: return null
    return when {
      allowEnvelope && payload.scheme.equals(SCHEME, true) && isOpaqueEnvelope(value) &&
        parseUri(value) != null -> value
      payload.scheme.equals("https", ignoreCase = true) &&
        payload.host?.isNotBlank() == true &&
        payload.userInfo == null &&
        (payload.port == -1 || payload.port in 1..65535) &&
        payload.fragment == null -> value
      value.startsWith("tt://", ignoreCase = true) &&
        payload.scheme.equals("tt", ignoreCase = true) &&
        payload.host?.isNotBlank() == true &&
        payload.userInfo == null &&
        payload.fragment == null -> value
      value.startsWith("tt://?", ignoreCase = true) &&
        payload.scheme.equals("tt", ignoreCase = true) &&
        payload.rawAuthority == null &&
        payload.rawPath.isNullOrEmpty() &&
        payload.rawQuery?.isNotBlank() == true &&
        payload.fragment == null -> value
      else -> null
    }
  }

  private fun decode(value: String): String =
    runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8.name()) }
      .getOrElse { "" }
}

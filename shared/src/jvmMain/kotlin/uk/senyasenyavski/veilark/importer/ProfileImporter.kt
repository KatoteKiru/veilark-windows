package uk.senyasenyavski.veilark.importer

import com.example.veilark.profile.SubscriptionParser
import com.example.veilark.profile.SubscriptionFetcher
import com.example.veilark.profile.CoreProfileJson
import com.example.veilark.profile.CoreArgonProfile
import com.example.veilark.profile.CoreArgonHandshakeProfile
import com.example.veilark.profile.ImportDeepLink
import uk.senyasenyavski.veilark.model.ImportResult
import uk.senyasenyavski.veilark.model.Node
import uk.senyasenyavski.veilark.model.Profile
import uk.senyasenyavski.veilark.model.VpnEngine
import uk.senyasenyavski.veilark.update.UpdateClient
import java.net.URI
import java.net.URLDecoder
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

class ProfileImporter(
  private val parser: SubscriptionParser = SubscriptionParser(),
  private val fetchSubscription: (String) -> ByteArray = { SubscriptionFetcher.fetchBlocking(it) },
  private val allowCoreProfiles: Boolean = VpnEngine.VeilarkCore.availableInProduct,
) {
  fun fromText(text: String, sourceLabel: String = "Вставка"): ImportResult {
    val value = text.trim()
    if (value.startsWith("https://", ignoreCase = true)) return fromHttps(value)
    if (ImportDeepLink.looksLikeEnvelope(value)) {
      val target = ImportDeepLink.parseUri(value)
        ?: return ImportResult.Failure("Адрес подписки некорректен")
      return if (target.startsWith("https://", ignoreCase = true)) fromHttps(target)
        else compile(target.toByteArray(Charsets.UTF_8), sourceLabel)
    }
    return compile(text.toByteArray(Charsets.UTF_8), sourceLabel)
  }

  fun fromFile(path: Path): ImportResult = runCatching {
    require(Files.size(path) <= MAX_BYTES) { "Файл профиля больше 4 МБ" }
    compile(Files.readAllBytes(path), path.fileName.toString())
  }.getOrElse { ImportResult.Failure(it.safeMessage()) }

  fun fromHttps(url: String): ImportResult = runCatching {
    val uri = URI(url.trim())
    require(uri.scheme.equals("https", ignoreCase = true)) {
      "Подписка должна использовать HTTPS"
    }
    require(!uri.host.isNullOrBlank()) { "В ссылке подписки нет адреса сервера" }
    val body = fetchSubscription(uri.toString())
    compile(body, uri.host, uri.toString())
  }.getOrElse { ImportResult.Failure(it.safeMessage()) }

  private fun compile(
    bytes: ByteArray,
    sourceLabel: String,
    sourceUrl: String? = null,
  ): ImportResult = runCatching {
    val text = bytes.toString(Charsets.UTF_8).trim().removePrefix("\uFEFF")
    // Core is a separate native engine. Never interpret its credential document
    // as a sing-box config, including documents with nested proxy outbounds.
    // The portable profile codec is prepared separately; Windows runtime and
    // per-device enrollment must be integrated before enabling this engine.
    val coreSchema = if (text.startsWith("{")) {
      // Malformed/ambiguous schema must not fall through into another engine.
      CoreProfileJson.topLevelSchema(text)
    } else null
    if (coreSchema?.let { it.startsWith("veilark-core-") || it.startsWith("veilark-argon-") } == true) {
      if (allowCoreProfiles) {
        return@runCatching CoreArgonProfile.parse(text).use { core ->
          require(core.handshakeProfile == CoreArgonHandshakeProfile.LEGACY) { "Unsupported Core wire profile" }
          val id = stableId(text)
          ImportResult.Success(Profile(
            id = id, name = core.name, engine = VpnEngine.VeilarkCore,
            config = text, nodes = listOf(Node("core-$id", core.name, "Fairway")),
            sourceLabel = sourceLabel.take(100), sourceUrl = sourceUrl,
          ), 0)
        }
      }
      return@runCatching ImportResult.Failure(
        "Подключение Fairway в этой версии Veilark для Windows пока недоступно.",
      )
    }
    if (looksLikeTrustTunnelToml(text)) {
      return@runCatching trustTunnelProfile(
        configs = listOf(text),
        sourceLabel = sourceLabel,
        sourceUrl = sourceUrl,
      )
    }
    val trustTunnelLinks = runCatching {
      parser.extractTrustTunnelLinks(bytes)
    }.getOrDefault(emptyList())
    val result = runCatching { parser.compile(bytes) }.getOrElse { parseFailure ->
      if (trustTunnelLinks.isNotEmpty()) {
        return@runCatching trustTunnelProfile(
          configs = trustTunnelLinks,
          sourceLabel = sourceLabel,
          sourceUrl = sourceUrl,
        )
      }
      throw parseFailure
    }
    val singBoxProfile = Profile(
      id = stableId(result.json),
      name = sourceLabel.take(80),
      engine = VpnEngine.SingBox,
      config = result.json,
      nodes = result.nodes.map { Node(it.tag, it.name, it.protocol) },
      sourceLabel = sourceLabel.take(100),
      sourceUrl = sourceUrl,
    )
    val profiles = buildList {
      add(singBoxProfile)
      if (result.trustTunnelLinks.isNotEmpty()) {
        add(createTrustTunnelProfile(result.trustTunnelLinks, sourceLabel, sourceUrl))
      }
    }
    ImportResult.Success(profiles, result.rejectedCount)
  }.getOrElse { ImportResult.Failure(it.safeMessage()) }

  private fun trustTunnelProfile(
    configs: List<String>,
    sourceLabel: String,
    sourceUrl: String? = null,
    rejected: Int = 0,
  ): ImportResult.Success = ImportResult.Success(
    profile = createTrustTunnelProfile(configs, sourceLabel, sourceUrl),
    rejectedProfiles = rejected,
  )

  private fun createTrustTunnelProfile(
    configs: List<String>,
    sourceLabel: String,
    sourceUrl: String? = null,
  ): Profile {
    require(configs.isNotEmpty()) { "Профили TrustTunnel не найдены" }
    val endpointConfigs = linkedMapOf<String, String>()
    val nodes = configs.distinct().mapIndexed { index, config ->
      val tag = "tt-${stableId(config)}"
      endpointConfigs[tag] = config
      Node(
        tag = tag,
        name = trustTunnelName(config, index, sourceLabel),
        protocol = "TrustTunnel",
      )
    }
    return Profile(
      id = stableId(configs.joinToString("\n")),
      name = sourceLabel.take(80),
      engine = VpnEngine.TrustTunnel,
      config = configs.first(),
      nodes = nodes,
      sourceLabel = sourceLabel.take(100),
      endpointConfigs = endpointConfigs,
      sourceUrl = sourceUrl,
    )
  }

  private fun trustTunnelName(
    config: String,
    index: Int,
    sourceLabel: String,
  ): String {
    if (looksLikeTrustTunnelToml(config)) {
      Regex("""(?m)^\s*name\s*=\s*"([^"]+)"""")
        .find(config)
        ?.groupValues
        ?.get(1)
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?.let { return it.take(80) }
    }
    val encodedName = config.substringAfterLast('#', "")
    val decodedName = runCatching {
      URLDecoder.decode(encodedName, Charsets.UTF_8)
    }.getOrDefault(encodedName).trim()
    return decodedName.takeIf(String::isNotBlank)?.take(80)
      ?: if (config.startsWith("tt://", ignoreCase = true)) {
        "TrustTunnel ${index + 1}"
      } else {
        sourceLabel.take(80)
      }
  }

  private fun looksLikeTrustTunnelToml(value: String): Boolean =
    value.contains("[endpoint]") &&
      (
        value.contains("addresses") ||
          value.contains("client_link") ||
          value.contains("username")
        )

  private fun stableId(value: String): String =
    MessageDigest.getInstance("SHA-256")
      .digest(value.toByteArray(Charsets.UTF_8))
      .take(10)
      .joinToString("") { "%02x".format(it.toInt() and 0xff) }

  internal companion object {
    private const val SING_BOX_COMPAT_VERSION = "1.13.21"
    private const val MAX_BYTES = 4L * 1024 * 1024
    // Only fixed, reviewed messages cross into UI. Parser/network exceptions can
    // contain a complete bearer URL, malformed JSON or private-key material.
    private val SAFE_FAILURE_MESSAGES = setOf(
      "Файл профиля больше 4 МБ",
      "Подписка должна использовать HTTPS",
      "В ссылке подписки нет адреса сервера",
      "Адрес подписки некорректен",
      "Разрешены только HTTPS-подписки",
      "Подписка больше 4 МБ",
      "Сервер вернул пустую подписку",
      "Слишком много перенаправлений",
      "Перенаправление без адреса",
      "Сервер подписки требует авторизацию (HTTP 401)",
      "Доступ к подписке запрещён или исчерпан лимит устройств (HTTP 403)",
      "Ссылка подписки не найдена или устарела (HTTP 404)",
      "Лимит устройств подписки исчерпан (HTTP 409)",
      "Подписка удалена или истекла (HTTP 410)",
      "Подписка ещё подготавливается. Повторите обновление немного позже (HTTP 503)",
      "Слишком много запросов к подписке (HTTP 429)",
      "Не удалось загрузить подписку",
    )

    internal fun subscriptionUserAgent(): String =
      "SFA/$SING_BOX_COMPAT_VERSION Veilark/${UpdateClient.CURRENT_VERSION_NAME}"
  }

  private fun Throwable.safeMessage(): String {
    val detail = message
    if (detail in SAFE_FAILURE_MESSAGES) return checkNotNull(detail)
    if (this is java.net.URISyntaxException) return "Адрес подписки некорректен"
    return "Не удалось импортировать профиль. Проверьте ссылку или файл подписки."
  }
}

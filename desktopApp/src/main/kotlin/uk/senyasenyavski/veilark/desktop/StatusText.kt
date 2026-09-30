package uk.senyasenyavski.veilark.desktop

import uk.senyasenyavski.veilark.helper.UpdateInstallOutcome
import uk.senyasenyavski.veilark.helper.UpdateInstallPhase
import uk.senyasenyavski.veilark.model.VpnPhase
import uk.senyasenyavski.veilark.model.VpnStatusCode
import uk.senyasenyavski.veilark.update.UpdateError
import uk.senyasenyavski.veilark.update.UpdateErrorCode
import java.util.Locale

/**
 * Localized problem text for [VpnPhase.Error] and [VpnPhase.Degraded].
 *
 * The session reports a stable [VpnStatusCode]; UI text is chosen here from
 * the code in both languages. The phase's technical `message` is never
 * matched and is shown only as a last resort in the Russian UI, where it was
 * written (it is journal text, not a translation key).
 */
internal fun phaseProblemText(
  phase: VpnPhase,
  fallback: String,
  certificateFallback: String,
  language: UiLanguage,
): String = when (phase) {
  is VpnPhase.Error -> errorText(phase, fallback, certificateFallback, language)
  is VpnPhase.Degraded -> degradedText(phase, fallback, certificateFallback, language)
  else -> fallback
}

private fun errorText(
  phase: VpnPhase.Error,
  fallback: String,
  certificateFallback: String,
  language: UiLanguage,
): String {
  val detail = phase.detail
  return when (phase.code) {
    VpnStatusCode.ENGINE_NOT_FOUND -> language.text(
      "Ядро ${detail.ifBlank { "VPN" }} не установлено. Переустановите Veilark.",
      "The ${detail.ifBlank { "VPN" }} core is not installed. Reinstall Veilark.",
    )
    VpnStatusCode.CORE_NOT_FOUND -> language.text(
      "Не найден компонент ${detail.ifBlank { "VPN-ядра" }}. Переустановите Veilark из официального пакета.",
      "Component ${detail.ifBlank { "of the VPN core" }} is missing. Reinstall Veilark from the official package.",
    )
    VpnStatusCode.CONFIG_INVALID -> language.text(
      "${detail.ifBlank { "VPN-ядро" }} отклонило конфигурацию профиля. Обновите подписку или выберите другой узел.",
      "${detail.ifBlank { "The VPN core" }} rejected the profile configuration. Refresh the subscription or choose another server.",
    )
    VpnStatusCode.TUN_NAME_TAKEN -> language.text(
      "Предыдущий сетевой адаптер не был освобождён. Запустите Veilark от имени администратора, чтобы он смог его удалить.",
      "A previous network adapter was not released. Run Veilark as administrator so it can remove it.",
    )
    VpnStatusCode.TUN_NOT_CREATED -> language.text(
      "Windows не создала туннель вовремя. Повторите попытку.",
      "Windows did not create the tunnel in time. Try again.",
    )
    VpnStatusCode.CORE_NOT_READY -> language.text(
      "VPN-ядро не подтвердило подключение. Попробуйте другой узел.",
      "The VPN core did not confirm the connection. Try another server.",
    )
    VpnStatusCode.ELEVATION_REQUIRED -> language.text(
      "Windows не разрешила создать туннель. Запустите Veilark от имени администратора.",
      "Windows did not allow the tunnel to be created. Run Veilark as administrator.",
    )
    VpnStatusCode.COMPETING_TUNNEL -> language.text(
      "Активен другой VPN «${detail.ifBlank { "VPN" }}». Отключите его и повторите подключение Veilark.",
      "Another VPN (${detail.ifBlank { "VPN" }}) is active. Disconnect it, then try Veilark again.",
    )
    VpnStatusCode.CONNECT_TIMEOUT -> language.text(
      "Подключение не завершилось за 90 секунд. Попробуйте другой узел.",
      "The connection did not complete within 90 seconds. Try another server.",
    )
    VpnStatusCode.CORE_EXITED -> language.text(
      "${detail.ifBlank { "VPN-ядро" }} неожиданно завершилось. Проверьте журнал и повторите подключение.",
      "${detail.ifBlank { "The VPN core" }} stopped unexpectedly. Check the log and connect again.",
    )
    VpnStatusCode.STOP_FAILED -> language.text(
      "Не удалось остановить VPN-ядро. Повторите остановку.",
      "Could not stop the VPN core. Retry stop.",
    )
    else -> technicalFallback(phase.message, fallback, certificateFallback, language)
  }
}

private fun degradedText(
  phase: VpnPhase.Degraded,
  fallback: String,
  certificateFallback: String,
  language: UiLanguage,
): String {
  val detail = phase.detail
  return when (phase.code) {
    VpnStatusCode.ADAPTER_LOST -> language.text(
      "Туннельный адаптер ${detail} исчез из системы. Подключитесь заново.",
      "The tunnel adapter ${detail} disappeared. Connect again.",
    )
    VpnStatusCode.TUNNEL_MISSING -> language.text(
      "Туннель не создан. Подключитесь заново.",
      "The tunnel was not created. Connect again.",
    )
    VpnStatusCode.TRAFFIC_BYPASSES_TUNNEL -> language.text(
      "Интернет работает в обход туннеля — трафик не защищён. Подключитесь заново.",
      "Internet traffic bypasses the tunnel and is not protected. Connect again.",
    )
    VpnStatusCode.COMPETING_TUNNEL -> language.text(
      "Трафик идёт через «$detail», а не через туннель Veilark. Отключите другой VPN и подключитесь заново.",
      "Traffic goes through $detail instead of the Veilark tunnel. Disconnect the other VPN and connect again.",
    )
    VpnStatusCode.INTERNET_UNREACHABLE, VpnStatusCode.TUNNEL_NO_RESPONSE ->
      probeFailureText(detail, language) ?: if (probeFailureIsCertificate(detail)) certificateFallback else fallback
    else -> fallback
  }
}

/** [detail] is a stable probe code from the helper (timeout, dns, unreachable, tls, http, failed). */
private fun probeFailureText(detail: String, language: UiLanguage): String? = when (detail) {
  "timeout" -> language.text(
    "Узел не отвечает вовремя. Попробуйте другой сервер.",
    "The server timed out. Try another server.",
  )
  "unreachable" -> language.text(
    "Сервер проверки недоступен через туннель. Попробуйте другой сервер.",
    "The check server is unreachable through the tunnel. Try another server.",
  )
  "dns" -> language.text(
    "DNS не отвечает через туннель. Попробуйте другой сервер.",
    "DNS does not respond through the tunnel. Try another server.",
  )
  else -> null
}

private fun probeFailureIsCertificate(detail: String): Boolean = detail == "tls"

/** Java/TLS exception names are technical English tokens, not UI text. */
private fun technicalIsCertificate(message: String): Boolean {
  val lower = message.lowercase(Locale.ROOT)
  return lower.contains("pkix") || lower.contains("certpath") ||
    lower.contains("certificate") || lower.contains("sslhandshake")
}

private fun technicalFallback(
  message: String,
  fallback: String,
  certificateFallback: String,
  language: UiLanguage,
): String {
  if (technicalIsCertificate(message)) return certificateFallback
  if (language != UiLanguage.Russian) return fallback
  val normalized = message.replace(Regex("\\s+"), " ").trim()
  val lower = normalized.lowercase(Locale.ROOT)
  return if (
    normalized.isBlank() || normalized.length > 180 || lower.contains("exception") ||
    lower.contains("javax.") || lower.contains("java.") || lower.contains("sun.")
  ) fallback else normalized
}

/** Localized text for an OTA failure reported by [UpdateError]. */
internal fun updateErrorText(error: UpdateError, language: UiLanguage): String = when (error.code) {
  UpdateErrorCode.CheckFailed -> language.text(
    "Не удалось проверить обновления. Проверьте подключение к интернету.",
    "Could not check for updates. Check your internet connection.",
  )
  UpdateErrorCode.HttpStatus -> language.text(
    "Сервер обновлений ответил HTTP ${error.detail}. Повторите позже.",
    "The update server returned HTTP ${error.detail}. Try again later.",
  )
  UpdateErrorCode.ManifestInvalid -> language.text(
    "Сервер обновлений вернул некорректный манифест.",
    "The update server returned an invalid manifest.",
  )
  UpdateErrorCode.SignatureInvalid -> language.text(
    "Подпись обновления недействительна. Обновление не будет установлено.",
    "The update signature is invalid. The update will not be installed.",
  )
  UpdateErrorCode.UntrustedAddress -> language.text(
    "Адрес обновления не входит в доверенный канал.",
    "The update address is outside the trusted channel.",
  )
  UpdateErrorCode.DownloadFailed -> language.text(
    "Не удалось загрузить обновление. Повторите попытку.",
    "Could not download the update. Try again.",
  )
  UpdateErrorCode.DownloadIncomplete -> language.text(
    "Обновление загрузилось не полностью. Повторите загрузку.",
    "The update download was incomplete. Download it again.",
  )
  UpdateErrorCode.ChecksumMismatch -> language.text(
    "Контрольная сумма установщика не совпадает. Файл удалён; повторите загрузку.",
    "The installer checksum does not match. The file was removed; download it again.",
  )
  UpdateErrorCode.InstallerMissing -> language.text(
    "Установщик обновления не найден. Загрузите обновление снова.",
    "The update installer was not found. Download the update again.",
  )
  UpdateErrorCode.InstallFailed -> language.text(
    "Не удалось подготовить установку обновления.",
    "Could not prepare the update installation.",
  )
}

/**
 * Result of the previous detached installation. The helper's own message is
 * technical journal text; the UI uses only the phase and version.
 */
internal fun updateOutcomeText(outcome: UpdateInstallOutcome, language: UiLanguage): String {
  val version = outcome.versionName
  return when (outcome.phase) {
    UpdateInstallPhase.Succeeded -> language.text(
      "Veilark обновлён до версии $version",
      "Veilark was updated to $version",
    )
    UpdateInstallPhase.Cancelled -> language.text(
      "Установка обновления $version отменена",
      "Installation of update $version was cancelled",
    )
    UpdateInstallPhase.Failed -> language.text(
      "Не удалось установить обновление $version. Подробности — в журнале.",
      "Could not install update $version. See the log for details.",
    )
    UpdateInstallPhase.Scheduled,
    UpdateInstallPhase.WaitingForExit,
    UpdateInstallPhase.Installing,
    UpdateInstallPhase.InteractiveFallback -> language.text(
      "Установка обновления $version не была завершена",
      "Installation of update $version did not finish",
    )
  }
}

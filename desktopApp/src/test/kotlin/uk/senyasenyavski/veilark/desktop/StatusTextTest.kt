package uk.senyasenyavski.veilark.desktop

import uk.senyasenyavski.veilark.helper.UpdateInstallOutcome
import uk.senyasenyavski.veilark.helper.UpdateInstallPhase
import uk.senyasenyavski.veilark.model.VpnPhase
import uk.senyasenyavski.veilark.model.VpnStatusCode
import uk.senyasenyavski.veilark.update.UpdateError
import uk.senyasenyavski.veilark.update.UpdateErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StatusTextTest {
  private fun String.hasCyrillic() = any { it in 'Ѐ'..'ӿ' }

  private val errorCodes = listOf(
    VpnStatusCode.ENGINE_NOT_FOUND, VpnStatusCode.CORE_NOT_FOUND, VpnStatusCode.CONFIG_INVALID,
    VpnStatusCode.TUN_NAME_TAKEN, VpnStatusCode.TUN_NOT_CREATED, VpnStatusCode.CORE_NOT_READY,
    VpnStatusCode.ELEVATION_REQUIRED, VpnStatusCode.COMPETING_TUNNEL, VpnStatusCode.CONNECT_TIMEOUT,
    VpnStatusCode.CORE_EXITED, VpnStatusCode.STOP_FAILED, VpnStatusCode.CORE_START_FAILED, "UNKNOWN",
  )
  private val degradedCodes = listOf(
    VpnStatusCode.DEGRADED, VpnStatusCode.ADAPTER_LOST, VpnStatusCode.TUNNEL_MISSING,
    VpnStatusCode.INTERNET_UNREACHABLE, VpnStatusCode.TRAFFIC_BYPASSES_TUNNEL,
    VpnStatusCode.TUNNEL_NO_RESPONSE, VpnStatusCode.COMPETING_TUNNEL, VpnStatusCode.HEALTH_CHECK_FAILED,
  )

  @Test
  fun `russian technical messages never leak into the english interface`() {
    val russianTechnical = "Конфигурация sing-box отклонена: неизвестное поле"
    errorCodes.forEach { code ->
      val text = phaseProblemText(
        VpnPhase.Error(russianTechnical, code, detail = "sing-box"),
        "fallback",
        "certificate",
        UiLanguage.English,
      )
      assertFalse(text.hasCyrillic(), "$code -> $text")
    }
    degradedCodes.forEach { code ->
      listOf("", "timeout", "tls", "dns", "unreachable", "failed").forEach { detail ->
        val text = phaseProblemText(
          VpnPhase.Degraded("нет ответа через туннель: тайм-аут проверки", code, detail),
          "fallback",
          "certificate",
          UiLanguage.English,
        )
        assertFalse(text.hasCyrillic(), "$code/$detail -> $text")
      }
    }
  }

  @Test
  fun `codes select texts without parsing messages`() {
    val competing = phaseProblemText(
      VpnPhase.Error("anything", VpnStatusCode.COMPETING_TUNNEL, detail = "happ-tun"),
      "fallback", "certificate", UiLanguage.English,
    )
    assertTrue("happ-tun" in competing)
    assertEquals(
      "certificate",
      phaseProblemText(VpnPhase.Degraded("x", VpnStatusCode.TUNNEL_NO_RESPONSE, "tls"), "fallback", "certificate", UiLanguage.English),
    )
    assertTrue(
      phaseProblemText(VpnPhase.Degraded("x", VpnStatusCode.TUNNEL_NO_RESPONSE, "timeout"), "fallback", "certificate", UiLanguage.Russian)
        .hasCyrillic(),
    )
  }

  @Test
  fun `every update error and outcome is localized in both languages`() {
    UpdateErrorCode.entries.forEach { code ->
      val error = UpdateError(code, "503")
      assertFalse(updateErrorText(error, UiLanguage.English).hasCyrillic(), code.name)
      assertTrue(updateErrorText(error, UiLanguage.Russian).hasCyrillic(), code.name)
    }
    UpdateInstallPhase.entries.forEach { phase ->
      val outcome = UpdateInstallOutcome(phase, 322, "0.3.22", 0, "Обновление установлено")
      assertFalse(updateOutcomeText(outcome, UiLanguage.English).hasCyrillic(), phase.name)
    }
  }
}

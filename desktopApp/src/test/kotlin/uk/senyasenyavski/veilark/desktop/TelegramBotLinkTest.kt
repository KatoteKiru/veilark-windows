package uk.senyasenyavski.veilark.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TelegramBotLinkTest {
  @Test
  fun acceptsTelegramHttpsHosts() {
    assertEquals(
      TelegramBotLink.DEFAULT_URL,
      TelegramBotLink.validate(TelegramBotLink.DEFAULT_URL)?.toString(),
    )
    assertEquals(
      "https://telegram.me/senyavpn_bot?start=client_windows",
      TelegramBotLink.validate("https://telegram.me/senyavpn_bot?start=client_windows")?.toString(),
    )
  }

  @Test
  fun rejectsUnsafeOrLookalikeUrls() {
    assertNull(TelegramBotLink.validate("http://t.me/senyavpn_bot"))
    assertNull(TelegramBotLink.validate("tg://resolve?domain=senyavpn_bot"))
    assertNull(TelegramBotLink.validate("https://t.me.evil.example/senyavpn_bot"))
    assertNull(TelegramBotLink.validate("https://user@t.me/senyavpn_bot"))
    assertNull(TelegramBotLink.validate("https://t.me/another_bot?start=client_windows"))
    assertNull(TelegramBotLink.validate("https://t.me/senyavpn_bot"))
    assertNull(TelegramBotLink.validate("https://t.me/senyavpn_bot?start=client_windows&next=evil"))
    assertNull(TelegramBotLink.validate("https://t.me:443/senyavpn_bot?start=client_windows"))
    assertNull(TelegramBotLink.validate("https://t.me/senyavpn_bot?start=client_windows#fragment"))
    assertNull(TelegramBotLink.validate("not a url"))
  }
}

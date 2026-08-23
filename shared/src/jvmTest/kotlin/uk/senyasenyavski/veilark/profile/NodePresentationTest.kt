package uk.senyasenyavski.veilark.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NodePresentationTest {
  @Test
  fun resolvesFlagsFromProviderEmojiCountryAndCityNames() {
    assertEquals("NL", NodePresentation.country("🇳🇱 Netherlands · VLESS")?.code)
    assertEquals("DE", NodePresentation.country("Frankfurt Reality")?.code)
    assertEquals("FI", NodePresentation.country("Хельсинки / Trojan")?.code)
    assertEquals("US", NodePresentation.country("US Miami")?.code)
  }

  @Test
  fun removesOnlyLeadingProviderFlagFromDisplayName() {
    assertEquals("Germany · VLESS", NodePresentation.displayName("🇩🇪 Germany · VLESS"))
    assertEquals("Premium 🇩🇪 Germany", NodePresentation.displayName("Premium 🇩🇪 Germany"))
  }

  @Test
  fun doesNotGuessCountryFromUnrelatedWords() {
    assertNull(NodePresentation.country("Default secure server"))
  }
}

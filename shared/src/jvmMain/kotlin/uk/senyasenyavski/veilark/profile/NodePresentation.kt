package uk.senyasenyavski.veilark.profile

import java.util.Locale

data class NodeCountry(
  val code: String,
  val flag: String,
)

/** Presentation-only location metadata; stored profiles remain schema-compatible. */
object NodePresentation {
  fun country(label: String): NodeCountry? {
    val code = flagCountryCode(label)
      ?: explicitCountryCode(label)
      ?: COUNTRY_ALIASES.entries.firstOrNull { (_, aliases) ->
        aliases.any { alias -> containsToken(label, alias) }
      }?.key
      ?: return null
    return NodeCountry(code, flagFor(code))
  }

  fun displayName(label: String): String {
    val trimmed = label.trim()
    if (trimmed.isEmpty()) return trimmed
    val first = trimmed.codePointAt(0)
    if (first !in REGIONAL_INDICATOR_RANGE) return trimmed
    val secondOffset = Character.charCount(first)
    if (secondOffset >= trimmed.length) return trimmed
    val second = trimmed.codePointAt(secondOffset)
    if (second !in REGIONAL_INDICATOR_RANGE) return trimmed
    return trimmed.substring(secondOffset + Character.charCount(second))
      .trimStart(' ', '\t', '·', '-', '—', '|')
      .ifBlank { trimmed }
  }

  private fun flagCountryCode(label: String): String? {
    val points = label.codePoints().toArray()
    for (index in 0 until points.lastIndex) {
      val first = points[index]
      val second = points[index + 1]
      if (first in REGIONAL_INDICATOR_RANGE && second in REGIONAL_INDICATOR_RANGE) {
        return buildString(2) {
          append(('A'.code + first - REGIONAL_INDICATOR_START).toChar())
          append(('A'.code + second - REGIONAL_INDICATOR_START).toChar())
        }
      }
    }
    return null
  }

  private fun explicitCountryCode(label: String): String? {
    val known = COUNTRY_ALIASES.keys
    return CODE_TOKEN.findAll(label).map { it.value.uppercase(Locale.ROOT) }
      .firstOrNull(known::contains)
  }

  private fun containsToken(label: String, alias: String): Boolean {
    val normalized = label.lowercase(Locale.ROOT).replace('ё', 'е')
    val escaped = Regex.escape(alias.lowercase(Locale.ROOT).replace('ё', 'е'))
    return Regex("(^|[^a-zа-я0-9])$escaped(?=$|[^a-zа-я0-9])").containsMatchIn(normalized)
  }

  private fun flagFor(code: String): String = buildString {
    code.uppercase(Locale.ROOT).forEach { letter ->
      appendCodePoint(REGIONAL_INDICATOR_START + letter.code - 'A'.code)
    }
  }

  private val COUNTRY_ALIASES = linkedMapOf(
    "DE" to listOf("germany", "deutschland", "германия", "frankfurt", "berlin", "франкфурт", "берлин"),
    "NL" to listOf("netherlands", "holland", "нидерланды", "голландия", "amsterdam", "амстердам"),
    "FI" to listOf("finland", "финляндия", "helsinki", "хельсинки"),
    "RU" to listOf("russia", "россия", "moscow", "москва", "saint petersburg", "санкт-петербург", "spb"),
    "FR" to listOf("france", "франция", "paris", "париж"),
    "GB" to listOf("united kingdom", "great britain", "britain", "великобритания", "london", "лондон", "uk"),
    "US" to listOf("united states", "usa", "сша", "new york", "los angeles", "miami", "chicago"),
    "SE" to listOf("sweden", "швеция", "stockholm", "стокгольм"),
    "NO" to listOf("norway", "норвегия", "oslo", "осло"),
    "CH" to listOf("switzerland", "швейцария", "zurich", "zürich", "цюрих"),
    "AT" to listOf("austria", "австрия", "vienna", "вена"),
    "PL" to listOf("poland", "польша", "warsaw", "варшава"),
    "CZ" to listOf("czechia", "czech republic", "чехия", "prague", "прага"),
    "ES" to listOf("spain", "испания", "madrid", "мадрид"),
    "IT" to listOf("italy", "италия", "milan", "rome", "милан", "рим"),
    "TR" to listOf("turkey", "türkiye", "турция", "istanbul", "стамбул"),
    "CA" to listOf("canada", "канада", "toronto", "vancouver"),
    "JP" to listOf("japan", "япония", "tokyo", "токио"),
    "SG" to listOf("singapore", "сингапур"),
    "HK" to listOf("hong kong", "hongkong", "гонконг"),
    "KR" to listOf("south korea", "korea", "южная корея", "корея", "seoul", "сеул"),
  )
  private val CODE_TOKEN = Regex("(?<![A-Za-z])(?:DE|NL|FI|RU|FR|GB|UK|US|SE|NO|CH|AT|PL|CZ|ES|IT|TR|CA|JP|SG|HK|KR)(?![A-Za-z])")
  private const val REGIONAL_INDICATOR_START = 0x1F1E6
  private val REGIONAL_INDICATOR_RANGE = REGIONAL_INDICATOR_START..0x1F1FF
}

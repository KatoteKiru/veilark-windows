package uk.senyasenyavski.veilark.desktop

import androidx.compose.runtime.staticCompositionLocalOf
import uk.senyasenyavski.veilark.helper.VeilarkPaths
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale

internal enum class UiLanguage(val code: String) {
  Russian("ru"),
  English("en");

  fun text(russian: String, english: String): String =
    if (this == Russian) russian else english
}

internal val LocalUiLanguage = staticCompositionLocalOf { UiLanguage.Russian }

internal object UiLanguageStore {
  private val path get() = VeilarkPaths.dataDirectory.resolve("language.txt")

  fun load(): UiLanguage = runCatching {
    when (Files.readString(path, Charsets.UTF_8).trim().lowercase(Locale.ROOT)) {
      UiLanguage.Russian.code -> UiLanguage.Russian
      UiLanguage.English.code -> UiLanguage.English
      else -> systemLanguage()
    }
  }.getOrElse { systemLanguage() }

  fun save(language: UiLanguage) {
    Files.createDirectories(path.parent)
    val temporary = path.resolveSibling(".${path.fileName}.tmp")
    Files.writeString(temporary, language.code, Charsets.UTF_8)
    runCatching {
      Files.move(
        temporary,
        path,
        StandardCopyOption.ATOMIC_MOVE,
        StandardCopyOption.REPLACE_EXISTING,
      )
    }.getOrElse {
      Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
    }
  }

  private fun systemLanguage(): UiLanguage =
    if (Locale.getDefault().language.equals("ru", ignoreCase = true)) {
      UiLanguage.Russian
    } else {
      UiLanguage.English
    }
}

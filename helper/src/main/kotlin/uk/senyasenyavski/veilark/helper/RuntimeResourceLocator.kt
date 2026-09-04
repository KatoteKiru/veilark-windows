package uk.senyasenyavski.veilark.helper

import java.nio.file.Files
import java.nio.file.Path

/**
 * Resolves immutable native resources from both Compose Desktop development
 * layouts and the layouts produced by jpackage.
 *
 * Compose's resources property is not guaranteed to have the same value for
 * every launcher/upgrade path.  In particular, an installed application can
 * expose either the resources directory itself or the application root.  Keep
 * the search bounded to known Veilark layouts; never search PATH or arbitrary
 * parent trees for privileged VPN executables.
 */
internal object RuntimeResourceLocator {
  fun requireFile(
    fileName: String,
    overridePath: Path? = null,
    environmentName: String? = null,
  ): Path = fileCandidates(
    fileName = fileName,
    overridePath = overridePath,
    environmentValue = environmentName
      ?.let(System::getenv)
      ?.takeIf(String::isNotBlank),
  ).firstOrNull(Files::isRegularFile)
    ?: error("Не найден $fileName. Переустановите Veilark из официального пакета.")

  fun requireDirectory(directoryName: String, overridePath: Path? = null): Path =
    directoryCandidates(directoryName, overridePath)
      .firstOrNull(Files::isDirectory)
      ?: error("Не найдены встроенные данные $directoryName. Переустановите Veilark.")

  internal fun fileCandidates(
    fileName: String,
    overridePath: Path? = null,
    environmentValue: String? = null,
    composeResourcesDirectory: String? =
      System.getProperty("compose.application.resources.dir"),
    javaHome: String? = System.getProperty("java.home"),
    codeSource: Path? = codeSourcePath(),
    workingDirectory: Path = Path.of("").toAbsolutePath(),
  ): List<Path> = buildList {
    overridePath?.let(::add)
    environmentValue?.takeIf(String::isNotBlank)?.let { add(Path.of(it)) }
    resourceRoots(
      composeResourcesDirectory,
      javaHome,
      codeSource,
      workingDirectory,
    ).forEach { add(it.resolve(fileName)) }
  }.map(Path::toAbsolutePath).map(Path::normalize).distinct()

  internal fun directoryCandidates(
    directoryName: String,
    overridePath: Path? = null,
    composeResourcesDirectory: String? =
      System.getProperty("compose.application.resources.dir"),
    javaHome: String? = System.getProperty("java.home"),
    codeSource: Path? = codeSourcePath(),
    workingDirectory: Path = Path.of("").toAbsolutePath(),
  ): List<Path> = buildList {
    overridePath?.let(::add)
    resourceRoots(
      composeResourcesDirectory,
      javaHome,
      codeSource,
      workingDirectory,
    ).forEach { add(it.resolve(directoryName)) }
  }.map(Path::toAbsolutePath).map(Path::normalize).distinct()

  private fun resourceRoots(
    composeResourcesDirectory: String?,
    javaHome: String?,
    codeSource: Path?,
    workingDirectory: Path,
  ): List<Path> = buildList {
    composeResourcesDirectory?.takeIf(String::isNotBlank)?.let { value ->
      val compose = Path.of(value)
      add(compose)
      add(compose.resolve("resources"))
      add(compose.resolve("windows"))
      add(compose.resolve("app").resolve("resources"))
    }
    javaHome?.takeIf(String::isNotBlank)?.let { value ->
      Path.of(value).parent?.let { applicationRoot ->
        add(applicationRoot.resolve("app").resolve("resources"))
        add(applicationRoot.resolve("resources"))
      }
    }
    codeSource?.let { source ->
      val base = if (Files.isDirectory(source)) source else source.parent
      base?.let {
        add(it.resolve("resources"))
        add(it.parent?.resolve("resources") ?: it.resolve("resources"))
      }
    }
    add(workingDirectory.resolve("app").resolve("resources"))
    add(workingDirectory.resolve("resources"))
    add(workingDirectory.resolve("packaging").resolve("resources").resolve("windows"))
  }.map(Path::toAbsolutePath).map(Path::normalize).distinct()

  private fun codeSourcePath(): Path? = runCatching {
    Path.of(RuntimeResourceLocator::class.java.protectionDomain.codeSource.location.toURI())
  }.getOrNull()
}

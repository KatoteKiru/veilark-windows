package uk.senyasenyavski.veilark.helper

import uk.senyasenyavski.veilark.model.VpnStatusCode
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
    packagedRoot = installedApplicationRoot(),
  ).firstOrNull { Files.isRegularFile(it) && trustedInstalledPath(it, installedApplicationRoot()) }
    ?: throw VpnStartException(
      code = VpnStatusCode.CORE_NOT_FOUND,
      message = "Не найден $fileName. Переустановите Veilark из официального пакета.",
      detail = fileName,
    )

  fun requireDirectory(directoryName: String, overridePath: Path? = null): Path =
    directoryCandidates(directoryName, overridePath, packagedRoot = installedApplicationRoot())
      .firstOrNull { Files.isDirectory(it) && trustedInstalledPath(it, installedApplicationRoot()) }
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
    packagedRoot: Path? = null,
  ): List<Path> = buildList {
    if (packagedRoot != null) {
      packagedResourceRoots(packagedRoot).forEach { add(it.resolve(fileName)) }
    } else {
      overridePath?.let(::add)
      environmentValue?.takeIf(String::isNotBlank)?.let { add(Path.of(it)) }
      resourceRoots(
        composeResourcesDirectory,
        javaHome,
        codeSource,
        workingDirectory,
      ).forEach { add(it.resolve(fileName)) }
    }
  }.map(Path::toAbsolutePath).map(Path::normalize).distinct()

  internal fun directoryCandidates(
    directoryName: String,
    overridePath: Path? = null,
    composeResourcesDirectory: String? =
      System.getProperty("compose.application.resources.dir"),
    javaHome: String? = System.getProperty("java.home"),
    codeSource: Path? = codeSourcePath(),
    workingDirectory: Path = Path.of("").toAbsolutePath(),
    packagedRoot: Path? = null,
  ): List<Path> = buildList {
    if (packagedRoot != null) {
      packagedResourceRoots(packagedRoot).forEach { add(it.resolve(directoryName)) }
    } else {
      overridePath?.let(::add)
      resourceRoots(
        composeResourcesDirectory,
        javaHome,
        codeSource,
        workingDirectory,
      ).forEach { add(it.resolve(directoryName)) }
    }
  }.map(Path::toAbsolutePath).map(Path::normalize).distinct()

  private fun packagedResourceRoots(root: Path): List<Path> = listOf(
    root.resolve("app").resolve("resources"),
    root.resolve("app").resolve("resources").resolve("windows"),
    root.resolve("resources"),
    root.resolve("resources").resolve("windows"),
  )

  private fun installedApplicationRoot(): Path? = runCatching {
    if (!System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) return null
    val executable = ProcessHandle.current().info().command().orElse(null) ?: return null
    val path = Path.of(executable)
    if (!path.fileName.toString().equals("Veilark.exe", ignoreCase = true)) return null
    path.toRealPath().parent
  }.getOrNull()

  private fun trustedInstalledPath(path: Path, root: Path?): Boolean =
    root == null || runCatching { path.toRealPath().startsWith(root.toRealPath()) }.getOrDefault(false)

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

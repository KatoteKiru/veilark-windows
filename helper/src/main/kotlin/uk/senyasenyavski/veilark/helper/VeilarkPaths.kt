package uk.senyasenyavski.veilark.helper

import java.nio.file.Files
import java.nio.file.Path

object VeilarkPaths {
  val dataDirectory: Path by lazy {
    val localAppData = System.getenv("LOCALAPPDATA")
      ?.takeIf(String::isNotBlank)
      ?: System.getProperty("user.home")
    Path.of(localAppData, "Veilark").also(Files::createDirectories)
  }
  val runtimeDirectory: Path by lazy {
    dataDirectory.resolve("runtime").also(Files::createDirectories)
  }
  val logDirectory: Path by lazy {
    dataDirectory.resolve("logs").also(Files::createDirectories)
  }
  val geoDirectory: Path by lazy {
    dataDirectory.resolve("geo").also(Files::createDirectories)
  }
  val activeConfig: Path get() = runtimeDirectory.resolve("active.json")
  val profileStore: Path get() = dataDirectory.resolve("profiles.dat")
  val logFile: Path get() = logDirectory.resolve("veilark.log")
}

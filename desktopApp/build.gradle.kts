import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.provider.Property
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
  kotlin("jvm")
  id("org.jetbrains.compose")
  id("org.jetbrains.kotlin.plugin.compose")
}

abstract class GenerateEmbeddedTrustProfiles : DefaultTask() {
  @get:InputFiles
  abstract val sourceFiles: ConfigurableFileCollection

  @get:OutputDirectory
  abstract val outputDirectory: DirectoryProperty

  @get:Input
  abstract val allowFixtures: Property<Boolean>

  @TaskAction
  fun generate() {
    val links = if (allowFixtures.get()) {
      listOf("tt://ci-fixture-one.invalid", "tt://ci-fixture-two.invalid")
    } else {
      val clientLink = Regex(""""client_link"\s*:\s*"([^"\\]+)"""")
      sourceFiles.files.sortedBy { it.name }.map { source ->
        require(source.isFile) { "Missing embedded TrustTunnel source: $source" }
        clientLink.find(source.readText(Charsets.UTF_8))
          ?.groupValues
          ?.get(1)
          ?.takeIf { it.startsWith("tt://") }
          ?: error("Invalid embedded TrustTunnel source: $source")
      }.distinct()
    }
    require(links.isNotEmpty()) { "No embedded TrustTunnel profiles were generated" }
    val output = outputDirectory.file("builtin_trust_profiles.txt").get().asFile
    output.parentFile.mkdirs()
    output.writeText(links.joinToString(separator = "\n", postfix = "\n"), Charsets.UTF_8)
  }
}

val embeddedTrustSources = listOf(
  rootProject.layout.projectDirectory.file("../secrets/generated/new-nl-trusttunnel.json"),
  rootProject.layout.projectDirectory.file("../secrets/generated/frankfurt-trusttunnel.json"),
)
val generatedTrustResources = layout.buildDirectory.dir("generated/veilark/trust-resources")
val allowFixtureTrustProfiles = providers.gradleProperty("veilarkAllowFixtureTrustProfiles")
  .map(String::toBoolean)
  .orElse(false)
val generateEmbeddedTrustProfiles = tasks.register<GenerateEmbeddedTrustProfiles>(
  "generateEmbeddedTrustProfiles",
) {
  if (!allowFixtureTrustProfiles.get()) sourceFiles.from(embeddedTrustSources)
  outputDirectory.set(generatedTrustResources)
  allowFixtures.set(allowFixtureTrustProfiles)
}

tasks.configureEach {
  if (name.startsWith("packageRelease") || name.startsWith("createReleaseDistributable")) {
    doFirst {
      require(!allowFixtureTrustProfiles.get()) {
        "Production packages cannot contain fixture TrustTunnel profiles"
      }
    }
  }
}

sourceSets.main {
  resources.srcDir(generatedTrustResources)
}

tasks.named("processResources") {
  dependsOn(generateEmbeddedTrustProfiles)
}

// Allows a release candidate to be assembled while an installed/elevated
// instance is still running from the regular Compose output directory.
if (providers.gradleProperty("veilarkIsolatedOutput").isPresent) {
  layout.buildDirectory.set(layout.projectDirectory.dir("build-isolated"))
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  implementation(project(":shared"))
  implementation(project(":helper"))
  implementation(compose.desktop.currentOs)
  implementation(compose.material3)
  implementation(compose.materialIconsExtended)
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
}

compose.desktop {
  application {
    mainClass = "uk.senyasenyavski.veilark.desktop.MainKt"
    jvmArgs += listOf("-Dfile.encoding=UTF-8")

    // ProGuard 7.7 strips JNA native resources/classes from the jpackage
    // runtime and makes the installed application fail in com.sun.jna.Native.
    // Stability is more important than a smaller VPN installer.
    buildTypes.release.proguard.isEnabled.set(false)

    nativeDistributions {
      targetFormats(TargetFormat.Msi, TargetFormat.Exe)
      packageName = "Veilark"
      packageVersion = "0.3.7"
      description = "Veilark VPN for Windows"
      vendor = "Veilark"
      modules("java.net.http", "java.logging", "java.naming", "java.security.jgss")
      appResourcesRootDir.set(rootProject.layout.projectDirectory.dir("packaging/resources"))
      windows {
        iconFile.set(project.file("src/main/resources/veilark.ico"))
        menuGroup = "Veilark"
        dirChooser = true
        perUserInstall = false
        upgradeUuid = "47a6cdd8-9630-4fa5-a2fd-c29c5774dc1a"
      }
    }
  }
}

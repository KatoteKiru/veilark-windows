import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
  kotlin("jvm")
  id("org.jetbrains.compose")
  id("org.jetbrains.kotlin.plugin.compose")
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
  testImplementation(kotlin("test"))
}

tasks.test {
  useJUnitPlatform()
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
      packageVersion = "0.3.13"
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

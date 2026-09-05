plugins {
  kotlin("jvm")
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  implementation(project(":shared"))
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
  implementation("net.java.dev.jna:jna-platform:5.18.1")
  implementation("org.json:json:20250517")
  testImplementation(kotlin("test"))
}

tasks.test {
  useJUnitPlatform()
  testLogging {
    events("failed", "skipped")
    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    showExceptions = true
    showCauses = true
    showStackTraces = true
  }
}

/**
 * Writes the runtime class path so that `scripts/live-tunnel-check.ps1` can run
 * the headless tunnel check under an elevated JVM without starting Gradle as
 * administrator.
 */
val exportRuntimeClasspath by tasks.registering {
  val runtimeClasspath = sourceSets["main"].runtimeClasspath
  val destination = layout.buildDirectory.file("runtime-classpath.txt")
  inputs.files(runtimeClasspath)
  outputs.file(destination)
  doLast {
    destination.get().asFile.writeText(runtimeClasspath.asPath)
  }
}

plugins {
  kotlin("multiplatform")
}

kotlin {
  jvm()
  jvmToolchain(17)

  sourceSets {
    commonMain.dependencies {
      implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    }
    commonTest.dependencies {
      implementation(kotlin("test"))
    }
    jvmMain.dependencies {
      implementation("org.json:json:20250517")
      implementation("org.yaml:snakeyaml:2.6")
    }
  }
}

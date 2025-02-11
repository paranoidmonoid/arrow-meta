plugins {
  alias(libs.plugins.arrowGradleConfig.multiplatform)
  alias(libs.plugins.arrowGradleConfig.publishMultiplatform)
}

kotlin {
  explicitApiWarning()

  sourceSets {
    commonMain {
      dependencies {
        implementation(libs.kotlin.stdlibCommon)
      }
    }

    named("jvmMain") {
      dependencies {
        implementation(libs.kotlin.stdlibJDK8)
      }
    }

    named("jsMain") {
      dependencies {
        implementation(libs.kotlin.stdlibJS)
      }
    }
  }
}

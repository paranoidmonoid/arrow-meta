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
        api(projects.arrowAnalysisTypes)
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

dependencies {
  kotlinCompilerClasspath(projects.arrowAnalysisKotlinPlugin)
}

tasks.compileKotlinJvm {
  kotlinOptions {
    dependsOn(":arrow-analysis-kotlin-plugin:jar")
    freeCompilerArgs = listOf(
      "-Xplugin=$rootDir/plugins/analysis/kotlin-plugin/build/libs/arrow-analysis-kotlin-plugin-1.5.31-SNAPSHOT.jar",
      "-P", "plugin:arrow.meta.plugin.compiler:generatedSrcOutputDir=$buildDir/generated/meta"
    )
  }
}

tasks.compileKotlinJs {
  kotlinOptions.suppressWarnings = true
}

tasks.compileKotlinMetadata {
  kotlinOptions.suppressWarnings = true
}

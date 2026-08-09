import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("com.android.application")
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.components.resources)
            implementation(compose.ui)
        }
        androidMain.dependencies {
            implementation("androidx.activity:activity-compose:1.11.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "com.example.countdown"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.countdown"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Development and production Webpack use the same intermediate Kotlin package.
// Always refresh that package from the matching executable before bundling;
// otherwise an up-to-date compile-sync task can leave the other mode's import
// object behind and Chrome cannot instantiate the Wasm module.
tasks.named("wasmJsDevelopmentExecutableCompileSync") {
    outputs.upToDateWhen { false }
}
tasks.named("wasmJsProductionExecutableCompileSync") {
    outputs.upToDateWhen { false }
}

// When both variants are requested together, let production finish consuming
// its staging files before development replaces them.
tasks.named("wasmJsDevelopmentExecutableCompileSync") {
    mustRunAfter(tasks.named("wasmJsBrowserProductionWebpack"))
}

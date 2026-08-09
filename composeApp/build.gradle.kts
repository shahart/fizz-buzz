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

// Development and production compile-sync tasks share an intermediate Kotlin
// directory. Keep production Webpack from replacing the import object before
// development Webpack has consumed its matching Wasm output.
tasks.named("wasmJsDevelopmentExecutableCompileSync") {
    mustRunAfter(tasks.named("wasmJsBrowserProductionWebpack"))
}

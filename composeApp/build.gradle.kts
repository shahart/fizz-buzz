import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("com.android.application")
}

// Firebase's generated Android resources require the app-specific config file.
// Keeping the plugin conditional lets contributors build the app without access
// to the Firebase project; Analytics starts automatically when the file exists.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
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
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
            implementation("io.ktor:ktor-client-core:3.5.2")
            implementation("io.ktor:ktor-client-websockets:3.5.2")
        }
        androidMain.dependencies {
            implementation("androidx.activity:activity-compose:1.13.0")
            implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
            implementation("com.google.firebase:firebase-analytics:23.2.0")
            implementation("io.ktor:ktor-client-okhttp:3.5.2")
        }
        wasmJsMain.dependencies {
            implementation("io.ktor:ktor-client-js:3.5.2")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "com.shahartal.fizzbuzz"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.shahartal.fizzbuzz"
        minSdk = 23
        targetSdk = 36
        versionCode = 3
        versionName = "1.02"
        val workerUrl = providers.gradleProperty("gameWorkerUrl")
            .orElse("wss://global-seven-boom.lat-shahar.workers.dev/game")
            .get()
        buildConfigField("String", "GAME_WORKER_URL", "\"$workerUrl\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
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

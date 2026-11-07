import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.HostTestBuilder

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.android.application)
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
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.resources)
            implementation(libs.compose.ui)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.websockets)
        }
        androidMain.dependencies {
            implementation(libs.activity.compose)
            // Firebase Analytics brings Fragment 1.1.0 transitively; pin a
            // maintained version so it isn't packaged in the Android release.
            implementation(libs.fragment)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.firebase.analytics)
            implementation(libs.ktor.wear.client.okhttp)
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidInstrumentedTest.dependencies {
            implementation(libs.compose.ui.test.junit4)
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.test.junit)
            implementation(libs.androidx.espresso.core)
        }
    }
}

dependencies {
    debugImplementation(libs.compose.ui.test.manifest)
}

extensions.configure<ApplicationExtension> {
    namespace = "com.shahartal.fizzbuzz"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.shahartal.fizzbuzz"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        minSdk = 24
        targetSdk = 36
        versionCode = 11
        versionName = "1.13"
        val workerUrl = providers.gradleProperty("gameWorkerUrl")
            .orElse("wss://global-seven-boom.lat-shahar.workers.dev/game")
            .get()
        buildConfigField("String", "GAME_WORKER_URL", "\"$workerUrl\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            // R8 removes unused code/resources and obfuscates the release artifact.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

extensions.configure<ApplicationAndroidComponentsExtension> {
    beforeVariants(selector().all()) { variant ->
        if (variant.buildType == "release") {
            variant.hostTests[HostTestBuilder.UNIT_TEST_TYPE]?.enable = true
        }
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

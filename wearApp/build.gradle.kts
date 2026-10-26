plugins {
    alias(libs.plugins.kotlin.multiplatform)
    // The shared commonMain sources carry @Serializable models, so the
    // serialization compiler plugin must run for this module as well.
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.android.application)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir("../composeApp/src/commonMain/kotlin")
            dependencies {
                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.compose.material3)
                implementation(libs.compose.resources)
                implementation(libs.compose.ui)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.ktor.wear.client.core)
                implementation(libs.ktor.wear.client.websockets)
            }
        }
        androidMain {
            kotlin.srcDir("../composeApp/src/androidMain/kotlin")
            dependencies {
                implementation(libs.activity.compose)
                implementation(libs.lifecycle.runtime.compose)
                implementation(libs.ktor.wear.client.okhttp)
            }
        }
    }
}

compose.resources {
    packageOfResClass = "fizz_buzz.composeapp.generated.resources"
    customDirectory(
        "commonMain",
        providers.provider { layout.projectDirectory.dir("../composeApp/src/commonMain/composeResources") },
    )
}

android {
    namespace = "com.shahartal.fizzbuzz"
    compileSdk = 37

    defaultConfig {
        // Distinct from the phone app so both can be installed side by side.
        applicationId = "com.shahartal.fizzbuzz.wear"
        minSdk = 26
        targetSdk = 36
        versionCode = 10_001
        versionName = "1.0-wear"
        val workerUrl = providers.gradleProperty("gameWorkerUrl")
            .orElse("wss://global-seven-boom.lat-shahar.workers.dev/game")
            .get()
        buildConfigField("String", "GAME_WORKER_URL", "\"$workerUrl\"")
    }

    sourceSets["main"].res.srcDir("../composeApp/src/androidMain/res")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            // Keep the wearable release consistent with the phone app.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

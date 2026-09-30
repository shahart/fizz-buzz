plugins {
    kotlin("multiplatform")
    // The shared commonMain sources carry @Serializable models, so the
    // serialization compiler plugin must run for this module as well.
    kotlin("plugin.serialization")
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

    sourceSets {
        commonMain {
            kotlin.srcDir("../composeApp/src/commonMain/kotlin")
            dependencies {
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
        }
        androidMain {
            kotlin.srcDir("../composeApp/src/androidMain/kotlin")
            dependencies {
                implementation("androidx.activity:activity-compose:1.13.0")
                implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
                implementation("io.ktor:ktor-client-okhttp:3.5.2")
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

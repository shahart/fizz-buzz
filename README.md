# Countdown Number

A small Compose Multiplatform app for Android and web. It displays a random number from 1 through 100, counts down from 7, and shows `TIMED-OUT` with a tone at zero.

## Run

Use JDK 17 through 24.

```bash
# Web development server
./gradlew :composeApp:wasmJsBrowserDevelopmentRun

# Android debug APK
./gradlew :composeApp:assembleDebug

# Tests and production web bundle
./gradlew :composeApp:allTests :composeApp:wasmJsBrowserDistribution
```

The production website is generated under `composeApp/build/dist/wasmJs/productionExecutable`.

Browser audio policies may require one user interaction before a page is allowed to play sound. If the first automatic tone is blocked, press **New number** or **Play again** to start another round after interacting with the page.

# fizz-buzz 7-boom

A small Compose Multiplatform voice and touch game for Android and web. Say or tap the displayed number within seven seconds, or say `BOOM`/tap 💣 when it is divisible by 7 or contains the digit 7. Correct answers advance the number and restart the timer; a mistake or timeout flashes `BOOM` and plays a tone.

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

The app requests microphone access when you press **Start game**. Browser support requires the Web Speech API (Chrome or another compatible browser) and microphone permission. The on-screen answer buttons work without microphone support or permission.

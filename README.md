# fizz-buzz 7-boom

A global Compose Multiplatform voice and touch game for Android, Wear OS, and web. One Cloudflare Durable Object owns the shared counter, player rotation, and seven-second deadline. Say or tap the displayed number, or say `BOOM`/tap 💣 when it is divisible by 7 or contains the digit 7.

## Run

Use JDK 17 through 24.

```bash
# Terminal 1: Worker, Durable Object, and production Wasm assets
cd worker
npm install
npm run dev

# Terminal 2: Android debug APK (connects to the deployed Worker by default)
./gradlew :composeApp:assembleDebug

# Standalone Wear OS debug APK
./gradlew :wearApp:assembleDebug

# Kotlin tests and production web bundle
./gradlew :composeApp:allTests :composeApp:wasmJsBrowserDistribution

# Worker type-check and Durable Object/WebSocket tests
cd worker && npm run check

# Update https://global-seven-boom.lat-shahar.workers.dev/composeResources
cd worker && npx wrangler deploy
```

Open `http://localhost:8787` in two browser windows to exercise the global rotation. The production website is generated under `composeApp/build/dist/wasmJs/productionExecutable` and is served by the Worker on the same origin as `/game`.

`./gradlew :composeApp:wasmJsBrowserDevelopmentRun` serves the UI from Webpack and connects its WebSocket directly to the deployed Worker. When the UI is served by the local Worker on port `8787`, it uses that same local origin instead.

Android builds use `wss://global-seven-boom.lat-shahar.workers.dev/game` by default. To use the local Worker from an Android emulator instead, override the endpoint when building:

```bash
./gradlew :composeApp:assembleDebug -PgameWorkerUrl=ws://10.0.2.2:8787/game
```

Use the same property with `:wearApp:assembleDebug` for a Wear OS emulator. The watch app is packaged as a separate, standalone Wear OS APK with its own version-code range while retaining the phone app's package name for a shared Play Store listing.

## Firebase Analytics (Android)

The Android app includes Google Analytics for Firebase. To connect a build to the Firebase project:

1. Register the Android app `com.shahartal.fizzbuzz` in Firebase with Google Analytics enabled.
2. Download its `google-services.json` and save it as `composeApp/google-services.json`.
3. Rebuild and install the app. Firebase then records automatic events such as `first_open`, `session_start`, and `user_engagement`; no initialization code is required.

Use Analytics **DebugView** while verifying a debug build. In Google Analytics, demographic reporting is under **Reports > User attributes > Demographic details**. Age, gender, and interests are aggregated only when eligible device advertising identifiers/signals and the required user consent are available, and Google can withhold low-volume results using privacy thresholds. Configure consent and the Google Analytics data controls for every region where the app is offered before distributing the analytics-enabled build.

### Firebase Analytics (web)

The browser target uses the modular Firebase JavaScript SDK and is registered as the `fizzbuzz-web` app in the same Firebase project. Its public client configuration is stored in `composeApp/src/wasmJsMain/resources/firebase-config.js`. The Android `google-services.json` cannot configure the web data stream because it does not contain the web app ID or Analytics measurement ID.

When `apiKey`, `appId`, and `measurementId` are present, Analytics starts automatically in supported browsers and records events such as `first_visit`, `page_view`, `session_start`, and `user_engagement`. With an incomplete config, Analytics stays disabled and the game continues normally. For web age, gender, and interest reporting, enable Google signals and implement the consent behavior required for the regions where the site is available.

Deploy from `worker/` with `npm run deploy`. No account, credentials, room codes, or free-text player names are used. Each player chooses a nickname from the built-in emoji list; it is remembered in Android SharedPreferences or browser local storage and sent to the game Worker. At game over, the emoji belonging to the fastest average response-time rank is shown as the winner (including every rank-one emoji in a tie).

The app opens its WebSocket and requests microphone access only after **Join game**. Browser speech input requires the Web Speech API and microphone permission; the answer buttons work without speech support. Hiding the page or backgrounding Android leaves the roster and requires **Rejoin game** on return.

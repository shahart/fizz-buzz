# fizz-buzz 7-boom

A global Compose Multiplatform voice and touch game for Android and web. One Cloudflare Durable Object owns the shared counter, player rotation, and seven-second deadline. Say or tap the displayed number, or say `BOOM`/tap 💣 when it is divisible by 7 or contains the digit 7.

## Run

Use JDK 17 through 24.

```bash
# Terminal 1: Worker, Durable Object, and production Wasm assets
cd worker
npm install
npm run dev

# Terminal 2: Android debug APK (connects to the deployed Worker by default)
./gradlew :composeApp:assembleDebug

# Kotlin tests and production web bundle
./gradlew :composeApp:allTests :composeApp:wasmJsBrowserDistribution

# Worker type-check and Durable Object/WebSocket tests
cd worker && npm run check
```

Open `http://localhost:8787` in two browser windows to exercise the global rotation. The production website is generated under `composeApp/build/dist/wasmJs/productionExecutable` and is served by the Worker on the same origin as `/game`.

`./gradlew :composeApp:wasmJsBrowserDevelopmentRun` serves the UI from Webpack and connects its WebSocket directly to the deployed Worker. When the UI is served by the local Worker on port `8787`, it uses that same local origin instead.

Android builds use `wss://global-seven-boom.lat-shahar.workers.dev/game` by default. To use the local Worker from an Android emulator instead, override the endpoint when building:

```bash
./gradlew :composeApp:assembleDebug -PgameWorkerUrl=ws://10.0.2.2:8787/game
```

Deploy from `worker/` with `npm run deploy`. No database, account, credentials, room codes, or player names are used.

The app opens its WebSocket and requests microphone access only after **Join game**. Browser speech input requires the Web Speech API and microphone permission; the answer buttons work without speech support. Hiding the page or backgrounding Android leaves the roster and requires **Rejoin game** on return.

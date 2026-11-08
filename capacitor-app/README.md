# 7-Boom Capacitor apps

This is a separate Capacitor wrapper for the web client in `../docs`. It owns its
Android and iOS projects; the existing Compose app is unchanged. It uses native
speech recognition on Android and iOS, with microphone and speech permissions
requested when the first live turn begins.

## Setup and build

From this directory:

```sh
npm install
npm run build:web
npm run sync
npm run build:android
npm run open:ios
```

Open the generated `ios/App/App.xcodeproj` in Xcode to select a signing team and
build for an iOS simulator or device. Android builds need the Android SDK and
JDK 21. iOS builds need Xcode and CocoaPods.

The web bundle is generated from `../docs` before every sync:

```sh
npm --prefix ../docs run build
npm run sync
```

`npm run sync` builds a bundled copy into the generated `www/` folder, including
the native speech recognition bridge, then copies it into both native projects.

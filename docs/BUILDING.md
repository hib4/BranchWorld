# Building and running BranchWorld

[← Back to README](../README.md)

* [Requirements](#requirements)
* [Desktop](#desktop)
* [Android](#android)
* [iOS](#ios)
* [Web](#web)
* [Tests](#tests)
* [Local release builds](#local-release-builds)

## Requirements

* **JDK 21.** Use the checked-in Gradle wrapper; its daemon toolchain selects Azul JDK 21.
* **Android SDK.** Install the Android 17 / API 37 SDK platform and Platform-Tools through Android Studio, and point `ANDROID_HOME` or the local `sdk.dir` property at the SDK. The app supports API 26 and later.
* **Xcode with the Metal Toolchain** (iOS only). The RealityKit shaders need it: `xcodebuild -downloadComponent MetalToolchain`. The app targets iOS 18.2.
* **A current Chrome, Edge, Firefox or Safari** (web only). The browser build draws with WebGL 2.
* **Git LFS.** The `.glb` models are stored in Git LFS. Run `git lfs pull` after cloning, or the models are 130-byte pointer files and the island shows "Island assets could not load".

## Desktop

Build the Desktop app, or run it in a graphical session:

```sh
./gradlew :desktopApp:assemble
./gradlew :desktopApp:run
```

For a macOS app bundle, run `./gradlew :desktopApp:createDistributable` and open `desktopApp/build/compose/binaries/main/app/id.branchworld.game.app`.

## Android

Build the debug APK and run lint:

```sh
./gradlew :androidApp:assembleDebug :androidApp:lintDebug
```

With one emulator or device connected, install and launch the debug APK. Add `-s <serial>` after `adb` when more than one device is connected.

```sh
adb devices -l
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
adb shell am start -W -n id.branchworld.game/.MainActivity
```

## iOS

Open `iosApp/iosApp.xcodeproj` in Xcode and run the `iosApp` scheme, or build from the command line:

```sh
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 18 Pro' build
```

Xcode's build runs `./gradlew :iosKit:embedAndSignAppleFrameworkForXcode` for the Kotlin framework and copies `renderer/src/commonMain/resources/diorama` into the app.

## Web

The browser build is Kotlin/JS (Kool publishes no Wasm target) and draws with WebGL 2.

```sh
./gradlew :webApp:jsBrowserDevelopmentRun        # opens a development server with live reload
./gradlew :webApp:jsBrowserDistribution          # static site in webApp/build/dist/js/productionExecutable
```

The distribution uses relative paths, so any static host serves it from any folder. Worlds save in the browser's IndexedDB; photos go to the browser's downloads.

Every push to `main` runs `.github/workflows/web.yml`. It checks out the Git LFS models, checks the shared rules under Kotlin/JS, refuses to publish if any model is still an LFS pointer, and publishes the bundle to GitHub Pages. In the repository settings, set **Pages → Source** to **GitHub Actions** once.

## Tests

| Target | Command |
| --- | --- |
| JVM and Android host | `./gradlew :simulation:jvmTest :simulation:testAndroidHostTest :shared:jvmTest :shared:testAndroidHostTest :renderer:jvmTest :renderer:testAndroidHostTest` |
| Kotlin/JS (Node.js) | `./gradlew :simulation:jsNodeTest :shared:jsNodeTest :renderer:jsNodeTest` |
| iOS simulator | `./gradlew :renderer:iosSimulatorArm64Test :iosKit:iosSimulatorArm64Test` |
| Desktop UI | `./gradlew :desktopApp:test` |
| Replay fixtures on a device | `./gradlew :simulation:connectedAndroidDeviceTest` |
| Phone walkthrough on a device | `./gradlew :androidApp:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=id.branchworld.game.LivingSandboxDeviceTest` |

The replay hashes of every rule set are pinned in the tests, so a change that alters any history fails on every target.

## Local release builds

```sh
./gradlew :androidApp:assembleRelease :androidApp:lintRelease :desktopApp:packageDmg
```

### macOS

On Apple Silicon, mount `desktopApp/build/compose/binaries/main/dmg/id.branchworld.game-1.0.0.dmg`, copy the app out of the image, and open it. The DMG is built for local review and is not notarized.

### Android

The Android release task produces an unsigned APK. Use Android Build Tools 36.1.0 and a local signing keystore outside the repository. A fresh emulator or a device with the same signing certificate can install the result.

```sh
keytool -genkeypair -keystore "$BRANCHWORLD_LOCAL_KEYSTORE" -storetype PKCS12 \
  -alias branchworld-local -keyalg RSA -keysize 3072 -validity 3650
tools="$ANDROID_HOME/build-tools/36.1.0"
mkdir -p build/release
"$tools/zipalign" -P 16 -f 4 androidApp/build/outputs/apk/release/androidApp-release-unsigned.apk build/release/aligned.apk
"$tools/apksigner" sign --ks "$BRANCHWORLD_LOCAL_KEYSTORE" --ks-key-alias branchworld-local \
  --out build/release/BranchWorld-android-local.apk build/release/aligned.apk
"$tools/apksigner" verify --verbose build/release/BranchWorld-android-local.apk
adb install build/release/BranchWorld-android-local.apk
```

# BranchWorld

BranchWorld is a Kotlin Multiplatform game about changing a living world and comparing the futures that follow. The current prototype opens a 3D island diorama with a shared Compose HUD.

## Build and run

Use the checked-in Gradle wrapper. Its daemon toolchain selects Azul JDK 21. For Android, install the Android 17 / API 37 SDK platform and Platform-Tools through Android Studio. Point `ANDROID_HOME` or the local `sdk.dir` property at the SDK. The app supports API 26 and later.

```sh
./gradlew :androidApp:assembleDebug :androidApp:lintDebug :desktopApp:assemble
./gradlew :shared:jvmTest :shared:testAndroidHostTest :renderer:jvmTest :renderer:testAndroidHostTest
```

With one emulator or device connected, install and launch the debug APK. Add `-s <serial>` after `adb` when more than one device is connected.

```sh
adb devices -l
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
adb shell am start -W -n id.branchworld.game/.MainActivity
```

Run the Desktop app in a graphical session.

```sh
./gradlew :desktopApp:run
```

For a macOS app bundle, run `./gradlew :desktopApp:createDistributable` and open `desktopApp/build/compose/binaries/main/app/id.branchworld.game.app`.

Select **Open world**. The island starts paused at tick 0. Select **Send bunny left** or **Send bunny right** to queue a destination for the next tick, then select **Play**. Switch between **1×**, **2×**, and **4×** while playing, or select **Pause** to hold the tick and pose. **Focus island** resets the camera. **Show diagnostics** displays the seed `104729`, bunny cell, platform, and lifecycle state.

| Action | Android | Desktop |
| --- | --- | --- |
| Orbit | Drag one finger | Drag the primary mouse button or use the arrow keys |
| Pan | Drag two fingers together | Drag the secondary mouse button, Shift-drag, or use Shift and the arrow keys |
| Zoom | Pinch | Scroll |

To check lifecycle behavior, background and return to the Android app, then rotate it. On Desktop, resize, minimize, and restore the window. Use **Back to home** and reopen the world to return to the same in-memory session. Time spent away from the viewport does not add ticks.

Run the common replay fixture and the Android gesture, world clock, and Activity recreation test on a connected device with `./gradlew :shared:connectedAndroidDeviceTest :androidApp:connectedDebugAndroidTest`.

## Architecture

- `androidApp` and `desktopApp` own platform startup, system input bridges, and window or Activity lifetime. The existing iOS scaffold consumes the shared module and is outside the launch gate.
- `shared/src/commonMain` owns the Compose shell, navigation state, and viewport slot. The shell uses Compose saved instance state for Android recreation. World save files will use a separate, versioned format.
- `renderer` owns the Kool prototype, authored scene and animation, camera controls, and platform surface adapters. Android hosts GLES under Compose. Desktop renders a Vulkan color attachment and delivers its pixels to Compose. The three runtime GLBs and the bunny's texture are bundled from one shared resource directory.
- The shared simulation owns authoritative state, accepts tick-indexed commands, and supplies read-only render projections. The Compose clock controls when to advance. Replay uses the seed, rules version, and ordered inputs. Durable save files follow in Phase 06.

## Source asset inventory

The eight downloaded packs live under `assets/`. The formats and animation entries below were inspected in the local files. The intended uses are candidates for later integration.

| Pack | Inspected runtime candidates and alternatives | Animation and intended use |
| --- | --- | --- |
| KayKit Forest (`environment/kaykit-forest`) | 105 glTF models with BIN and PNG dependencies. FBX and OBJ/MTL variants. | Static trees, rocks, and vegetation for the diorama and island. |
| Kenney Nature (`environment/kenney-nature`) | 329 GLB models. FBX, OBJ/MTL, DAE, STL, and PNG previews. | Static rocks, ground props, and vegetation for the diorama and terrain. |
| Kenney Cube Pets (`animals/kenney-cube-pets`) | 24 GLB models plus FBX and OBJ/MTL. | Each GLB has node animation clips such as idle, walk, run, and eat. Candidate for the first animal integration and wildlife. |
| Vuena Pets (`animals/vuena-pets`) | Three GLB and FBX models with PNG textures. | Skinned cat, fox, and owl with embedded clips including idle, walk, eat, and sleep. Candidates for additional wildlife. |
| KayKit Medieval (`buildings/kaykit-medieval`) | 221 glTF/BIN models plus FBX, OBJ/MTL, and PNG textures. | Static buildings and props for settlements. |
| KayKit Adventurers (`characters/kaykit-adventurers`) | Six skinned character GLBs, two animation GLBs, and glTF/BIN, FBX, OBJ/MTL, and PNG assets. | Separate Rig Medium general and locomotion clips. Candidates for future inhabitants. |
| KayKit Character Animations (`characters/kaykit-character-animations`) | 14 GLB and 14 FBX animation files plus two mannequin variants in each format. | Separate Rig Medium and Rig Large motion clips. Candidate motion library for compatible characters. |
| Kenney Interface Sounds (`audio/kenney-interface-sounds`) | 100 OGG audio files. | Interface feedback for future audio integration. |

Keep the original packs in `assets/`. Import only the models, textures, and clips verified for runtime use on Android and Desktop.

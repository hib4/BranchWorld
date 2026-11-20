# BranchWorld

BranchWorld is a Kotlin Multiplatform game about changing a living world and comparing the futures that follow. The Android and Desktop builds open generated 3D islands with living populations of rabbits and deer.

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

Choose island seed `104729`, `271828`, or `424242`, then select **Open world**. A new island starts paused at tick 0 with four rabbits and three deer. Select **Play** and watch the forage total as they eat and the patches regrow. The blue markers show freshwater springs. **Send bunny left** and **Send bunny right** queue a waypoint for the lead rabbit, which tends to urgent needs first. Use **1×**, **2×**, or **4×** to change playback speed and **Pause** to hold the tick. **Focus island** resets the camera.

| Action | Android | Desktop |
| --- | --- | --- |
| Orbit | Drag one finger | Drag the primary mouse button or use the arrow keys |
| Pan | Drag two fingers together | Drag the secondary mouse button, Shift-drag, or use Shift and the arrow keys |
| Zoom | Pinch | Scroll |

To check lifecycle behavior, background and return to the Android app, then rotate it. On Desktop, resize, minimize, and restore the window. Use **Back to home** and reopen the same seed to retain the current tick. Opening a different seed replaces the in-memory session and starts at tick 0.

For an intervention demo, open seed `104729`, play until shortly after rain begins at tick 300, and pause. Open **Inspect world**, choose region **B3**, and queue **Reforest region** and **Supplement forage**. Start playback to see saplings appear and the region's forage rise. Use the creature picker to check an animal's needs and current action.

**Save world** captures the current tick and any queued interventions in one local slot. After closing and reopening BranchWorld, choose **Load saved world** from Home. The island opens paused at the saved tick. Press **Play** to resume and apply any queued interventions.

Open seed `104729` and use **Timeline** → **Play to tick** to reach `300`. Select **Fork here** and name the branch **Restored Forest**. In **Inspect world**, choose **B3** and select **Reforest region**. Play the child to `900`, select **Main** in the timeline tree, and play it to the same tick.

Choose **Compare branches** to see both histories at tick `900`. **Show left** and **Show right** switch the island at the same camera position. The table shows the difference from the left branch. Select the **Reforest B3 at tick 301** event to see how many saplings the command planted. Close comparison, choose **Save world**, then quit and relaunch. Use **Load saved world** and reopen comparison. An existing single-island save opens as **Main** starting at its saved tick.

Run the common replay fixture and the Android gesture, world clock, and Activity recreation test on a connected device with `./gradlew :shared:connectedAndroidDeviceTest :androidApp:connectedDebugAndroidTest`.

## Architecture

- `androidApp` and `desktopApp` own platform startup, system input bridges, and window or Activity lifetime. The existing iOS scaffold consumes the shared module and is outside the launch gate.
- `shared/src/commonMain` owns the Compose shell, navigation state, and viewport slot. Compose restores the shell across Android recreation. A separate versioned checkpoint restores the world, with platform adapters for atomic local saves.
- `renderer` owns the Kool scene, creature model pool, camera, and platform adapters. Android hosts GLES under Compose. Desktop delivers a Vulkan color attachment to Compose. The bunny and deer GLBs use the bundled Kenney texture.
- Shared Kotlin generates terrain, climate, props, and traversability from the seed and worldgen version. Kool builds chunk meshes and instances the bundled pines and rocks from a static terrain projection.
- Rules v4 advances weather, interventions, forage, creature needs, movement, and population turnover in shared Kotlin at fixed ticks. The renderer consumes read-only climate, sapling, forage, and creature projections. Rules v1–v3 retain their replay fixtures.

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

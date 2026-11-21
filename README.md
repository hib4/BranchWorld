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

### Local release builds

```sh
./gradlew :androidApp:assembleRelease :androidApp:lintRelease :desktopApp:packageDmg
```

On Apple Silicon, mount `desktopApp/build/compose/binaries/main/dmg/id.branchworld.game-1.0.0.dmg`, copy the app out of the image, and open it. The DMG is built for local review and is not notarized.

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

### Play and compare

BranchWorld opens on **The Fading Grove**, the live scene for seed `104729`. Choose **Begin story** to start time, or **Continue** to load the local save. Other island seeds and camera motion settings are in **Menu**.

| Action | Android | Desktop |
| --- | --- | --- |
| Orbit | Drag one finger | Drag the primary mouse button or focus the viewport and use the arrow keys |
| Pan | Drag two fingers together | Drag the secondary mouse button, Shift-drag, or hold Shift and use the arrow keys in the focused viewport |
| Zoom | Pinch | Scroll |

Use **Focus island** to return to the opening overview. **Inspect** lets you select and follow a creature or check on a settlement. **Restore grove** opens a map of candidate regions. Select a region to see it highlighted in the world, then choose **Plant saplings**. If time is paused, the planting takes effect on the next tick after you choose **Play**. **Menu** → **Show diagnostics** exposes seed, ticks, playback speeds, and wildlife counters.

**Menu** → **Save world** captures the current tick and pending interventions in one local slot. **Continue** on the opening screen restores the world in a paused state. Older saves retain their original rules, and an existing single-island save opens as **Main** at its saved tick.

For the reproducible branching walkthrough, start on seed `104729`. Pause, then use **Timeline** → **Play to tick** to reach `600`. Use **Go to tick** to rewind to `300`. Choose **Fork here** and name the branch **Restored Forest**. Close Timeline, open **Restore grove**, select **C2**, and plant saplings. Reopen Timeline, play the child to `900`, select **Main**, and play it to `900` as well. **Compare branches** opens the matched views. **Show left** and **Show right** switch histories at the same camera position. On narrow screens, open **Compare details** to see the branch picker, time controls, and changes. Main has 76 stored wood and Restored Forest has 124. Select **Reforest C2 at tick 301** to see the six saplings planted by that command. Close comparison, save, and use **Continue** after relaunch to reopen both histories.

Run the common replay fixture and the Android gesture, world clock, and Activity recreation test on a connected device with `./gradlew :shared:connectedAndroidDeviceTest :androidApp:connectedDebugAndroidTest`.

## Architecture

- `androidApp` and `desktopApp` own platform startup, system input bridges, and window or Activity lifetime. The existing iOS scaffold consumes the shared module and is outside the launch gate.
- `shared/src/commonMain` owns the Compose shell, navigation state, and viewport slot. Compose restores the shell across Android recreation. A separate versioned checkpoint restores the world, with platform adapters for atomic local saves.
- `renderer` owns the Kool scene, creature model pool, camera, and platform adapters. Android hosts GLES under Compose. Desktop delivers a Vulkan color attachment to Compose. The bunny and deer GLBs use the bundled Kenney texture.
- Shared Kotlin generates terrain, climate, props, and traversability from the seed and worldgen version. Kool builds chunk meshes and instances the bundled pines and rocks from a static terrain projection.
- Rules v5 adds settlements to the shared fixed-tick ecology. Cohorts farm, gather, expand, and respond to shortages. The renderer receives read-only homes, farm, and resident positions. Rules v1–v4 retain their replay fixtures.

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
| Kenney Interface Sounds (`audio/kenney-interface-sounds`) | 100 OGG audio files. | Two clips are bundled as small mono WAVs for fork and save feedback. |

Keep the original packs in `assets/`. Import only the models, textures, and clips verified for runtime use on Android and Desktop.

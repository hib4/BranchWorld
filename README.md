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

BranchWorld opens on **The Fading Grove**, a river valley entering its dry season at seed `104729`. Choose **Begin story** to start time or **Continue** to load the local save. **Explore islands** also opens **Cairnwater**, a lake between stone ridges, and **Heron Reach**, a cape that shelters reed beds and tidal shallows. Camera motion settings are in **Menu**.

| Action | Android | Desktop |
| --- | --- | --- |
| Orbit | Drag one finger | Drag the primary mouse button or focus the viewport and use the arrow keys |
| Pan | Drag two fingers together | Drag the secondary mouse button, Shift-drag, or hold Shift and use the arrow keys in the focused viewport |
| Zoom | Pinch | Scroll or press `+` and `-` in the focused viewport |

Use **Focus island**, or `Home` in the focused Desktop viewport, to return to the overview. In **Inspect**, select Thistle the rabbit, Alder the deer, or Russet the fox to follow them. Their behavior changes with the available forage, water, and cover. **Wildlife history** records births, new feeding grounds, and losses. Select an event to go to the animal or the place it happened. **Landmarks** describes local soil conditions, and **Settlements** shows how nearby communities are doing.

**Shape** opens a paused preview. **Call rain** can replenish Hollowmere’s drinking bank and help its next harvest. **Restore grove** marks the individual planting sites. Groves need time to root in suitable soil, then hold moisture and provide forage and shelter. Each history has one intervention before natural rain replenishes the watershed. Choose a power and confirm it, or use **Cancel intervention** before playback applies it.

**Menu** → **Show diagnostics** exposes the seed, playback speeds, and wildlife counters. Enable **Timeline tick tools** there to enter an exact point for replay or comparison.

**Menu** → **Save world** captures the current tick and pending interventions in one local slot. **Continue** restores the world in a paused state. Older saves retain their original rules and geography, and an existing single-island save opens as **Main** at its saved tick.

In **The Fading Grove**, let Hollowmere reach its warning. Use **Timeline** → **Watch the river recover** to see how Main fares. Choose **Return to the drought warning** → **Fork here**, name a rain history, and use **Shape** to call a shower over the suggested field and drinking bank. Watch two harvests, then watch through recovery. Return to the warning again, name a grove history, and restore the grove at C2. Watch through recovery in that history too.

From either fork, open **Timeline** → **Compare branches**. Living Echo matches the two histories to the same moment. Select a history by name to switch the world in place. In **Details**, choose the two forks, then return to **Island view**. Select **Harvest** and focus **Drinking bank** or **Farm field** to see where the rain helped. Select **After rain** and focus **Grove sites**. Pale trees show where the grove grew in the other history. **Details** shows the recorded events and actual values. Pick an intervention there to replay its result. **Continue this history** opens the view you chose at that moment. **Back** returns to where you entered.

Save through **Menu** and relaunch with **Continue** to reopen all three histories.

The shared fixture starts each fork at tick `240`. It compares water and crops at `360`, then rooted habitat and wildlife use at `900`.

Run the common replay fixture and the Android gesture, world clock, and Activity recreation test on a connected device with `./gradlew :shared:connectedAndroidDeviceTest :androidApp:connectedDebugAndroidTest`.

## Architecture

- `androidApp` and `desktopApp` own platform startup, system input bridges, and window or Activity lifetime. The existing iOS scaffold consumes the shared module and is outside the launch gate.
- `shared/src/commonMain` owns the Compose shell, navigation state, and viewport slot. Compose restores the shell across Android recreation. A separate versioned checkpoint restores the world, with platform adapters for atomic local saves.
- `renderer` owns the Kool scene, creature model pool, camera, and platform adapters. Android hosts GLES under Compose. Desktop delivers a Vulkan color attachment to Compose. The Living Echo layer draws sites from an immutable comparison of two matching shared snapshots. The bunny, deer, and fox GLBs are from Kenney Cube Pets.
- Shared Kotlin generates terrain, drainage, soils, props, and traversability from the seed and worldgen version. Kool builds chunk meshes, fills the generated channels and basins, and instances the bundled trees from a read-only terrain projection.
- Rules v8 adds branch-local interventions, soil retention, and settlement water welfare. Rules v7 introduced local habitat, competition for forage, and fox pursuits. Each history records wildlife milestones alongside its commands and snapshots. Rules v6 introduced seasonal recharge and soil moisture. Saved histories retain their original rules and terrain.

## Source asset inventory

The eight downloaded packs live under `assets/`. The formats and animation entries below were inspected in the local files. The intended uses are candidates for later integration.

| Pack | Inspected runtime candidates and alternatives | Animation and intended use |
| --- | --- | --- |
| KayKit Forest (`environment/kaykit-forest`) | 105 glTF models with BIN and PNG dependencies. FBX and OBJ/MTL variants. | Static trees, rocks, and vegetation for the diorama and island. |
| Kenney Nature (`environment/kenney-nature`) | 329 GLB models. FBX, OBJ/MTL, DAE, STL, and PNG previews. | Static rocks, ground props, and vegetation for the diorama and terrain. |
| Kenney Cube Pets (`animals/kenney-cube-pets`) | 24 GLB models plus FBX and OBJ/MTL. | Runtime rabbit, deer, and fox use the CC0 models and their idle, walk, run, and eat clips. |
| Vuena Pets (`animals/vuena-pets`) | Three GLB and FBX models with PNG textures. | Skinned cat, fox, and owl with embedded clips including idle, walk, eat, and sleep. Candidates for additional wildlife. |
| KayKit Medieval (`buildings/kaykit-medieval`) | 221 glTF/BIN models plus FBX, OBJ/MTL, and PNG textures. | Static buildings and props for settlements. |
| KayKit Adventurers (`characters/kaykit-adventurers`) | Six skinned character GLBs, two animation GLBs, and glTF/BIN, FBX, OBJ/MTL, and PNG assets. | Separate Rig Medium general and locomotion clips. Candidates for future inhabitants. |
| KayKit Character Animations (`characters/kaykit-character-animations`) | 14 GLB and 14 FBX animation files plus two mannequin variants in each format. | Separate Rig Medium and Rig Large motion clips. Candidate motion library for compatible characters. |
| Kenney Interface Sounds (`audio/kenney-interface-sounds`) | 100 OGG audio files. | Six clips are bundled as small mono WAVs: fork and save feedback, plus `select_002`, `confirmation_002`, `back_002` and `open_002` for the menus (converted with ffmpeg to 22.05 kHz mono). |
| Fredoka and Nunito (`assets/fonts`) | Static TTF instances from Google Fonts, SIL Open Font License 1.1 (`Fredoka-OFL.txt`, `Nunito-OFL.txt`). | Fredoka Medium, SemiBold and Bold for titles and controls and Nunito Medium, Bold and ExtraBold for interface text, bundled in `shared/src/commonMain/composeResources/font`. |

The runtime tree set uses Kenney Nature’s `tree_pineRoundA`, `tree_pineRoundB`, and `tree_pineTallA_detailed`, with in-engine matte materials and normalized ground pivots. The original BranchWorld surf, wind, rain, and bird layers can be regenerated with `python3 assets/audio/branchworld/generate_coast.py`. World Sounds controls the ambience and interaction cues. Reduce Motion holds decorative effects and camera transitions while keeping weather and lighting visible.

Hollowmere (rules v9) uses KayKit Medieval homes A/B in four colours, the well, scaffolding, stores, fence and lumber, all sharing one runtime copy of `hexagons_medieval.png`, plus Kenney Nature furrows, wheat, stumps and log stacks. Villagers are built by `python3 -I tools/assets/build_villagers.py .`, which combines the KayKit Ranger, Rogue and Barbarian bodies (quiver and bear hat removed) with ten renamed `Rig_Medium` clips: idle, walk, carry, chop, dig, hammer, pick, gather, sit and wave.

Keep the original packs in `assets/`. Import only the models, textures, and clips verified for runtime use on Android and Desktop.

# BranchWorld

**Create a world. Fill it with life. Watch what it becomes.**

BranchWorld is a Kotlin Multiplatform living-world sandbox. You create a small 3D island, set down rabbits, wolves, deer, foxes, trees and grass by hand, and watch them live on their own: rabbits graze, dig warrens and raise litters; wolves form packs, howl at dusk and hunt together; populations rise and fall; fire and rain change the land. Nothing is scripted. Every consequence comes from deterministic rules written once in shared Kotlin and run identically on Android, Desktop, iOS and the web.

## Build and run

Use the checked-in Gradle wrapper. Its daemon toolchain selects Azul JDK 21. For Android, install the Android 17 / API 37 SDK platform and Platform-Tools through Android Studio. Point `ANDROID_HOME` or the local `sdk.dir` property at the SDK. The app supports API 26 and later.

```sh
./gradlew :androidApp:assembleDebug :androidApp:lintDebug :desktopApp:assemble
./gradlew :simulation:jvmTest :simulation:testAndroidHostTest :shared:jvmTest :shared:testAndroidHostTest :renderer:jvmTest :renderer:testAndroidHostTest
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

### Web

The browser build is Kotlin/JS (Kool publishes no Wasm target) and draws with WebGL 2. It needs a current Chrome, Edge, Firefox or Safari.

```sh
./gradlew :webApp:jsBrowserDevelopmentRun        # opens a development server with live reload
./gradlew :webApp:jsBrowserDistribution          # static site in webApp/build/dist/js/productionExecutable
./gradlew :simulation:jsNodeTest :shared:jsNodeTest :renderer:jsNodeTest # the rules and replay fixtures under Kotlin/JS
```

The distribution uses relative paths, so any static host serves it from any folder. Every push to `main` runs `.github/workflows/web.yml`, which checks the shared rules under Kotlin/JS and publishes the bundle to GitHub Pages. In the repository settings, set **Pages → Source** to **GitHub Actions** once. Worlds save in the browser's IndexedDB; photos go to the browser's downloads.

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

### Play

**Create.** On the title choose **New world**. Pick an island (The Fading Grove, Cairnwater or Heron Reach) or type or roll a seed, choose **Empty land** (grass, trees and water, no animals) or **Wild** (rabbits, deer and foxes already live there), give it a name and choose **Create world**. **Continue** reopens your last world; **My worlds** lists every world you have made.

**Populate.** The dock holds a palette of wooden pegs: **Look**, **Rabbit**, **Wolf**, **Deer**, **Fox**, **Tree**, **Grass** and **Lift out**. Choose one, then click or tap the island. A peg on the ground shows where it would go: green where it can, madder where it cannot, and a short note says why (open water, unreachable ground, nothing takes root there, the island is full). Animals set down while time is paused appear at once. **Lift out** removes an animal or something you planted.

**Observe.** Use **Play**/**Pause** and **1×**, **2×**, **4×** or **8×** to set the pace. Choose **Look** and tap an animal to open its card: its name, sex and age, what it is doing and why ("Chasing Clover with the pack", "Hiding from Fern"), its food, water, energy and stamina, its pack and its litters. **Follow** keeps the camera with it. The name board counts every species on the island.

**Experiment and discover.** **What happened** lists the moments the world produced (a pack forming, a first kill, an escape, a first litter, a boom, a crash, an animal that starved, a fire) with **Why?** to follow each back to its causes and **Show me** to go there. **Timeline** lets you go back to any moment and start another history beside the first, compare the two, and share a world as a code. Worlds save automatically.

| Action | Android | Desktop |
| --- | --- | --- |
| Set down or select | Tap the island | Click the island |
| Orbit | Drag one finger | Drag the primary mouse button, or focus the viewport and use the arrow keys |
| Pan | Drag two fingers together | Drag the secondary mouse button, Shift-drag, or Shift and the arrow keys |
| Zoom | Pinch | Scroll, or `+` and `-` in the focused viewport |

**Focus island**, or `Home` in the focused Desktop viewport, returns to the overview. Settings hold sound, Reduce Motion and text size; its developer section holds timeline tick tools.

Run the replay fixtures on a connected device with `./gradlew :simulation:connectedAndroidDeviceTest`, and the phone walkthrough with `./gradlew :androidApp:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=id.branchworld.game.LivingSandboxDeviceTest`.

## Architecture

BranchWorld follows a layered architecture: each layer depends only on the layers below it, and the deterministic rules sit in a module of their own so the compiler keeps the interface out of them.

```
androidApp / desktopApp / webApp / iosApp   startup, window or page lifetime, platform adapters
        │                   │
    :renderer ──────────► :shared            ui ─► data ─► domain ─► platform (ports)
                            │
                       :simulation           deterministic rules, saves and render projections
```

### `:simulation` — the world's rules (no Compose, no platform code)

| Package | What lives there |
|---|---|
| `core` | `GridCell`, `SplitMix64`, `CanonicalWriter`, `CreatureHeading`, and `RulesVersions.kt`: every rule set and world generator a save can name |
| `terrain` | Island generation, drainage, navigation |
| `ecology` | World state and the rules of each system: `environment`, `wildlife`, `range`, `life` (rules v12), `fire`, `settlement`, `chronicle`, `chapters` (the crafted chapters), `legacy` (rules v3–v6 animals) |
| `command` | `WorldCommand` and its tick-ordered queue: the player's acts, recorded in every history |
| `engine` | `EcologyEngine`: one step applies commands, growth and fire, then hands over to the rules era that finishes it (`finishLivingStep` for v12, `finishEarlierStep` for v3–v11) |
| `timeline` | Branches, replay, seeking and history browsing |
| `save` | Save codecs (schemas 1–10) and shareable world codes |
| `projection` | Read-only poses the renderer and interface draw: `WorldRenderProjection`, `LivingEchoProjection`, `WorldCamera`, `WorldPointer` |
| `runtime` | `WorldClock`, the coroutine that ticks a world at its chosen speed |
| `legacy` | Rules v1–v2 (the first walking rabbit) |

Living worlds run rules v12 in `ecology/life`. `LifeRules` runs each step over its collaborators:
- `LifeGround` holds what the animals perceive, plus movement.
- `LifePlacement` checks the player's hand.
- `PreyBehaviour`, `PredatorBehaviour` and `EverydayNeeds` decide what each animal does.

Each creature decides from what it could perceive on the previous tick and draws counter-based random rolls, so the result never depends on processing order. Earlier rule sets stay so that saves made with them keep replaying exactly. Their replay hashes are pinned in the tests, so a refactor that changes any history fails the build.

### `:shared` — the game around the rules

| Layer | What lives there |
|---|---|
| `platform` | Ports each host implements, bundled in `AppServices`: `WorldViewport` (3D view), `WorldSoundPlayer`, `PhotoSaver`, `WorldSaveStore`. Also the shared code the hosts reuse: `KeyedWorldSaveStore` over a tiny `BlobStorage`, the save layout (`island-v3.json`, `worlds/<name>`) and `AmbienceGlide` |
| `domain` | `IslandSessionKey` (which world), autosave and soundscape policy, `WorldInteraction` (what a tap of the hand does), and `session/`. `IslandLoad.Ready` joins one `WorldSessionCore` with role classes: `WorldPlacement`, `HistoryNavigation`, `BranchComparing`, `ChapterControls` |
| `data` | `WorldRepository` (open, cache, autosave, import), `ProfileRepository`, `PlayerProfile` |
| `ui` | `app` (`App`, `AppViewModel`, navigation, layout), `theme`, `components`, `home`, `share`, `world` (HUD, panels, inspector, timeline, comparison, echo, history) and `legacy` (the earlier chapters' screens and HUD) |

`App(services, foreground)` is the whole contract with a host. The world screen keeps its momentary state in `WorldScreenState`, and its side effects (clock, taps, sound) live in `WorldEffects`.

### `:renderer` — Kool behind the `WorldViewport` port

| Package | What lives there |
|---|---|
| `camera` | `PreviewController` (camera state, picking, `ViewportStatus`), `PreviewCamera`, `GroundPicking`, `FocusFraming` |
| `input` | `TouchGestureTracker`, shared by Android and the Skiko targets. `pointerCameraInput` handles mouse, wheel and keys on Desktop and the web |
| `scene` | `DioramaSession` (per-frame orchestration), `IslandScene`, `PineForest`, terrain, water and light. `scene/life` and `scene/effects` hold creatures, villages, fire and weather details |
| `hud`, `assets`, `diagnostics` | Status and camera reset, the asset registry, frame timing |

`DesktopDiorama` (Vulkan into Compose), `AndroidDiorama` (GLES) and `WebDiorama` (WebGL 2 beneath a transparent Compose canvas) each implement `WorldViewport`. The iOS app has no 3D renderer yet and shows a drawn island.

### Where new code goes

| Change | Where it goes |
|---|---|
| A new rule | Its system's package in `:simulation`, behind a new entry in `core/RulesVersions.kt` |
| A new player act | A `WorldCommand` with an explicit `@SerialName` |
| What the interface does with a world | `domain/session` |
| Storage, sound or photos for a platform | A new adapter for an existing port |
| A screen | Its feature folder under `ui` |

## Source asset inventory

The eight downloaded packs live under `assets/`. The formats and animation entries below were inspected in the local files. The intended uses are candidates for later integration.

| Pack | Inspected runtime candidates and alternatives | Animation and intended use |
| --- | --- | --- |
| KayKit Forest (`environment/kaykit-forest`) | 105 glTF models with BIN and PNG dependencies. FBX and OBJ/MTL variants. | Static trees, rocks, and vegetation for the diorama and island. |
| Kenney Nature (`environment/kenney-nature`) | 329 GLB models. FBX, OBJ/MTL, DAE, STL, and PNG previews. | Static rocks, ground props, and vegetation for the diorama and terrain. |
| Kenney Cube Pets (`animals/kenney-cube-pets`) | 24 GLB models plus FBX and OBJ/MTL. | Runtime rabbit, deer, fox and wolf use the CC0 models and their idle, walk, run and eat clips; the wolf is the dog model with a grey coat applied in the renderer. |
| Vuena Pets (`animals/vuena-pets`) | Three GLB and FBX models with PNG textures. | Skinned cat, fox, and owl with embedded clips including idle, walk, eat, and sleep. Candidates for additional wildlife. |
| KayKit Medieval (`buildings/kaykit-medieval`) | 221 glTF/BIN models plus FBX, OBJ/MTL, and PNG textures. | Static buildings and props; candidates for placeable structures. |
| KayKit Adventurers (`characters/kaykit-adventurers`) | Six skinned character GLBs, two animation GLBs, and glTF/BIN, FBX, OBJ/MTL, and PNG assets. | Separate Rig Medium general and locomotion clips. Candidates for future placeable people. |
| KayKit Character Animations (`characters/kaykit-character-animations`) | 14 GLB and 14 FBX animation files plus two mannequin variants in each format. | Separate Rig Medium and Rig Large motion clips. Candidate motion library for compatible characters. |
| Kenney Interface Sounds (`audio/kenney-interface-sounds`) | 100 OGG audio files. | Six clips are bundled as small mono WAVs: fork and save feedback, plus `select_002`, `confirmation_002`, `back_002` and `open_002` for the menus (converted with ffmpeg to 22.05 kHz mono). |
| Fredoka and Nunito (`assets/fonts`) | Static TTF instances from Google Fonts, SIL Open Font License 1.1 (`Fredoka-OFL.txt`, `Nunito-OFL.txt`). | Fredoka Medium, SemiBold and Bold for titles and controls and Nunito Medium, Bold and ExtraBold for interface text, bundled in `shared/src/commonMain/composeResources/font`. |

The runtime tree set uses Kenney Nature’s `tree_pineRoundA`, `tree_pineRoundB`, and `tree_pineTallA_detailed`, with in-engine matte materials and normalized ground pivots. The original BranchWorld surf, wind, rain, and bird layers can be regenerated with `python3 assets/audio/branchworld/generate_coast.py`. World Sounds controls the ambience and interaction cues. Reduce Motion holds decorative effects and camera transitions while keeping weather and lighting visible.

Keep the original packs in `assets/`. Import only the models, textures, and clips verified for runtime use on Android and Desktop.

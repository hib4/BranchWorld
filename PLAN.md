# BranchWorld - 10-Phase Development Plan

**Tagline:** Every decision creates another world.

**Document purpose:** This file defines WHAT the agent must deliver, in 10 major milestones. `AGENTS.md` defines HOW the agent executes, verifies, and reports. No additional phase-prompt files are required.

**Current implementation status:** Unverified until the coding agent inspects and builds the actual repository. The milestones below are specifications, not claims that features already exist.

## Product goal

Create an inviting low-poly 3D miniature world that behaves like a real simulation: islands grow forests, animals seek resources, weather affects survival, and later civilizations develop. The player can intervene, rewind to a past tick, branch a new future, and compare the consequences against the original world. Differences must emerge from deterministic simulation rules, not pre-authored outcomes.

**Primary platforms:** Android API 26+ and Desktop JVM. **UI:** Compose Multiplatform. **Simulation:** shared Kotlin. **3D:** Kool candidate, subject to Phase 02 verification. **Future:** iOS through a separately validated renderer strategy.

### North-star playable experience

1. Generate a recognizable island with seed `104729`, water, trees, and wildlife.
2. Run its fixed-tick ecology and observe meaningful resource/population changes.
3. At tick `300`, fork an independent branch named `Restored Forest`.
4. Reforest one area in the child only; advance parent and child to tick `900`.
5. Compare both worlds at the SAME simulation tick using measured vegetation, food, wildlife, and events.
6. Save, close, reopen, and inspect both branches without corruption or state contamination.

This is the signature vertical slice. All phase work should lead toward it.

## Milestones at a glance

| Phase | Name | Visible result | Engineering proof |
|---|---|---|---|
| 01 | Foundation & Architecture | Android/Desktop app shell | Real target builds and shared boundaries |
| 02 | 3D Engine & Asset Integration | Interactive 3D diorama | Renderer, controls, and real assets on both targets |
| 03 | Deterministic Simulation | Live world clock and state | Stable ticks, PRNG, ordering, canonical hashes |
| 04 | Procedural World Generation | Seeded islands and biomes | Reproducible terrain and consistent 3D mapping |
| 05 | Living Ecosystems & Creature AI | Animals and vegetation interact | Deterministic, measurable ecological feedback |
| 06 | Environment, Interaction & Saves | Weather, interventions, persistence | Commands affect ecology and survive restart |
| 07 | Time Travel & Branching | Rewind and independent timelines | Replay equality, branch isolation, durable histories |
| 08 | Butterfly Effect Explorer | Compare divergent futures | Matched-tick, real-data comparison |
| 09 | Civilizations & Emergent History | Settlements, growth, decline | Deterministic economy/ecology/history feedback |
| 10 | Polish, Optimization & Release | Cohesive cross-platform game | Performance, full regression, reproducible release |

## Phase specifications and acceptance gates

### Phase 01 - Foundation & Architecture

**Outcome:** A buildable, maintainable game foundation, with a real Android and Desktop application shell.

**Build:** Audit existing Gradle modules, Kotlin/Compose versions, source sets, tests, `.agents/skills/`, SDKs, and assets before changing anything. Configure Android minSdk 26 and Desktop JVM, Compose application navigation, a placeholder world viewport, a diagnostic overlay, and app lifecycle plumbing. Establish boundaries between shared simulation, rendering adapters, persistence and UI. Add shared smoke tests and reproducible build/run instructions in the repository's existing documentation. Inventory the eight downloaded packs by actual formats, animation support and intended runtime use, without doing licensing audits.

**Done when:** Both targets actually build and launch; shared tests run; an app shell accepts input and survives ordinary lifecycle changes. Record precise Gradle tasks, devices/SDKs, outcomes, architecture decisions, and any target that could not be tested.

### Phase 02 - 3D Engine & Asset Integration

**Outcome:** An appealing interactive low-poly diorama whose renderer can be replaced without rewriting simulation logic.

**Build:** Run a minimal Kool proof of concept on Android and Desktop, including Compose HUD interoperability, scene lifecycle, touch/mouse input, resize and pause/resume. Load selected real vegetation, rock, and animal/character models from the downloaded KayKit/Kenney packs; verify actual file formats, model pivots, scale, materials, and animations. Implement orbit/pan/zoom/focus camera controls, water or terrain placeholder, lighting, resource loading and disposal. Keep scene objects outside shared world state.

**Done when:** A user can orbit, pan and zoom a rendered scene with imported assets and a working Compose overlay on both targets. Measure device-specific frame time, asset loading and memory; document a renderer go/no-go decision. Replacing Kool requires human approval.

### Phase 03 - Deterministic Simulation Engine

**Outcome:** A standalone shared-Kotlin world kernel that produces the same outcomes under the same starting conditions, independently of render FPS.

**Build:** Define `WorldState`, stable IDs, deterministic PRNG, fixed tick update order, tick-indexed commands, explicit world ruleset version, world clock, immutable render projections, and canonical world-state serialization/hash. Compose controls pause/resume/speed but never mutates state directly. Keep coroutine scheduling from influencing authoritative decisions.

**Done when:** Identical seed + version + ordered commands give identical canonical hashes at ticks `0`, `300`, `600`, `900` across repeated runs. Verify ordering, pause/speed equivalence, restart/replay readiness, and cross-target fixture consistency where runners are available. Clearly report numeric precision limitations.

### Phase 04 - Procedural World Generation

**Outcome:** Beautiful, distinct, reproducible miniature islands generated by shared Kotlin rules.

**Build:** Implement a seeded island mask, terrain elevation, coastlines, beaches, biomes, moisture/temperature, plant/rock placement and usable land/water geography. Render terrain meshes/chunks and instanced assets through the renderer. Expose terrain queries for navigation, resources and animal habitats. Favor coherent geography over half-finished advanced features.

**Done when:** Seeds `104729`, `271828`, and `424242` produce recognizable different islands; repeating a seed and worldgen version reproduces authoritative geography and placements. Verify mesh integrity, valid elevations, traversability where required, and measured generation/load performance on both targets.

### Phase 05 - Living Ecosystems & Creature AI

**Outcome:** A compact but genuine ecology where creatures and resources influence one another.

**Build:** Model vegetation biomass, consumption and regrowth; at least two distinct animal behavior profiles where assets and performance permit; needs such as food, rest and water; deterministic perception/movement/foraging; reproduction/death or equivalent population turnover; habitat and survival constraints. Use simple state machines or utility scoring rather than unnecessary generic AI infrastructure. Render animal movement and appropriate animations based on simulated state.

**Done when:** Animals choose actions based on needs and environment, food patches respond to grazing, and at least one ecological feedback loop is observable in a repeatable seed scenario. Tests confirm deterministic decisions, valid resource bounds and stable long-running behavior. Record population and biomass traces, not only screenshots.

### Phase 06 - Environmental Systems, World Interaction & Saves

**Outcome:** A living world players can inspect, change, save and restore.

**Build:** Add world day/night and simple weather (e.g., rainy/dry), with at least one REAL effect on moisture, plant growth or wildlife. Implement a world/creature inspector, play/pause/speed controls, and two meaningful player interventions including regional reforestation. Route every intervention through deterministic commands. Add versioned save/load for world state, ruleset/worldgen versions, tick, PRNG, creatures and resources; implement storage adapters for both targets and safe handling of missing/corrupt files.

**Done when:** At least two interventions visibly and measurably affect world simulation, and the world survives a complete save/quit/relaunch/load/resume cycle on Android and Desktop. Serialization round-trip and corrupt-file tests pass; a saved-state hash matches the restored state.

### Phase 07 - Time Travel & Timeline Branching

**Outcome:** Correct rewind, replay, and persistent alternative histories.

**Build:** Create an append-only ordered command history and periodic snapshots, reconstruct past states by replay, and model a branch ancestry graph with stable IDs, fork ticks, branch-local commands, mutable state and PRNG. Support rewind, create branch, switch branch and resume execution. Reuse the same simulation engine and persistence. Bound cache/memory use; avoid a full world copy for every rendered frame.

**Done when:** Rewinding from tick `600` to `300` and replaying to `600` reproduces the original canonical gameplay-state hash. A fork at tick `300` does not mutate its parent; parent and child can advance independently. Save/restore preserves branches, snapshots, inputs, and deterministic replay. Test switching, corrupt history and repeated forks.

### Phase 08 - Butterfly Effect & Timeline Explorer

**Outcome:** BranchWorld's signature mechanic becomes understandable and satisfying for players.

**Build:** Create a Compose timeline tree, branch selection and naming, scrubber, intervention indicators and a paired comparison view (side-by-side or synchronized switching). Compare branches at the SAME tick using actual simulated vegetation, food, wildlife, and significant events. Explain observed differences without inventing causes or scripted results. Make the complete flow accessible with mouse/keyboard and touch.

**Done when:** A new player can run the north-star scenario: seed `104729`, fork at tick `300`, reforest only the child, advance both to `900`, inspect genuine differences, and save/reload both histories. The comparison uses canonical simulated state, not animated decoration. Provide screen recordings/screenshots where possible and a concise user walkthrough.

### Phase 09 - Civilizations & Emergent History

**Outcome:** Autonomous settlements arise within the ecosystem and develop distinct histories across branches.

**Build:** Simulate inhabitants or population cohorts, settlement selection, food/wood/stone gathering, storage, farming, homes, work allocation, population change, scarcity and migration. Use the existing KayKit medieval buildings and character assets where technically suitable. Have settlement activity consume and transform environmental resources; record founding, shortage, expansion and abandonment events in the same history system. Favor aggregated simulation where individual agents would be wasteful.

**Done when:** At least one settlement develops autonomously, reacts to ecological and economic conditions, and creates persistent historical events. An intervention can measurably alter settlement outcomes between branches. Verify deterministic replays, branch isolation, save/load and representative performance.

### Phase 10 - Polish, Optimization & Release

**Outcome:** A cohesive, stable, high-quality experience on verified Android and Desktop builds.

**Build:** Refine materials, terrain, water, lighting, wildlife animation, sound, camera transitions, onboarding, UI readability, performance and error states. Profile renderer frame times, draw calls, memory, worldgen, simulation throughput, load/save and rewind latency on identified hardware. Apply bounded performance experiments only after correctness is protected. Run regression, lifecycle, file corruption, deterministic replay and long-run simulation tests. Prepare real build instructions, an honest feature list, reproducible screenshots/demo and installable release artifacts.

**Done when:** A fresh installation on both primary platforms can run the signature world -> intervention -> rewind -> fork -> compare -> save/restore flow. No known critical data-loss or branch-contamination issues remain. Record real tested builds, devices, measured budgets and release limitations.

## Implementation order and release scope

- Work phase-by-phase, completing the end-to-end player-visible slice and its verification. Minor preparatory interface design for later phases is fine; do not build unrelated future systems early.
- Correctness, UX, accessibility, tests and profiling are continuous duties, not deferred until Phase 10.
- Phase 09 is important for the FULL GAME, but not necessary for the signature alternate-history demo. With explicit user approval, mark Phase 09 `DEFERRED (contest track)` and proceed to essential Phase 10 release checks after Phase 08. This does not count Phase 09 as complete.
- For an approaching contest deadline, prefer one small, beautiful, stable island and a real measurable branch comparison over many unfinished features. Confirm current contest requirements separately before submission.
- Android/Desktop are authoritative launch targets. iOS is not implied by shared Kotlin logic and requires its own rendering validation.

## Phase acceptance and evidence

A phase is ready for review only when it has a demonstrable player-visible outcome (where applicable), meaningful automated checks, actual target-specific build/run evidence, a reproducible demo scenario, no unacknowledged architecture violations, and an honest list of remaining risks.

`PASS`, `FAIL`, `BLOCKED`, and `NOT RUN` must be distinguished. A green JVM test is not proof of working Android graphics. Completion requires explicit human acceptance; the agent must stop before the next milestone.

### Progress Ledger (agent-maintained; update ONLY based on actual evidence)

| Phase | Engineering status | Human gate | Evidence / last verified revision |
|---|---|---|---|
| 01 | COMPLETE | ACCEPTED | Verified on Android API 36 and macOS ARM64, with both shared test runners passing. Foundation committed at `28a1c26`. |
| 02 | COMPLETE | PENDING | Kool prototype GO on Android API 36 (GLES) and Apple M4 macOS (Vulkan with Compose frame delivery). Build, input, asset, lifecycle, and scene-release evidence in the Phase 02 handoff. |
| 03 | NOT VERIFIED | PENDING | - |
| 04 | NOT VERIFIED | PENDING | - |
| 05 | NOT VERIFIED | PENDING | - |
| 06 | NOT VERIFIED | PENDING | - |
| 07 | NOT VERIFIED | PENDING | - |
| 08 | NOT VERIFIED | PENDING | - |
| 09 | NOT VERIFIED | PENDING | - |
| 10 | NOT VERIFIED | PENDING | - |

**Allowed engineering statuses:** `NOT VERIFIED`, `IN PROGRESS`, `COMPLETE`, `PARTIAL`, `BLOCKED`, `DEFERRED`. **Human gate:** `PENDING`, `ACCEPTED`, `REJECTED`, or `DEFERRED WITH APPROVAL`. The agent cannot set `ACCEPTED` without an explicit user decision. Do not modify phase requirements without user approval; record technical evidence and decisions below.

### Decision Log (agent-maintained)

| Date / phase | Decision or experiment | Evidence and trade-off | User approval needed? |
|---|---|---|---|
| 2026-11-19 / 01 | Keep the existing Android app, Desktop app, and shared module. | Both apps build with the pinned Gradle 9.5.1, Kotlin 2.4.21, AGP 9.1.1, Compose 1.12.2, and Azul Java 21. APK metadata reports minSdk 26 and targetSdk 37. | No |
| 2026-11-19 / 01 | Use shared, saveable state for the viewport route and diagnostics visibility. Keep the illustrated viewport read-only. | Rotation destroyed and recreated the Android Activity and restored both UI values. Desktop resize and minimize/restore retained them. World state, render projections, and platform storage ownership are documented in README.md. | No |
| 2026-11-19 / 01 | Inventory the eight original packs for later selection. | Inspected actual glTF, GLB, FBX, texture, and audio files and recorded animation metadata and intended use in README.md. Historical renderer logs lack their corresponding source in this checkout. Kool remains a Phase 02 candidate. | No |
| 2026-11-19 / 02 | Pin Kool 0.19.0 in an Android and JVM renderer module. Set its JVM consumers to bytecode level 17. | Both apps built and ran with the existing Azul Java 21 toolchain. The APK keeps minSdk 26. The Desktop bundle includes `jdk.unsupported` for LWJGL. Checked 11 packaged ARM64 native binaries against the Maven artifacts. Their executable sections match and the package signatures differ, which accounts for the hash warning. | No |
| 2026-11-19 / 02 | Keep the Android EGL context for the app process and release each diorama when leaving the viewport. | A stock surface reproduced the retained-context error on Activity recreation. The retained EGL context passed repeat launches and rotation on the software-rendered AVD. A GPU reset or context loss on physical hardware remains unverified. | No |
| 2026-11-19 / 02 | Use a Vulkan offscreen pass to supply Desktop frames to Compose. | The native Swing canvas stayed blank beneath the HUD. The copyable color attachment with one readback in flight displayed the scene with working controls. The readback costs memory and limits the view to 768 pixels on its longest side. | Yes, at the phase gate |

### Latest phase handoff (agent-maintained)

```text
PHASE: 02 - 3D Engine & Asset Integration
STATUS: COMPLETE
PLAYER-VISIBLE OUTCOME: Open world shows a lit island, water, imported pines and rocks, and a textured bunny running its walk clip. Orbit, pan, zoom, focus, and pause work alongside the Compose HUD.
CHANGED CODE: Android and JVM renderer module, native surface hosts, injected shared viewport and HUD, bundled GLBs and bunny texture, and device and renderer tests.
TESTS / BUILDS:
  PASS - ./gradlew --offline --console=plain :androidApp:assembleDebug :androidApp:lintDebug :shared:jvmTest :shared:testAndroidHostTest :renderer:jvmTest :renderer:testAndroidHostTest :desktopApp:createDistributable :shared:compileCommonMainKotlinMetadata :shared:compileKotlinIosArm64 :shared:compileKotlinIosSimulatorArm64
         Renderer JVM 5 tests and Android host 3 tests. Shared JVM and Android host 6 tests each. Android lint reported zero errors and four existing launcher warnings.
  PASS - ./gradlew --offline --console=plain :androidApp:connectedDebugAndroidTest
         Pixel_9 ARM64 AVD, API 36. Compared viewport pixels while paused and after orbit, pan, pinch, and resumed animation. Recreated MainActivity and reopened the diorama three times.
  PASS - adb -s emulator-5554 install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
         adb -s emulator-5554 shell am start -W -n id.branchworld.game/.MainActivity
         Opened the assets, exercised the HUD and system Back, backgrounded and returned, and verified rotation by Activity destruction and creation events and a rendered landscape screenshot.
  PASS - open -n desktopApp/build/compose/binaries/main/app/id.branchworld.game.app
         macOS 27.2.1 ARM64, Apple M4, Vulkan via MoltenVK. Observed real pixels, HUD clicks, arrow-key orbit, Shift-arrow pan, wheel zoom, resize, minimize/restore, and three scene reopens. Normal close freed the Vulkan device.
         Primary and secondary drag passed the JVM Compose input test. Manual OS-level drag delivery could not be confirmed with the available Desktop UI driver.
  NOT RUN - Physical Android, Android API 26 runtime, iOS runtime, and non-macOS Desktop runtime.
DETERMINISM / SAVE / BRANCH: The authored scene and camera are presentation state. Display seed 104729 labels the preview. The integer-tick world begins in Phase 03.
PERFORMANCE / DEVICE:
  Android GLES on the API 36 AVD with ANGLE and SwiftShader, 840x1256. Cold first complete asset frame 720 ms. Warm 388–490 ms.
  Ten-second samples after a 2-second settle measured p50/p95 25.0/36.2 ms (381 frames) and 26.9/49.0 ms (294 frames). An earlier run measured 94.2/254.5 ms, so emulator frame pacing varied substantially.
  Android PSS in KiB before the scene, active, and at the third return home was 87267, 111548, and 128267. Active GPU buffer and texture totals were the same across the sampled reopens.
  Desktop Vulkan at 768x332. First complete frame delivered to Compose in 1009 ms cold and 233 ms warm.
  Desktop 10-second samples at the original viewport measured 36.7/38.3 ms cold-open p50/p95 and 36.6/38.1 ms warm-open p50/p95 at the Compose delivery point. Samples followed a 2-second settle.
  Desktop RSS in KiB before load, active, and after release in the final measured run was 195408, 507248, and 465520. A separate three-reopen run ended at 318480 KiB with four verified scene releases.
DEMO: Open world, wait for Island ready, then pause and orbit, pan, zoom, and focus. Select Show diagnostics to see seed 104729. Reopen three times and rotate Android or resize and minimize Desktop.
EVIDENCE: Local Gradle, Android, and Desktop logs and Android captures are under build/verification/phase02. Desktop captures were inspected during runtime checks.
BLOCKERS / NEXT STEPS: GO for the two tested runtimes. Desktop readback cost and Android software-emulator variance are recorded for follow-up profiling. After approval, start Phase 03 with a shared integer-tick kernel, PRNG, ordered command queue, and repeatable canonical hash fixtures.
HUMAN APPROVAL: PENDING
```

## How to invoke the coding agent

For the first session: **"Read AGENTS.md and PLAN.md. Inspect the repository, then execute Phase 01 end-to-end. Run real checks, update the Progress Ledger in PLAN.md, and stop for my approval."**

For subsequent sessions: **"Execute Phase NN according to AGENTS.md and PLAN.md. Do not advance beyond this phase without my approval."**

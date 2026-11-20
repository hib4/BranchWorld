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
| 02 | COMPLETE | ACCEPTED | Kool prototype GO on Android API 36 (GLES) and Apple M4 macOS (Vulkan with Compose frame delivery). Verified at `73ca11e` and approved by the user. |
| 03 | COMPLETE | ACCEPTED | Rules v1 checkpoints and runtime playback verified at `790e34e`. Approved by the user. |
| 04 | COMPLETE | ACCEPTED | Terrain and rules v2 fixtures and both primary runtimes were verified at `bcc8069`. Approved by the user. |
| 05 | COMPLETE | ACCEPTED | Rules v3 goldens, 10,000-tick ecology fixtures, Android APK, Desktop Compose controls, and native Vulkan frames were verified at `496ebef`. Approved by the user. |
| 06 | COMPLETE | ACCEPTED | Rules v4, both interventions, and Android/macOS restart were verified at `2c0021a`. Approved by the user. |
| 07 | COMPLETE | PENDING | Exact replay hashes match across four runners. Android and macOS restored both timelines and the viewed tick after restart, with schema-v1 saves preserved. |
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
| 2026-11-20 / 03 | Keep rules v1 in shared Kotlin. Apply validated destination commands in sequence order before seeded movement on an integer grid. | SplitMix64 state is explicit. A 52-byte canonical state and FNV-1a 64 digest matched across JVM, Android ART, and Kotlin/Native. Accepted inputs are retained separately for replay. | No |
| 2026-11-20 / 03 | Retain the in-memory world in the shared UI's ViewModel. Pace integer ticks at nominal 20 Hz with 1×, 2×, and 4× rates. | Lifecycle suspension discards elapsed time. The renderer reads immutable poses, and tests matched state at the same tick across all rates, including after a rapid pause and resume. | No |
| 2026-11-20 / 03 | Serialize Android surface idle and wake-up on the UI thread and refresh readiness for each viewport entry. | A rapid reopen after Activity recreation previously stalled on the loading screen. The device test passed twice after the fix, with three verified reopens in each run. | No |
| 2026-11-20 / 04 | Version integer world generation independently. Hash 64×64 terrain, climate, and prop placement separately from mutable state. | The three preset terrains reproduced their digests on all runners. The land masks differed by at least 15% of the union for each pair. | No |
| 2026-11-20 / 04 | Use rules v2 with the generated terrain's largest walkable component and deterministic distance fields. | Movement honors shoreline, slope, and prop occupancy. The v2 canonical state appends worldgen version and terrain digest. The v1 fixtures still pass. | No |
| 2026-11-20 / 04 | Build 16×16 terrain chunks and group the existing GLBs into GPU instances. Keep one in-memory session bound to the chosen seed. | Rendered all three seeds on GLES and Vulkan. Verified same-seed resume, different-seed reset, and scene release. | No |
| 2026-11-20 / 05 | Use rules v3 with integer forage and animal reserves, two herbivore profiles, and a cap of eight per species. Retain the v1 and v2 fixtures. | Both species stayed present at every tick through 10,000 on all three seeds. The paired patch recovered after grazing and was eaten again. | No |
| 2026-11-20 / 05 | Keep urgent food and water goals active through recovery. Rank moist spring candidates by terrain distance from the island center. | The starting rule lost all rabbits on seed 271828 at tick 1356. Hysteresis prevented that collapse. Accessible springs improved survival and yielded births on seeds 104729 and 271828. A lower breeding water threshold alone did not produce births on 271828 and was reverted. | No |
| 2026-11-20 / 05 | Use the Kenney deer and rabbit clips, scene-owned textures, pooled models, and instanced forage tiers. | Android displayed both models and the changing forage total. The Desktop Vulkan probe isolated 82 pixels changed by forage, exercised births and deaths, and reopened the scene to identical pixels at the same tick. | No |
| 2026-11-20 / 06 | Add rules v4 with a deterministic 1,200-tick day and a separate weather PRNG. Rain doubles integer forage regrowth within patch capacity. | A depleted patch reached 22 biomass in dry weather and 24 in rain with identical starting state at tick 299. Rules v1–v3 still pass. Both species persisted for 10,000 ticks on all three preset seeds. | No |
| 2026-11-20 / 06 | Plant traversable browse in stable cell order and supplement the most depleted patches in the selected region. Select B3 by default when eligible. | With seed 104729 at tick 300, B2 planting did not produce a deer bite by tick 900. B3 planted six saplings at tick 301 and deer 5 ate planted browse at (7, -8) by tick 604. Separate intervention runs added 180 browse and 120 supplemental forage to the unchanged control. | No |
| 2026-11-20 / 06 | Save one versioned JSON checkpoint with pending commands, a checksum, and explicit replacement confirmation. | Interrupted-write tests recovered the committed slot. Restart tests compared canonical hashes and applied both queued commands on the first resumed tick. Loading opens paused at 1×. | No |
| 2026-11-20 / 07 | Keep a distinct viewed tick and branch head, and record commands only when the live head advances. Retain eight periodic checkpoints per branch at 300-tick intervals, plus branch starts and heads. | Replaying tick 300 to 600 reproduced the original state. A test also recovered tick 250 after advancing to 3300 and evicting the oldest periodic checkpoints. | No |
| 2026-11-20 / 07 | Fork from the displayed state with independent future commands and preserve both PRNG states. Key creature presentation by branch as well as tick. | Seed 104729 at tick 900 had 9893 forage and zero saplings on Main, and 11105 forage and six saplings on Restored Forest. Switching branches at that tick updated the Vulkan viewport and restored the previous branch's pixels when switching back. | No |
| 2026-11-20 / 07 | Save branch history to schema v2 and retain the Phase 06 file as the fallback when the v2 slot is absent. | macOS restored cursor 700 and both branches at 900. Import preserved the v1 file byte for byte. Damaged v2 data kept the active session intact. | No |

### Latest phase handoff (agent-maintained)

```text
PHASE: 07 - Time Travel & Timeline Branching
STATUS: COMPLETE
PLAYER-VISIBLE OUTCOME: The Timeline panel supports exact seek and playback targets, named forks, and branch switching. A fork lets players intervene at an earlier tick. Saving retains the branches, their checkpoints and inputs, and the branch and tick currently on screen.
IMPLEMENTATION: Shared timeline owner, ordered execution history and replay, lifecycle-bound Compose timeline controls, branch-aware render and inspector updates, and schema-v2 save adapters with a readable and intact schema-v1 fallback.
VERIFICATION:
  PASS - ./gradlew --offline --console=plain :shared:jvmTest :shared:testAndroidHostTest :renderer:jvmTest :renderer:testAndroidHostTest :androidApp:assembleDebug :androidApp:assembleDebugAndroidTest :androidApp:lintDebug :desktopApp:test :desktopApp:createDistributable :shared:compileCommonMainKotlinMetadata :shared:compileKotlinIosArm64 :shared:compileKotlinIosSimulatorArm64 :shared:iosSimulatorArm64Test :shared:packageAndroidDeviceTest
         Shared JVM, Android host, and iOS ARM64 simulator each ran 37 tests. Renderer Desktop JVM (8), renderer Android host (4), and Desktop storage (1) passed. Android lint reported zero errors and seven warnings.
  PASS - Install the APK and both test APKs with adb install -r. Run adb -s emulator-5554 shell am instrument -w -r with id.branchworld.game.shared.test/androidx.test.runner.AndroidJUnitRunner and id.branchworld.game.test/androidx.test.runner.AndroidJUnitRunner.
         Shared Android ART (36) and Android app instrumentation (2) passed on the Pixel 9 API 36 emulator.
  PASS - python3 build/verification/phase07/android-final-restore.py
         The final APK reopened both saved branches at child tick 700 with hash b558845d62318515. Both save files remained unchanged.
  PASS - python3 build/verification/phase07/android-timeline.py
         Loaded the existing v1 save at tick 357 and kept its original 8355 bytes. Replayed the main timeline from 600 to 300 to 600, forked at 300, and advanced both branches to 900. The B3 inspector showed 2217 forage and zero saplings on Main, and 3496 forage and six saplings on Restored Forest. Restored the child at tick 700 after force-stop and relaunch, opened both tick-900 heads, and resumed to tick 901.
  PASS - open -n desktopApp/build/compose/binaries/main/app/id.branchworld.game.app
         The native bundle launches. DesktopTimelineProbe ran the packaged Compose UI and storage adapter in separate processes. It verified the exact replay, loaded the child at tick 700, reopened both heads at 900, and advanced to 901. The v1 import preserved its original bytes. A corrupt v2 file left the current island intact.
  PASS - DesktopTimelineRenderProbe against the packaged renderer on Apple M4 Vulkan.
         At the same tick and camera, the child changed 651 pixels. Creature-only changes accounted for 537 pixels in an isolated comparison. Reassigning a reused entity ID to a different species changed 136 pixels. Switching back restored identical parent pixels each time.
  NOT RUN - Manual pointer and keyboard input in the packaged macOS window, physical Android, Android API 26, Windows and Linux Desktop, and iOS rendering.
DETERMINISM / SAVE / BRANCH: The ordered-command fixture for seed 104729 reproduced b8900e6fd6de6c0d after replay from tick 300 to 600 on JVM, Android host, Android ART and iOS simulator. The same runner matrix matched all three seeds and both North Star branch heads. At tick 900, Main hash 689a2ab6f9063004 had 9893 total forage and zero saplings. Restored Forest hash 6af1190dbe1f4a7f had 11105 total forage and six saplings. Each had four rabbits and three deer. Android and macOS wrote byte-identical v2 files and restored the same child view hash b558845d62318515 at tick 700. Tests covered nested forks, checkpoint eviction, pending inputs, malformed ancestry, duplicate commands, damaged saves, and v1 import.
PERFORMANCE: The 75147-byte, two-branch file restored in 613 ms on the Pixel 9 API 36 emulator and 441 ms on Apple M4 macOS. Exact seeks to checkpoint 300 took 67–249 ms on that emulator and 7–13 ms on the Mac in the end-to-end runs.
DEMO: Open seed 104729. Play to 600, go to 300, then play to 600. Go to 300 again and fork Restored Forest. In Inspect world, choose B3 and reforest, then play to 900. Switch to Main and play to 900. Inspect both heads, switch to Restored Forest, go to 700, save, quit, and load. The child opens at 700 and both branch heads remain at 900.
LIMITATIONS: Imported Phase 06 saves start their recorded history at the saved tick. Inner checkpoints retain the eight most recent 300-tick intervals per branch. Older seeks rebuild from the branch start and may take longer.
DECISIONS: One visible save slot uses island-v2.json with a read-only fallback to island-v1.json. Parent and child share immutable fork state and have independent future inputs. The world admits at most 16 branches and 16384 ordered commands across the session.
EVIDENCE: build/verification/phase07/verified.log, cross-target-fixtures.txt, cross-platform-save.txt, android-timeline.log, android-final-instrumentation.log, android-final-restore.log, desktop-timeline-final.log, desktop-vulkan-final.log, driver sources, and device captures.
NEXT STEPS: After approval, build the Phase 08 timeline tree, scrubber, and intervention indicators from the persisted branch graph and command history. Use timeline replay to show both branches at the same tick and compare real vegetation, forage, wildlife, and event data. Re-run the seed 104729 demo through restart.
APPROVAL NEEDED: yes
```

## How to invoke the coding agent

For the first session: **"Read AGENTS.md and PLAN.md. Inspect the repository, then execute Phase 01 end-to-end. Run real checks, update the Progress Ledger in PLAN.md, and stop for my approval."**

For subsequent sessions: **"Execute Phase NN according to AGENTS.md and PLAN.md. Do not advance beyond this phase without my approval."**

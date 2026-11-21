# BranchWorld — Grand Vision & 10-Phase Master Plan

**Tagline:** *Shape a world. Change its fate. Discover what could have been.*

**Purpose:** This file defines **WHAT** BranchWorld must become, what players should experience, the 10 major delivery outcomes, concrete acceptance gates, and the persistent progress record. `AGENTS.md` defines **HOW** autonomous agents work.

**Development philosophy:** **Maximum quality and originality. No invented development schedule, no artificial feature ceiling, and no contest-only shortcut.** Pursue a standout premium game, integrate it steadily, and refuse shallow or visually weak results. This is ambition with real engineering evidence, not a promise that every idea can be built immediately.

**Context:** The Kotlin Multiplatform Contest remains a key standard of excellence (novelty 40%, genuine KMP utilization/shared Kotlin 40%, coding conventions 20%). The competition has its own external submission requirements; **this product plan does not use a deadline to cap the game's ambitions.** Verify contest-specific information from https://kotlinconf.com/contest-rules/ when relevant.

**Implementation honesty:** The supplied screenshots show a sparse menu, a very small distant 3D island, debug-like animal commands and raw stats. They are evidence of a disappointing player-facing result, **not proof** of what simulation, persistence, timeline, rendering, Android or Desktop code works. The coding agent must audit the actual repository before modifying systems or marking milestones complete.

---

## 1. The game we are building

### High concept

**BranchWorld is a premium, single-player, visually captivating, three-dimensional world-shaping strategy game about fragile ecosystems, emerging societies, and alternate histories.** Players become Keepers of living miniature worlds. They explore landscapes and their inhabitants, make consequential interventions, witness unintended ripple effects, rewind a pivotal choice, and discover what another future would have looked like.

The player is not primarily a manager of numerical resources. They are an explorer of consequences: **"What if we saved the forest? What if the river changed course? What if the village never settled here?"**

The game's signature innovation is **playable counterfactual history**: two independently simulated futures derived from the same past, with a beautifully integrated way to inspect the difference. The same rules generate both outcomes. The game never replaces real consequences with fictional charts or cosmetic branch effects.

### Premium promise

A player should want to launch BranchWorld because:

1. **The world is breathtaking.** At a glance it resembles a carefully art-directed living storybook diorama, not a debugging viewport.
2. **Life is worth caring about.** A herd looks for safety, a fisher watches the river, a village struggles through a dry season. Changes are local, human-scale, and visible.
3. **Their actions matter.** Restoring a wetland can save wildlife but reduce farmland; a rainstorm may rescue a harvest but bring erosion. Tradeoffs emerge from systems.
4. **Time is expressive.** Rewind and compare decisions without erasing previous futures. Players can preserve, revisit, and inspect their own histories.
5. **Every world tells a different story.** Procedural geography, climate, relationships, ecology, settlements and player choices create varied but comprehensible outcomes.
6. **There is a journey.** Crafted scenarios, discoverable possibilities, a living world atlas, unlockable tools, achievements, and optional sandbox mode offer purpose and replay value.

### What it is NOT

- A screensaver with a small floating island and a collection of counters.
- A tech demo whose primary controls are "Send bunny left", raw ticks, and diagnostics.
- A menu-heavy idle simulator where the world is merely decorative.
- A random generator with no coherent ecological consequences.
- A heavily scripted story where outcomes claim to be simulated but are predetermined.
- A feature checklist built at the expense of beauty, responsiveness, or emotional connection.
- A clone of an existing ecological strategy, diorama builder, or settlement simulation.

### Inspiration without imitation

Study why these experiences work: [Tiny Glade](https://store.steampowered.com/app/2198150/Tiny_Glade/) for scene intimacy and satisfying visual craftsmanship; [Terra Nil](https://store.steampowered.com/app/1593030/Terra_Nil/) for legible environmental transformation; [Reus 2](https://store.steampowered.com/app/1875060/Reus_2/) for intertwined ecosystems and society-level decisions. Borrow *design lessons*, never specific art, interfaces, mechanics, marketing, or identities. **BranchWorld's differentiator is seeing, inhabiting, and comparing alternative futures.**

---

## 2. The irresistible player loop

**Discover -> Understand -> Shape -> Witness -> Rewind -> Compare -> Choose -> Remember -> Discover again.**

| Beat | Player experience | Required in-game manifestation |
|---|---|---|
| **Discover** | Explore a gorgeous world already full of life | Orbiting cinematic camera, animated fauna, readable settlements, environmental storytelling |
| **Understand** | Notice an opportunity or an emerging threat | A world-local event, character reaction, visible habitat change and optional context |
| **Shape** | Use a world-shaping tool thoughtfully | Clear target/preview, costs/tradeoffs, touch/mouse affordances, satisfying action feedback |
| **Witness** | Watch consequences develop | Growth, migration, river changes, building activity, weather, human-readable events |
| **Rewind** | Return to a meaningful earlier decision | Rich timeline navigation, reconstruction and smooth transition; previous branch preserved |
| **Compare** | Reveal which outcomes changed | Matched-time synchronized views, world-space Echo Lens overlays, causal event trails and metrics |
| **Choose** | Adopt one future or continue exploring | Preserve branch, keep both, branch again, bookmark a turning point |
| **Remember** | Build a personal history of worlds | Chronicle, map memories, outcomes, unlocks and saved alternate branches |

**Principle:** Even if a player ignores branching for a while, the living world and its inhabitants must remain worth watching and shaping. But branching must ultimately provide the signature feeling no conventional ecological strategy game can offer as directly.

### Intervention design — meaningful verbs, not debug commands

Candidate world-shaping powers, to be prototyped and balanced through general rules:

| Power | Immediate expression | Potential simulated consequence / tradeoff |
|---|---|---|
| **Restore Grove** | New saplings, spreading roots and canopy | Slow soil recovery, shelter and forage, consumes finite seeds/effort or displaces open habitat |
| **Guide Water** | Visible stream path or wetland restoration | Better downstream water access, altered flood risk and available farmland |
| **Call Rain** | Cloud build-up and rainfall | Fast moisture relief; excessive rain can cause runoff/erosion where terrain warrants |
| **Protect Habitat** | Mark a refuge with visible environmental feedback | Reduced disturbance, possible settlement resource constraints |
| **Reintroduce Wildlife** | Herd arrives and begins exploring | Biodiversity benefits but competition or grazing pressure may increase |
| **Shape the Land** | Terrain modification with clear preview | New waterways/habitat corridors, potential instability, changing access paths |

Do not force every power into the first implementation. Each power must be better than an alternative **in some conditions and worse in others** for reasons players can understand. All consequences must flow through tick-indexed shared Kotlin commands.

### Depth without tedious micromanagement

Players influence **systems**, not the manually controlled movement of individual rabbits. They can inspect and follow a creature, but wild animals must make their own decisions. The best strategic choices have delayed, interpretable ripple effects, not merely positive/negative progress bars.

### Failure and recovery

Worlds are allowed to struggle, decline, recover, and change. Avoid binary punishment whenever possible. Failure should create a story, a new question, or a chance to explore an alternative strategy. The player keeps access to previous histories and may branch again.

---

## 3. A distinctive art and presentation system

### World composition

The opening must be a **hero image generated by the actual game renderer**. Most of the play viewport is occupied by layered, readable landscape: near-field canopy and rocks, midground habitats and paths, a focal river or settlement, background hills, lively water and atmospheric depth. Use a composed isometric-leaning perspective that can smoothly transition to close creature tracking and broader survey views.

**Required redesign from current screenshots:**

- Remove the large blank home screen and the tiny island in a broad dark rectangle. Launch into a live world, using an unobtrusive overlay for Continue / Begin Story / Atlas / Settings.
- Treat world scale, zoom, camera focal length, terrain composition and viewport layout as first-class art problems. As an initial review heuristic, the meaningful 3D world should occupy roughly **two-thirds or more** of the usable play frame in the standard hero shot; adjust based on composition and platform, not an inflexible number.
- Hide diagnostic controls and tick counters by default. Replace them with a concise objective, contextual event notification, a thoughtfully designed action bar, world-time affordance, and an accessible inspect mode.
- Use close-up creature and settlement moments to create empathy; avoid uniformly distant top-down observation.
- Animate **the world itself**: leaf movement, grass, smoke, river ripples, birds, animal gait, seasonal changes, lamps at twilight, rain hitting terrain, terrain-color response to moisture.

### Art language

- **Visual language:** warm storybook low-poly forms, strong silhouettes, tasteful painted materials, lush vegetation variation, soft but directional lighting, rich water treatment, readable atmospheric perspective. Cohesive rather than photorealistic.
- **Palette:** vivid but controlled biome-specific schemes. For instance, forest jade/moss and sunlit honey, shallow-water teal, weathered stone, warm village wood, twilight indigo and gold. Lighting and weather must alter tone without losing readability.
- **Camera:** graceful orbit, pan, zoom, click-to-focus, follow-animal, cinematic reveal, branch-transition alignments, safe bounds, smooth touch/mouse input and adjustable motion intensity.
- **Assets:** use the downloaded KayKit/Kenney packs as material; normalize scale/rotation/pivots and unify their look through custom material/shading, mesh placement, foliage variation and animation. Select better-suited assets or author supporting geometry when existing packs cannot meet the target.
- **Sound:** spatial wind, water, birds, wildlife calls, settlement ambience and restrained music that reacts to season, threat and resolution. Interaction sounds and haptics where platform support allows.
- **UI:** minimal, elegant, responsive Compose UI with clear type hierarchy, unobtrusive transitions and world-aware placement; no developer-dashboard identity.

### Non-negotiable image-quality test

Capture authentic game screenshots at representative Desktop wide/16:9 and Android sizes under: **sunny day, dusk, rain or crisis, a close-up creature, and a branch comparison.** Review clarity at thumbnail size and up close. Any phase modifying graphics must retain good framing and avoid asset popping, clipping, broken shadows, unreadable silhouettes, or visual clutter. A static concept illustration is not evidence of in-engine quality.

---

## 4. The first truly memorable world-story

### Chapter One — The Fading Grove

**Setting:** A valley of luminous forests, a river bend, shallow wetlands, small farms and a timber village named **Hollowmere**. Deer and rabbits move through meadows, birds cross the sky, families gather near the water. The scene feels beautiful before anything goes wrong.

**Premise:** A dry season begins earlier than expected. The river shallows; grazing land shrinks; villagers worry about harvests, and wildlife searches for water. The player can make deliberate interventions, but none simply restores everything for free.

**Candidate choices:**

- **Restore the upper grove:** slow-growing moisture retention, habitat improvement and delayed benefits, but limited immediate relief.
- **Guide the river into the old wetlands:** quickly creates water access and habitat, but redirects water away from other uses and reshapes flood exposure.
- **Call seasonal rain:** immediate water and growth stimulus, with runoff/erosion or downstream risk if the world conditions create them.

**Emergent consequences:** The same underlying hydrology, vegetation, creature-needs, and settlement resource rules drive all outcomes. A rainy season may rescue fields or damage slopes; a restored grove may help food webs later; a new wetland may support animals at the expense of one farm. Do not preassign success/failure to each power.

**Emotional anchors:** Give the world a small cast of observable persistent entities or communities: an identified deer herd, a river fisherman, a named village, or a local ranger. Characters do not need expansive dialogue; reactions, animation, movement and concise event text can make their lives meaningful. The Chronicle should tell the truth about events that actually occurred.

**Chapter climax:** The player witnesses the outcome, rewinds to the warning, tries another intervention, and sees a **Living Echo**: foliage or paths from one future overlaid against the other, synchronized cameras, population and river differences, and an event chronology explaining how each unfolded.

**Closure:** Players may keep both futures, choose which to revisit, bookmark a turning point, and unlock another world-story or sandbox possibility. A history should remain accessible; do not delete a discarded branch without explicit user action.

**Playable proof:** An independent tester can experience the conflict and compare different futures using normal controls, without opening diagnostics, inspecting code, or receiving a live developer explanation. The exact effects are reproducible from a documented seed and command sequence; no scripted fake statistic is accepted.

---

## 5. World systems and how they connect

BranchWorld's depth comes from **interacting systems**. Never build each subsystem as a disconnected collection of menu toggles.

```text
Procedural terrain + climate + water circulation
                  |
                  v
       Soil moisture + biome suitability
                  |
                  v
     Plants + food + shelter + regeneration
                  |
                  v
   Herbivores + predators + migration + life
                  |
                  v
    Settlement resources + labor + development
                  |
                  v
       Local events + remembered history
                  |
                  v
      Branch-specific alternate outcomes
```

### Authoritative shared Kotlin model

Suggested logical interfaces (names are illustrative, not proof these classes exist):

- `WorldSeed`, `WorldRulesetVersion`, `WorldState`, `TickIndex` and stable entity IDs.
- `WorldCommand` for all user interventions and simulation commands, with explicit tick and ordering.
- `SimulationEngine.step(state, commands)` as the sole authoritative state advancement path.
- `WorldGenerator` for terrain, biomes, water networks, spatial resources and spawn records.
- `WorldEvent` and a reliable event chronology with recorded causes where actually known.
- `WorldSnapshot` and read-only `WorldView`/`SceneProjection` for the UI/render adapter.
- `TimelineBranch`, `SnapshotStore`, `ReplayEngine` and branch-specific PRNG state for versioned histories.
- `SaveRepository` shared schema behind Android/Desktop storage adapters.

**Hard invariant:** Simulation rules never depend on Kool scene nodes, Compose recomposition timing, wall-clock elapsed time or device scheduling. Do not call a procedural renderer state "deterministic" unless cross-target replay fixtures actually agree.

### Deterministic simulation is a feature

The same initial seed, ruleset, command sequence and simulation tick must recreate equivalent authoritative state and a stable canonical state hash. Deterministic state makes world comparison, shareable histories, user trust, troubleshooting and portable saves possible. Quantize/fix numeric computations where required for target parity.

### Scalable world representation

Design terrain chunks, spatial indexing, instanced vegetation, animation update budgets, camera-based LOD, culling and aggregate distant simulation carefully. A world can *appear* vast while detailed simulation concentrates near the player; far-away systems may use deterministic aggregate models that yield compatible results when brought into focus. Avoid changing authoritative outcomes based on GPU framerate.

---

## 6. Ten major development phases (outcome-driven, not timeboxed)

Phases are milestones with **observable player value**, not tiny tickets. The Lead Architect generates internal implementation tasks and delegates safely. Foundational tests, art, camera, input, sound, saves and performance work are continuous across every phase; they do not wait until Phase 10. A verified vertical slice should remain playable as systems grow.

### PHASE 01 — Experience Reconstruction & Art Direction

**Player milestone:** Opening BranchWorld instantly presents a beautiful, interactive living world instead of an empty technical application.

**Build:** Audit the actual existing KMP project and keep proven working simulation, renderer, assets and saves. Replace the menu-first presentation with a live 3D hero world and elegant overlay. Recompose the viewport so the landscape is large, layered and readable. Establish the project's visual identity and coherent interface components, beautiful camera controls and player-oriented interactions. Relocate debug controls to an opt-in diagnostics mode. Introduce a clear chapter/location identity and one visible world concern. Verify the first render on Desktop and Android rather than trusting only the desktop screenshot.

**Craft focus:** The opening screenshot must be attractive before the player knows any technical details. The player can orbit/zoom, inspect a creature, understand what the world is, and start the story. Use convincing real-world geometry and animation; polish the focal camera position and scene scale.

**Gate:** Real captured before/after screenshots show substantial improvement, the playable world occupies the visual center, menu and HUD no longer dominate, camera/input works, and the agent can demonstrate exactly how to launch both targets or report truthful platform blockers. The current "tiny island" appearance is unacceptable.

### PHASE 02 — Breathtaking 3D World & Responsive Presentation

**Player milestone:** The world feels like a premium, handcrafted storybook diorama at every reasonable camera distance.

**Build:** Validate the chosen rendering adapter on Android and Desktop, including Compose surface interop, camera gestures, scene disposal and lifecycles. Implement a polished terrain mesh/water shoreline, biomes in presentation, varied trees/rocks, animated animals and vegetation, shadow/lighting strategy, atmosphere, time-of-day tone, rain, fog, VFX and audio ambience. Unify imported assets with carefully designed materials, pivots and scale. Add camera tracking and a small amount of world-local storytelling animation. Instrument asset loads, memory, draw calls, frame time and input latency; optimize the measured bottleneck instead of guessing.

**Craft focus:** The scene must remain delightful in a still screenshot, become more captivating in motion, and remain readable on Android. Adjust visual richness by device tier without changing simulation truth.

**Gate:** Real gameplay captures demonstrate good composition in sunlight, dusk and rain, from overview and close-up. Imported models/animations work where source data supports them, Compose controls remain responsive, and load/lifecycle/performance behavior is measured on available hardware.

### PHASE 03 — Rich Procedural Geography & Living Environments

**Player milestone:** Each generated world feels like a distinct believable place rather than a random scattering of assets.

**Build:** Develop seed/ruleset-versioned island geometry, coastlines, elevation, slopes, land/water boundaries, soil/moisture, climate, biome transitions, rivers/lakes/wetlands, forest patterns, rock placement, navigable clearings and resource maps. Support natural landmarks and spatial storytelling. Connect generation to rendering and ecological inputs through shared Kotlin authoritative data. Use appropriate terrain chunks and placement/instancing. Add environmental cycles—day/night, seasons, storms, drought, fire risk where designed—with rules and visuals separated correctly.

**Craft focus:** Different seeds should produce visually varied but composed, explorable islands. Cohesion and compelling landmarks matter more than raw noise complexity or sheer map size.

**Gate:** A seed gallery demonstrates multiple genuinely different, attractive worlds; repeated seed/ruleset reproduces identical authoritative geography; terrain supports credible creature movement, local hydrology and future settlements; measured worldgen and scene performance is documented.

### PHASE 04 — Believable Creatures, Habitats & Ecosystem Feedback

**Player milestone:** Players can follow and care about creatures while ecology evolves for understandable reasons.

**Build:** Model plant biomass, food, shelter, habitat, water, regrowth, resource depletion, carrying capacity and migration pressure. Implement wildlife sensory/decision systems, needs, movement, terrain navigation, predator/prey or competition dynamics where appropriate, reproduction, age and mortality. Use autonomous Kotlin rules; derive 3D animation from simulated actions. Add creature follow/inspect, subtle behavior cues and event reporting. Develop at least several interacting loops—forest-water-herbivore, predator-prey, village-resource—even if the representative chapter uses a subset.

**Craft focus:** Avoid wandering entities that all behave alike. Give visible groups habits, distinctive movement/animation and enough continuity for players to recognize them over time.

**Gate:** In a documented fixed-seed scenario, changes in moisture, vegetation or predator pressure alter creature decisions and population trajectories. Both visual and numerical outcomes follow actual state. Long-run fixtures detect instability; save/replay consistency holds across targets where verified.

### PHASE 05 — Expressive World-Shaping Powers & Strategic Consequences

**Player milestone:** Changing the world feels wonderful in the moment and strategically significant over time.

**Build:** Implement the intervention framework with command validation, targeting/preview, costs or limited opportunities as appropriate, satisfying animations/sound, contextual explanations and reversible UX mistakes. Prototype Restore Grove, Guide Water, Call Rain, Protect Habitat, and carefully chosen later powers. Wire actions into hydrology, soil, vegetation, wildlife and settlement effects. Design conflicts with multiple legitimate strategies, forecasts that communicate uncertainty, and world events that surface consequences without overwhelming the HUD. Develop the first complete dilemma in The Fading Grove, including failure and recovery. For this initial story, implement a compact, real settlement-welfare model (population, food/water needs and visible reactions) before the deeper village simulation in Phase 07; avoid placeholder villagers or manufactured outcome text.

**Craft focus:** The player should feel agency but not omniscience. One well-composed ecological intervention that dramatically changes the living scene is worth many shallow tools.

**Gate:** Multiple player approaches lead to measurably and visibly different simulated results under identical starting conditions. UX is understandable without developer labels; side effects are traceable; there are no purely cosmetic "powers" masquerading as real state changes.

### PHASE 06 — Time Weaving, Alternate Futures & the Living Echo

**Player milestone:** BranchWorld delivers its unmistakable signature: rewind a real choice and *see another possible world*.

**Build:** Implement/complete stable snapshots, command/event logs, deterministic replay, branch ancestry, branch-local mutable state and PRNG, versioned persistence and integrity checks. Add tactile time-scrubbing, meaningful turning-point markers, branch creation/naming and a graceful transition across worlds. Build an extraordinary matched-time comparison experience: aligned 3D camera views; side-by-side or fast-switch modes; **Living Echo** spatial overlays highlighting changed forests, river paths, habitats and settlements; event chronology and real state metrics. Offer a way to keep both histories, inspect them, compare again and revisit older decisions.

**Craft focus:** The player should understand changed outcomes emotionally and spatially before needing to open a chart. Use measured changes and recorded events—never fake a cause chain or predetermined ending.

**Gate:** An agent can reproduce a documented scenario: from the same ancestor, fork two branches, choose two different interventions, simulate to a matched tick, display authentic world-state differences, then save/restart/reload/reconstruct both branches with matching canonical hashes. Parent and child remain strictly isolated. A new player can do this through normal controls.

### PHASE 07 — Settlements, Civilization & Emergent History

**Player milestone:** Worlds develop inhabited stories as settlements emerge, adapt, flourish, migrate or collapse.

**Build:** Extend the smaller Phase 05 settlement-welfare model into resource-conscious settlement founding, villager/cohort needs, food, shelter, labor, agriculture, forestry, paths, construction, storage, and responses to drought, wildlife and environmental change. Populate with selected medieval buildings and inhabitants from asset packs. Support recognizable village life and animations (working, gathering, traveling, nightfall), historical milestones and relationships where they create meaningful decisions. Use deterministic scalable simulation; aggregate distant population if appropriate. Integrate civilization events, structures and resources into rewind/branch/save/compare. Add the human-scale Chronicle of consequential events.

**Craft focus:** A village is not a numeric population counter. Its growth and hardships must be *seen* in buildings, movement, land use and character-level moments.

**Gate:** Players can witness a settlement's visible development and create a reproducible ecological-to-societal divergence: the same past intervention changes future settlement welfare, construction or location. The Chronicle and Living Echo accurately represent recorded history.

### PHASE 08 — World Atlas, Crafted Stories & Meaningful Progression

**Player milestone:** A finished story leads naturally to curiosity about another world; sandbox remains a joyful alternative.

**Build:** Build a world atlas/map of discovered islands, save slots and branching histories, chapter progression and memorable discoveries. Create a designed anthology of distinct ecological/historical dilemmas that use systemic rules differently: **The Fading Grove** (drought/forest), **Ember Season** (fire/regeneration), **The Last Crossing** (wildlife corridor and human expansion), **The River Remembers** (water flow and agriculture), and later procedurally remixed events. Distinct worlds should offer different landscape composition, characters, powers, hazards, tradeoffs and closing stories. Add optional sandbox mode, photo mode, replay bookmarks, progression rewards that broaden expression, and approachable tutorials.

**Craft focus:** Each chapter should have a strong dramatic premise, an environmental identity and a memorable difference from previous worlds. Do not turn unlocks into grinding or force one correct outcome.

**Gate:** Multiple substantial world-stories can be played, replayed and meaningfully compared; progression and history persist; new players understand what to do next; the sandbox supports creative exploration without breaking deterministic world rules.

### PHASE 09 — Advanced Living Worlds, Scale & Discovery

**Player milestone:** BranchWorld rewards long-term curiosity with surprising interactions and deep systems rather than repeating one formula.

**Build:** Expand ecology/civilization complexity where it produces new player stories: species diversity and niches, seasonal migration, storms/drought/fire/flood, ecosystem succession, fisheries, terrain transformation, trade/resource webs, long-term settlement development, cultural priorities, multi-island connections and generation-spanning change. Add robust world history browsing, event bookmarks, causality-aware explanations grounded in actual events, sophisticated Living Echo visualization, and shareable world seeds/branch descriptors. Explore larger maps, archipelagos or cross-world atlases only when they remain visually and computationally coherent. Use spatial partitioning, instancing, LOD, streaming, deterministic aggregate simulation and measured CPU/memory budgets.

**Craft focus:** Complexity should produce better choices and richer surprises, not invisible background calculations. Add advanced systems only when their impact can be recognized through visuals, narrative and meaningful decisions.

**Gate:** Stress-tested large/deep scenarios remain reproducible, save-safe and intelligible; richer systems materially change player strategy and create memorable events. Profiling proves scalability on representative hardware; scope expansion does not regress the polished core loop.

### PHASE 10 — Masterpiece-Level Craft, QA & Publication

**Player milestone:** The whole experience feels intentionally designed, stable, immersive and recommendable—not like an unfinished engineering project.

**Build:** Apply cohesive art/sound direction across every scene, animation, lighting state and interaction. Perfect the opening, camera, world feedback, time transitions, information architecture and endings. Balance scenarios with real playtests and field recordings of confusion, delight and replay intent; iterate on weak decisions. Ensure accessibility, settings, input parity, localization-ready text, save repair/migration, clean installs, reliable recovery, performance profiles and platform-native behavior. Develop a compelling English README, screenshots/recorded real gameplay, clear architecture/demo narrative, release builds, and contest materials if applicable. Evaluate future platform ports (iOS/Web) based on actual renderer support rather than assumptions. For commercial distribution, research market interest, platform fit, discoverability, pricing and store presentation without inventing demand.

**Craft focus:** The final experience is judged holistically: the world looks beautiful, actions are engaging, other futures are surprising, and the software is trustworthy.

**Gate:** Independent testers can learn, complete and replay the core journey without developer coaching; recorded feedback supports improvements. Verified Android and Desktop targets run the same meaningful shared-Kotlin gameplay. No known critical save corruption or branch-contamination defects remain; performance is measured on named devices; screenshots and demonstration footage are authentic; the repository is inspectable and conventions are maintained.

---

## 7. The competition advantage (without a date-driven scope ceiling)

The official Kotlin Multiplatform Contest judging criteria are **creativity/novelty 40%, KMP use/shared Kotlin 40%, Kotlin conventions 20%**. Optimize the *substance* of the project for these criteria while still aiming at a remarkable commercial-quality game.

| Contest dimension | BranchWorld's convincing proof |
|---|---|
| **Creativity / novelty** | A living, emotionally meaningful 3D world; choice-driven ecological and social ripple effects; actual counterfactual histories; in-world Living Echo comparisons |
| **Meaningful KMP engineering** | Shared deterministic simulation, worldgen, ecology, settlement systems, commands, snapshots/replay, save schemas, chronology and scenario test fixtures; real Android + Desktop operation |
| **Idiomatic Kotlin craft** | Clear domain APIs, Kotlin conventions, focused modules, reproducible tests, safe state transitions, appropriate Flow/coroutines, portable models and honest architecture documentation |

The most persuasive technical presentation is a **single real gameplay demonstration** whose behavior can be reproduced on both platforms with the same world seed, ordered intervention log, tick count and measured state. It shows why shared Kotlin matters instead of just stating a percentage.

External contest requirements (eligibility, allowed repository license, README, essay and entry mechanism) still must be checked if an actual submission is made. Do not imply that the competition has no closing date; this plan simply avoids using that date to constrain the full game's design.

---

## 8. Cross-phase acceptance and research program

### Five proofs of excellence

| Proof | Verify by | Disqualifying weakness |
|---|---|---|
| **First-frame beauty** | Authentic screenshot/capture on target screen sizes, compared to curated art-quality references | Empty UI, tiny world, asset pile without lighting/material cohesion |
| **Delightful interaction** | Live controller/mouse/touch walkthrough, feedback latency/clarity review | Debug actions, confusing target selection, no immediate response |
| **Real emergent consequence** | Fixed-seed tests of intervention -> system changes -> visual outcomes | Scripted fake outcomes, untraceable numbers, no alternate strategies |
| **Trustworthy alternate histories** | Exact command replay, branch isolation and save/restart tests | Parent changes after fork, mismatched state or nonrepeatable scenario |
| **Player desire to return** | Independent play sessions, recorded confusion/delight/voluntary replay, later market research | Agent declares fun/sellability without evidence |

### Design experiments

Choose experiments that directly improve the game, not random benchmarking exercises:

- **Camera & composition:** Different orbit centers, FOV, island scale and water/terrain framing assessed via authentic side-by-side screenshots.
- **Art direction:** Lighting rig, palette, cloud/fog, material treatments and vegetation density assessed for beauty and readability.
- **Ecology & balance:** Same-seed drought/rain/forest scenarios; compare recovery, overgrazing, settlement welfare and player-perceived fairness.
- **Timeline UI:** Compare spatial overlay, synchronized split view, and quick-switch history; test whether first-time players can identify what changed and why.
- **Performance:** With identical scenes, measure frame-time distributions, GPU/draw budgets, memory and startup/load on available named devices.
- **Desirability:** Blind playtest observation, voluntary replay, scenario-completion friction and later informed willingness-to-pay research. None are assumed successful beforehand.

Log experiments in the ledger near the end of this file. Keep or revert based on evidence; do not declare visual quality through FPS alone.

### Performance and platform truth

Optimize for smooth interaction on a nominated Desktop test machine and representative Android devices. Initial aspirations may include ~60 FPS Desktop and ~30+ FPS on representative Android hardware, but these are **provisional goals**, not guaranteed facts. Use frame-time budgets, responsiveness, memory growth, lifecycle reliability and actual device classes as the evidence. Scale visual settings without altering deterministic simulation.

### Source asset inventory (known from planning; verify in repository)

- KayKit Forest Nature — foliage and environmental props
- KayKit Medieval Hexagon — settlement structures
- KayKit Adventurers — inhabitants
- KayKit Character Animations — animations
- Kenney Nature Kit — additional landscape props
- Kenney Cube Pets — stylized animals
- Kenney Interface Sounds — interface feedback
- Cute Pets & Wildlife Free — candidate animated wildlife; archive contents not yet verified here

Source archives may live in `assets/{animals,audio,buildings,characters,environment}/`. Optimize selected runtime assets for the real KMP module's resource system; do not assume contents without inspecting files.

---

## 9. The immediate transformation — what the next coding session must do

**The screenshots are a useful baseline, not an acceptable destination.** Do not postpone the experience rescue to a later polish phase.

1. **Inspect and preserve reality:** Read both root documents, check real code/Git/builds and installed Skills, identify which systems are functional and which are placeholders, and save the result below.
2. **Rebuild the opening composition:** Reframe the 3D island so the world—not the UI or empty viewport—commands attention. Improve environmental density, lighting and the focal camera. Use actual runtime screenshots to compare before/after.
3. **Replace the player-facing dashboard:** A living world with a clear local concern, one world-focused action, minimal purposeful HUD and simple inspection. Hide `Send bunny left/right`, raw diagnostic stats and developer buttons from normal play.
4. **Give the world a pulse:** Movement, foliage/water motion, audio and at least one evolving ecological state connected to what is shown on screen. If any system does not exist yet, implement the smallest coherent real path without faking it.
5. **Validate across targets and hand off:** Run available tests and Desktop/Android checks, inspect actual visuals, note unknowns and failure modes, update status below and stop for human acceptance at the Phase 01 gate.

**Starter instruction to agent:**

> Read `AGENTS.md` and `PLAN.md` entirely. Inspect the actual BranchWorld repository, Git status, installed Skills and builds. Resume the active phase; if none is verified, execute Phase 01. Your objective is a remarkable world-first, player-centered game—not a prototype dashboard. Preserve proven code, implement real gameplay/visual changes, capture real screenshots, test both KMP targets, and iterate until the player, visual and engineering gates are met. Keep `PLAN.md` current for seamless continuation and request approval before moving to the next phase.

---

## 10. Persistent progress record (AI-agent maintained)

The evidence below follows the current ten-phase plan. Earlier implementation commits and captures document working systems in the previous plan. The current phase gates require a separate player-facing review and explicit human acceptance.

### Current State

**Active phase:** 01 — Experience Reconstruction & Art Direction. The live Fading Grove opening, viewport, first intervention, and inspection flow are implemented. Both launch targets have rendered the reconstructed world. Human acceptance is pending.

**Verified foundations:** Android GLES and Desktop Vulkan render the same shared world. Shared tests cover seed generation, ecology, settlements, interventions, replay, branch isolation, and save compatibility. The persistent schema and ruleset versions stayed unchanged in this phase. The shared iOS simulator target compiles, and the iOS shell remains outside the 3D launch gate.

**Player experience:** The opening looks into seed `104729` before time starts. Beginning the story keeps the world in place and starts its clock. Restore grove highlights a region and plants through the existing shared command. Inspect follows a creature by ID and exposes its current activity. Timeline, save, and diagnostics are available through the in-world controls.

### Phase Ledger

| Phase | Engineering status | Player / visual quality | Evidence | Human acceptance |
|---|---|---|---|---|
| 01 | COMPLETE | World-first opening and intervention reviewed on Android and Desktop | See Latest Handoff | PENDING |
| 02 | PARTIAL | Current renderer, input, scenery, lighting, and rain verified | Android device tests and Desktop Vulkan captures | PENDING |
| 03 | PARTIAL | Existing shared terrain, biomes, and traversability | `IslandGeneratorTest`, terrain replay fixtures | PENDING |
| 04 | PARTIAL | Creatures respond and can be inspected in the scene | Shared ecology tests and Android walkthrough | PENDING |
| 05 | PARTIAL | Regional reforestation and forage supplementation are available | Environmental rules, pending/applied tests, and new grove workflow | PENDING |
| 06 | PARTIAL | Matched-time history switches in a single viewport | `TimelineTest`, save codecs, Desktop comparison test and Vulkan captures | PENDING |
| 07 | PARTIAL | Settlement development renders in the existing world | Settlement fixtures and seed `104729` captures | PENDING |
| 08 | NOT VERIFIED | NOT VERIFIED | Independent gate review pending | PENDING |
| 09 | NOT VERIFIED | NOT VERIFIED | Independent gate review pending | PENDING |
| 10 | NOT VERIFIED | NOT VERIFIED | Independent gate review pending | PENDING |

### Decision Log

| Decision | Rationale | Status |
|---|---|---|
| Maximize premium game quality; do not run a deadline-compressed product lane | Explicit player direction; existing screenshots are well below target quality | Product direction accepted; implementation pending |
| Retain the ten-phase milestone system and only two root planning files | Clear autonomous-agent continuity without document sprawl | Accepted by previous user instruction |
| Preserve alternate-history branching as a defining **game mechanic** | Core identity and originality; "timeline" objection refers to development schedule, not time-rewind gameplay | Existing deterministic branching and save fixtures pass on JVM and Android |
| Keep Android and Desktop as core KMP targets; renderer is an adapter | Meaningful shared Kotlin engineering without assuming extra platform support | Android GLES and Desktop Vulkan verified in Phase 01 |
| Open the Fading Grove on seed `104729`, with a paused authoritative clock | The real world appears immediately and the player chooses when time starts | Implemented and covered by the Desktop ecology UI test |
| Route grove planting and creature focus through their existing owners | The deterministic command affects future histories. Camera requests remain cosmetic | Implemented |

### Experiment Ledger

| Hypothesis / candidate | Baseline scenario | What was actually measured or observed | Keep / revert / pending |
|---|---|---|---|
| Fill the frame with a coastal landscape | Seed `104729`, default camera and scrolling dashboard on Pixel 9 AVD and Apple M4 | Full-frame water removes the exposed sea rim. The closer landscape makes creatures and terrain visible on both targets. Portrait crops the coast intentionally. | KEEP. Check the remaining near-field and canopy detail in Phase 02 after approval. |
| Add contact shade, rock silhouettes, and meadow detail | Seed `104729`, tick 0, Desktop Vulkan | Replaced repetitive round rocks with terrain-conforming facets and added small plants. The first shade pass showed white patches in Desktop composition. Opaque framebuffer alpha restored the correct shade and wavelets. | KEEP corrected composition. Measure presentation pacing at the larger readback size. |
| Keep creatures visible during mobile inspection | Seed `104729`, Pixel 9 portrait | The initial sheet hid much of the focused animal and the camera button overlapped the title. A shared header row allocates room for both labels and the regional controls now expand on demand. | KEEP. Review with an unaided player and larger text. |

### Latest Handoff

```text
ACTIVE PHASE / STATUS: 01 — COMPLETE, awaiting human acceptance
PLAYER-VISIBLE CHANGE: The Fading Grove opens directly onto a live coast at seed 104729. Begin story starts time in the same scene. Restore grove previews a region and plants through the shared simulation. Inspect can follow a creature. Narrow comparison keeps the scene visible and expands the details on demand.
VISUAL EVIDENCE: build/verification/experience-01 contains Android before/after, portrait/landscape, 1.3× text, rain, creature focus, planting preview, and matched-time comparison. The Desktop bundle was reviewed at 2240×1300 window pixels. Native Desktop Vulkan captures are 1152×648. See the evidence links below.
CODE CHANGED: Shared Compose opening, HUD, inspection, comparison, and planting feedback. Kool camera, terrain detail, water, rain, and Desktop frame compositing. Android and Desktop entry points, affected tests, and the README walkthrough.
BUILDS & TESTS: PASS. ./gradlew --offline --console=plain :shared:jvmTest :shared:testAndroidHostTest :renderer:jvmTest :renderer:testAndroidHostTest :desktopApp:test :desktopApp:assemble :desktopApp:createDistributable :androidApp:assembleDebug :androidApp:lintDebug :shared:compileKotlinIosSimulatorArm64 :shared:connectedAndroidDeviceTest :androidApp:connectedDebugAndroidTest
SIMULATION / SAVE / BRANCH: PASS. 108 host test executions and 48 on Pixel 9 API 36 AVD. The rules-v5 C2 fork fixture at tick 900 returned Main 5dce8f94926c905d and planted branch 4689c1bc6a8d2df2. The branch planted six saplings and stored wood was 76 vs 124. The Android walkthrough saved two branches, restarted, and restored Main at tick 900 with the same canonical hash. A final-build walkthrough reopened both histories from the generated fixture and checked compact switching and details.
PERFORMANCE: Apple M4, packaged JVM/Vulkan probe at 1152×648, renderer p95 32.68 ms and Compose delivery p95 40.12 ms. The 960×540 trial gave 32.88 ms and 44.51 ms in its single sample. Kept 1152 for readability. Pixel 9 API 36 emulator at 1080×2424 reported renderer p95 18.41 ms on the opening walkthrough and 19.15 ms on restored comparison. These are local scene samples.
PLAYTEST: Agent walkthroughs opened both targets, advanced the world clock, inspected wildlife and regions, and reviewed the final composition. Android exercised a complete fork, planting, comparison, and save/reload with the normal controls. Device tests exercised orbit, pan, pinch, Activity recreation, and foreground pause. Independent unaided playtest NOT TESTED.
DECISIONS / EXPERIMENTS: Retained the existing rules, save layout, and renderer adapters. Kept the opening view at tick 0 and raised Desktop readback to 1152 pixels after measuring both sizes. Flattened framebuffer alpha for Desktop composition. Collapsed mobile comparison details so the player can see the island when switching histories.
KNOWN GAPS: Physical Android NOT TESTED. Independent player reaction and replay intent are unmeasured. Broader environmental animation, lighting and ambience, plus steady Desktop presentation pacing, remain Phase 02 work.
NEXT ACTION: Review the evidence and run ./gradlew :desktopApp:run. Begin the story, pause, and inspect a creature. Open Restore grove, preview B3, plant, and resume time. Accept Phase 01 before starting Phase 02.
PHASE APPROVAL: AWAITING
```

- Android opening and play. [Before opening](build/verification/experience-01/android-before-opening.png) · [Before gameplay](build/verification/experience-01/android-before-world.png) · [Live opening](build/verification/experience-01/android-after-opening.png) · [Gameplay](build/verification/experience-01/android-after-world.png) · [Landscape](build/verification/experience-01/android-landscape-opening.png) · [Landscape at 1.3× text](build/verification/experience-01/android-landscape-large-text.png)
- Android intervention and branches. [Creature focus](build/verification/experience-01/android-creature-focus.png) · [Planting preview](build/verification/experience-01/android-planting-preview.png) · [Main](build/verification/experience-01/android-final-comparison-main.png) · [Restored Forest](build/verification/experience-01/android-final-comparison-grove.png) · [Comparison details](build/verification/experience-01/android-final-comparison-details.png)
- Desktop native Vulkan frames. [Prior wide render](build/verification/phase05/macos-wildlife-tick-0.png) · [Opening composition](build/verification/experience-01/desktop-opening.png) · [Rain](build/verification/experience-01/desktop-rain.png) · [Creature focus](build/verification/experience-01/desktop-creature.png) · [Main](build/verification/experience-01/desktop-main-900.png) · [Restored Forest](build/verification/experience-01/desktop-grove-900.png)
- Verification logs. [Gradle](build/verification/experience-01/gradle-acceptance.log) · [Android walkthrough](build/verification/experience-01/android-walkthrough.log) · [Android save, restore, and renderer](build/verification/experience-01/android-runtime.log) · [Desktop timing](build/verification/experience-01/desktop-perf-1152.log)

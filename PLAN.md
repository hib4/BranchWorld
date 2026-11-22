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

**Active phase:** 08 — World Atlas, Crafted Stories & Meaningful Progression. Phase 07 was accepted on 2026-11-22. Phase 08's original mechanics were verified, but the player rejected the dark-glass shell. The chosen replacement, Painted Wooden Toy Set, is implemented over the existing gameplay. Phase 08 remains unaccepted; see Latest Handoff for verification and visual review.

**Verified foundations:** Android GLES and Desktop Vulkan render the same shared geography. New Fading Grove histories use rules v9 and save schema 7. The Last Crossing and the sandbox use rules v10 and save schema 8, which add a habitat section to the canonical bytes only when present. Real rules-v5 to v9 histories replay with their original hashes. Each island keeps its own atlas slot (`worlds/<slot>.json`) beside a player profile (`worlds/profile.json`). The original single save is copied into its island's slot once and never rewritten.

**Player experience:** The live island fills the opening. A beech name sign sits above it and a low shelf carries Begin or Continue, then Stories, World Atlas, Sandbox and Settings. A new player can begin The Fading Grove directly; Continue names the current island and saved day. Wooden boards carry the guide and village requests, and chapter paint identifies the story at a glance.
- **Stories** shows illustrated chapter cards with real island maps. The Last Crossing stays locked until the Fading Grove is closed.
- **The Last Crossing** takes place on Cairnwater. Its deer herd keeps two homes: the lake shore in the dry season and the western pasture in the rains, where it raises its calves. Its way crosses the high pass, drawn as a worn trail. Cairnholt's families fill their homes, and at tick 960 the village asks to settle the pasture. The Keeper can protect the pasture (a refuge; calves follow, but crowded families leave), endorse building within (a delay; families take the pasture later), or let the village decide (it settles the pasture, and the herd raises few calves).
- **Closing a chapter:** once the crisis has played out, a chapter can be closed from its top card. The closing sheet shows every history's real outcome, and the chosen ending is recorded in the Atlas.
- **Sandbox:** shape any gallery island or a typed seed with every power, including Protect habitat.
- **Pause menu:** play is kept automatically. The pause menu offers the timeline, marked moments, photo mode, settings, the Atlas and the title screen.
- **Settings:** sound, motion, text size (up to 1.3×) and tick tools persist across sessions.
- **Keeper's guide:** short hints appear once at the first look, the warning, the petition, the rewind and the close.

### Phase Ledger

| Phase | Engineering status | Player / visual quality | Evidence | Human acceptance |
|---|---|---|---|---|
| 01 | COMPLETE | World-first opening and intervention reviewed on Android and Desktop | `207e5f1`, Phase 01 captures and verification below | ACCEPTED |
| 02 | COMPLETE | Sunlit, rainy, dusk, close-up and matched-history scenes reviewed on both targets. Live Android audio recorded. | `5dd1945` and `build/verification/presentation-02` | ACCEPTED |
| 03 | COMPLETE | Three explorable landscapes, visible water stages, connected habitats, and named viewpoints | `8d7d04a` and `build/verification/geography-03` | ACCEPTED |
| 04 | COMPLETE | Named wildlife can be followed during play. Native captures and matched-time histories show pursuits, refuge and changed populations. | `WildlifeTest`, `build/verification/wildlife-04`, and Phase 04 evidence | ACCEPTED |
| 05 | COMPLETE | Local rain changes Hollowmere’s harvest. Restored C2 develops usable habitat. Both are reachable from the same warning on Desktop and Android. | `WorldShapingTest` and `build/verification/shaping-05` | ACCEPTED |
| 06 | COMPLETE | Actual Desktop and Pixel 9 play, shared warning, matched harvest and grove Echo, save and restart | `WorldShapingTest`, `TimelineExplorerTest`, `build/verification/living-echo-06` | ACCEPTED |
| 07 | COMPLETE | Named households, visible daily work, petitions, a second settlement, truthful Chronicle and village Echo on Desktop and Pixel 9 | `VillageTest`, `DesktopVillageUiTest`, `build/verification/village-07` | ACCEPTED |
| 08 | COMPLETE | Mechanics verified. The first menu look was rejected; the Painted Wooden Toy Set replacement is built and recaptured on Desktop and Pixel 9 | `LastCrossingTest`, `AtlasTest`, Desktop UI tests, `build/verification/atlas-08/redesign`, and Latest Handoff | PENDING |
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
| Advance from Experience Reconstruction to Breathtaking 3D World & Responsive Presentation | Human approved the Phase 01 gate | Phase 02 accepted |
| Keep coastal time, wind and audio in presentation | The climate projection follows the displayed tick. Decorative effects and camera framing do not affect simulations or saves. | Verified by cross-target rules-v5 fixtures |
| Retain Desktop Vulkan and Android GLES | Both packaged targets render the shared shader scenes. The lower Android detail budget affects decorative density. | Verified on Apple M4 and Pixel 9 API 36 AVD |
| Open new runs of The Fading Grove on the river valley | Human accepted Phase 02 and selected the evolved chapter opening. Continue restores histories by their original terrain identity. | Phase 03 active |
| Keep the three explored islands and one recent additional world in session memory | Switching from the coast to the valley can involve the same seed. Full terrain keys distinguish each geography and retain already explored histories when revisiting the gallery. | Verified by shared session and Desktop gallery fixtures |
| Version new geography and its environmental snapshot together | Version 2 routes water through the generated relief. Rules v6 add recharge to each history and schema 4 records it. Legacy integrity encoding omits the new optional field. | Verified with schema 1–4 fixtures and the Phase 02 packaged save |
| Give wildlife pursuits storybook tension | The user selected visible stalking and flight, with catches resolved without graphic detail and recorded in the island’s wildlife history. | Phase 04 approved |
| Keep shared wildlife rules and version histories with the same terrain | Rules v7 add wildlife snapshots and schema 5 while reusing worldgen v2. Live sessions distinguish rules as well as terrain. | Both earlier saves replayed with their original hashes. Android Continue reopened the v6 island and the two v7 histories. |
| Reserve one intervention until natural rain replenishes the watershed | Gives players a choice between early relief for Hollowmere and lasting habitat | User selected this rule. The shared fixture verified both outcomes. |
| Version the drought and root biomass rules without changing worldgen | Local rain, village water, and branch opportunity now affect authoritative state. Rules v8 writes schema 6. | Rules v5–7 verified against saved artifacts. |
| Use one shared 3D view for the Living Echo | Full-frame terrain and an aligned switch leave room to inspect individual sites on phones. | Player selected and approved for Phase 06. |
| Represent villages as named households | Readable attachment without per-person simulation cost; families have a home, a trade, hardship and a recorded departure cause | Player selected for Phase 07. Implemented in rules v9. |
| The village asks the Keeper through petitions | Water and Growth petitions are raised by state, carry their feasible options, and are answered with a branchable `EndorsePlan` command; unanswered petitions are decided by the village at a deadline | Player selected for Phase 07. Implemented and tested. |
| Rules v9 and save schema 7 for new games; keep v5–v8 replay | New settlement rules change outcomes; earlier histories must reopen unchanged | Player selected. v7 and v8 Android saves replayed with original hashes. |
| The second story is The Last Crossing on Cairnwater, with Protect Habitat | Builds on the village and wildlife systems; the herd's seasonal way meets the village's need for land | Player selected for Phase 08. Implemented in rules v10. |
| One autosaved slot per island in a World Atlas | Every island keeps all of its histories without a replace dialog; the original single save is copied once and never rewritten | Player selected. Desktop and Android imports verified byte-for-byte. |
| The player closes a chapter after its crisis; closing opens the next story | Endings are chosen from real outcomes of every history, with no grades; histories stay playable | Player selected. Fading Grove closes after its recovery, The Last Crossing after the herd's year. |
| Wildlife eases to 0.58× within a few cells of village structures | Keeps homes legible beside deer without changing the approved size elsewhere | Player selected. Cosmetic only. |
| Game-like menus in a "Keeper's Atlas" style | First Phase 08 shell used ink-glass, vellum and gold ornaments | Implemented, then rejected by the player and superseded. |
| Painted Wooden Toy Set, code-led | Player selected a live island framed by a beech sign and shelf; chapter paints carry meaning, with Fredoka and Nunito lettering. Android portrait and Desktop landscape have equal priority. | Implemented in the shared shell; see Phase 08 redesign handoff. |
| Rules v10 and save schema 8 for The Last Crossing and the sandbox; keep v5–v9 replay | Seasonal ranges, refuges, crowding and the pasture plan change outcomes | v8 Android save and v9 Desktop import replayed with original hashes. |
| Woodcutters coppice and newcomers avoid a village a family has just left | Unrestricted v9 woodcutting drove Cairnwater foxes extinct; arrivals into a failing village produced boom-bust churn | Keep. All three gallery islands sustain every species for 24,000 ticks with a village. |

### Experiment Ledger

| Hypothesis / candidate | Baseline scenario | What was actually measured or observed | Keep / revert / pending |
|---|---|---|---|
| Fill the frame with a coastal landscape | Seed `104729`, default camera and scrolling dashboard on Pixel 9 AVD and Apple M4 | Full-frame water removes the exposed sea rim. The closer landscape makes creatures and terrain visible on both targets. Portrait crops the coast intentionally. | KEEP. Check the remaining near-field and canopy detail in Phase 02 after approval. |
| Add contact shade, rock silhouettes, and meadow detail | Seed `104729`, tick 0, Desktop Vulkan | Replaced repetitive round rocks with terrain-conforming facets and added small plants. The first shade pass showed white patches in Desktop composition. Opaque framebuffer alpha restored the correct shade and wavelets. | KEEP corrected composition. Measure presentation pacing at the larger readback size. |
| Keep creatures visible during mobile inspection | Seed `104729`, Pixel 9 portrait | The initial sheet hid much of the focused animal and the camera button overlapped the title. A shared header row allocates room for both labels and the regional controls now expand on demand. | KEEP. Review with an unaided player and larger text. |
| Soften repeated bands on the water | Seed `104729`, overview at 0, 330, and 645, Kotlin renderer on both targets | Replaced continuous bands with crossing waves and coast-following foam. Review showed clearer shoreline contours, while rain rings and wet light remained visible. | KEEP. Revisit nearshore terrain variety with Phase 03 geography. |
| Let the renderer pace Desktop readback | Same Phase 02 opening, Apple M4, Vulkan, 1152×648 | At the 30 FPS cap, removing the 32 ms throttle changed Compose p95 from 43.66 to 42.79 ms. With the throttle removed, increasing the cap to 60 changed it to 23.90 ms. The final build measured 26.44 ms p95. | KEEP both changes and the one-download limit. Profile wider output when measuring more hardware. |
| Check the heavier coastal scene on Android | Same seed and portrait resolution on Pixel 9 API 36 AVD | Matched opening renderer p95 was 18.41 ms before and 18.48 ms after. First complete asset frame was 1.24 s before and 2.14 s after in those runs. GPU textures stayed about 12.13 MB. | KEEP the scene. Profile cold shader and asset loading on physical hardware. |
| Find a clearer angle for tracking wildlife | Desktop inspector and fixed-seed Pine fixture | The original follow angle put the canopy in front of a deer. Scoring obstruction when tracking begins improved the angle, and the player can still orbit. | KEEP. Close canopies can still partly occlude a creature. |
| Leave room for a tracked animal in the narrow inspector | Pixel 9 portrait, seeded rain and deer tracking | Full settlement details filled the lower half of the screen. The compact layout now opens those details on demand, verified in the 390 dp Compose test. | KEEP. Capture the final inspector again on a phone when device access is available. |
| Make the channel read as one continuous feature | Seed `104729`, Desktop Vulkan and Pixel 9 AVD, first survey and estuary close-up | First captures showed rectangular color changes at tile boundaries and abrupt banks. Shared corner samples, softer bank slopes, and coastal waves carried upstream greatly reduced the breaks. | KEEP. Some fine stepped edges remain at close range. |
| Keep settlement and wildlife access reliable across seeds | Gallery seeds, `-24..24`, and signed 64-bit extremes | Cairnwater initially drained toward the nearest coast. A tapered headwater apron made the long drainage viable. Ranking only drinking banks with a nearby clearance removed failures where the first candidate had steep ground. All tested landscapes have two banks on the main connected component. | KEEP. Revisit route variety when expanding wildlife territories. |
| Link visible low water to actual growing conditions | Valley at 299 and 479, fixed seed `104729` | Recharge fell from 64 to 6 before returning above 90 during rain. Regrowth resumed and the same settlement field yielded more per worker from wetter soil. | KEEP. Test what players infer from the banks before designing additional interventions. |
| Keep opening copy legible against the wider valley | Pixel 9 AVD at 2424×1080 | The short landscape viewport originally used the portrait introduction, which put the last words over bright ground. The opening now uses its own aspect-ratio breakpoint and shades the text side, leaving the river visible on the right. | KEEP. Check against a larger system text setting during the accessibility pass. |
| Sustain wildlife under finite forage | All three gallery seeds, 24,000 ticks each | Initial rabbit and fox groups collapsed. More reachable breeding sites and rabbit use of understory helped. Valley rabbits still ran out of food while switching between forage and water. Committed recovery trips and applying the extra food cost to sprinting let all species survive. | KEEP. The fixed-seed fixtures check movement, biomass accounting, survival and cross-target state hashes. |
| Show refuge without overlapping a fox and rabbit | Valley pursuit close-ups and Cairnwater 24,000-tick fixture | First native captures showed a fox sharing a sheltered rabbit's tile. Stopping at the refuge boundary avoided that overlap. The resulting upland decline exposed adults failing to meet across the lake. A common wet-season den and species-specific breeding reserves restored replacement generations. The final Cairnwater run recorded nine catches and finished with all three species. | KEEP. The caveat is visible crowding near small banks, where each animal model now occupies less of the scene. |
| Separate wildlife silhouettes in cover | Fixed valley openings and pursuit, Desktop Vulkan and Android GLES | The source rabbit and fox had similar warm colors at the default camera distance. A muted rabbit coat, rust fox coat, wider obstruction test, scaled bodies, and closer follow made the roles clearer. Berry-bearing shrubs mark browse in new histories. | KEEP, after native review. |
| Clear the foreground silhouette at narrow banks | Desktop Vulkan at tick 438 | Depressing water above walkable tile centers added angular gaps and left the perspective overlap visible. | REVERT. Retained the smooth shoreline and the smaller wildlife bodies. |

| Competing drought decisions should change different outcomes | Seed `104729`, untreated, Call Rain B2, and Restore Grove C2 from the same warning | Rain raised the dry-season harvest from two crops to six and the village reserve from zero to 12. The first grove trial rooted no trees because wildlife grazed seedlings. A moderate establishment bonus alone did not solve it. Reserving the root biomass while allowing animals and the settlement to take surplus browse produced three rooted groves by tick 900. Wildlife fed or rested at the planted sites. | KEEP. Compare which locations players choose and how clearly they see long-term habitat effects. |
| Keep rainfall and targeting tied to the land | Desktop Vulkan and Pixel 9 portrait, B2 and C2 previews | The first preview could hide the bank under the panel. Tall streak geometry put drops above the cloud. Reframing around actual targets, shortening the narrow sheet, softening region tint, and limiting streaks to the cloud’s rain column kept the bank and effects visible. | KEEP. A cloud can still clip at close zoom, and foreground pines can hide some planting markers. |
| Return to the point where the warning first happened | Grove and rain variants forked exactly at tick 240 | Returning from an equal-tick fork initially selected that fork. The timeline now follows ancestry through inherited warning snapshots. The Android save recorded both variants as children of Main, with each intervention at tick 241. | KEEP. Reuse the same turning point for the spatial comparison in Phase 06 after approval. |
| Keep the landscape visible on a short wide screen | Pixel 9 at 2424×1080 with the reloaded grove comparison open | The bottom sheet initially covered most of the island. A side-docked panel left the view available while retaining the comparison controls. The new 850×390 Compose test verifies right-hand docking. | KEEP. Review the spatial alignment again when adding Living Echo. |
| Keep Echo places available while scrubbing | Pixel 9 at 1080×2424, two futures at the post-rain checkpoint | The first dock put the place buttons below the fold. A compact dock for named moments and a top strip for places fit at normal and 1.3× font scale. | KEEP. Check rotation framing when introducing additional comparison sites. |
| Make a grove from another future recognizable at dusk | Apple M4 Vulkan and Pixel 9 GLES at the matched C2 site | The early wireframe looked technical. Two translucent crowns and dashed roots distinguish the alternate trees from the solid planted grove. Foreground pines occlude some marks. | KEEP. Keep camera and depth behavior when adding new footprints. |
| Find where the rules-v8 village first diverges | Seed `104729`, warning at 240, untreated vs rain B2 vs grove C2 to 1800 | Rain gave 8 vs 0 water at 300 and raised homes at 540/1140 instead of 780/1560. The grove's settlement numbers matched Main exactly; wood and stone accumulated unused; food was never short. | Informed rules v9: thirst, construction sinks and field grazing now connect the village to the ecology. |
| Make the drought cost something the Keeper can change | Same seed and three histories under rules v9 | Two haulers erased the thirst; one hauler and severe-thirst threshold (unmet × 3 > residents × 2) made the Quills leave at 480 unless the Pells move to the river flats. A well endorsed at 421 is finished by 600 but cannot save the Quills. | KEEP. The well is an investment, the flats a rescue with a flood risk. |
| Keep villages and wildlife stable for 24,000 ticks | Three gallery islands, rules v9 | Cairnwater first churned through 15 departures; whole households fell to gathering because a field's room was assigned per household. Splitting hands, production-based growth and an arrival cooldown left two departures. Lot spacing 3 drove deer extinct there; spacing 2 kept all species. Unlimited timber took fox browse; coppicing to half restored foxes. | KEEP spacing 2 and coppicing. |
| Make Hollowmere readable from the village camera | Desktop Vulkan 1152×648 and Pixel 9 1080×2424 | The first pass drew homes at 0.28 height beside 0.9 pines and a pine grew through the village. Homes at 0.38 scale with two-cell lots, villagers at 0.095, a closer Village focus and hidden pines on village plots read clearly. | KEEP. Wildlife bodies remain larger than homes and can hide Echo marks in the village. |
| Share the KayKit atlas | Same Desktop scene | Each KayKit model file loaded its own 1024² atlas. One shared texture now serves all building props; GPU textures measured 33.6 MB vs 28.0 MB in Phase 06 with three villager bodies added. | KEEP. Measure Android memory when more structures are on screen. |
| Place the herd's seasons from terrain alone | Cairnwater, Fading Grove and Heron Reach under rules v10 | Choosing the wet range as the forage close across water but far to walk put Cairnwater's pasture on a sliver of lake shore. Choosing the richest pasture at least 24 steps from the dry-season bank found the western meadows on Cairnwater and plausible ranges on both other islands. | KEEP. The route runs over the high pass on Cairnwater. |
| Make the herd's year depend on the village | Same seed, warning at 960, three answers to 7,200 | With calving everywhere, all three histories ended with 5 deer. Calving only on a quiet wet-season pasture left the herd with no water there, so no calves in any history. Rain pools on the pasture in the wet season, and a displaced herd raising at most one late calf every other season, gave 2 / 4 / 2 calves and 5 / 7 / 5 deer. | KEEP. Pinned hashes match on JVM, Android host and iOS. |
| Make the refuge cost the village something | Same three histories | Building within at first beat the refuge outright. Counting settlement turns with every roof full (12 turns) made families take the pasture anyway after building within. Under a refuge, crowded families leave instead: 12 / 8 / 18 people. | KEEP. Revisit how often families leave over a longer game. |
| Keep long saved histories quick to reopen | Three Last Crossing histories at 7,200 ticks | Validation rebuilt the island's habitat for every snapshot: 623 ms cold. Sharing one habitat during a single validation and caching the baseline: 175 ms cold, 48 ms warm. | KEEP. |
| Replace the rejected dark-glass shell with a chosen visual world | Phase 08 menus at 1280×800 and Pixel 9 portrait (`build/verification/atlas-08/desktop`, `android`) | The player chose Painted Wooden Toy Set on the Impeccable decision page. The live island now fills the opening, with a beech sign and a four-route shelf. An independent finish review of the first build returned "fix": cropped island framing, ruled-paper grain, a guide that covered the chapter card in landscape at 1.3×, clipped labels and no cue for more content below, dark status icons, low-contrast day stamps, and unexplained disabled choices. Two correction rounds followed. The second round moved the overflow cue below the content, put landscape pause in a single band of four columns, kept 8 dp dock gaps by insetting the HUD with safeDrawing, aligned the closing rows with explicit zeros, and widened the short-window framing. Latest recaptures: `redesign/round3-*` and `redesign/android/8x-round3-*`. | KEEP. Awaiting the player's own judgement. |
| Frame the whole island on the opening at every aspect | Same title at 1280×800, 850×390 and 1080×2424 | The original framing cut the shore on both sides on the phone and cut off the bottom on Desktop. Pulling the opening camera back by aspect (1.8–2.3 units wide, 0.6–4.2 tall) brings the shoreline into frame without returning to a tiny island. | KEEP. Desktop still crops the southern tip slightly under the shelf. |
| Cache the two oriented Echo meshes | Grove comparison at tick 900 with fixed camera and Reduce Motion | The final 1152×648 Vulkan runs sampled 15.77 ms p95 with Echo and 16.03 ms without. Echo added one draw and 0.26 MB of buffers. The changed viewport image arrived in 19–29 ms across 20 warm switches. | KEEP. These are local samples. Compare longer histories on physical Android when available. |

### Latest Handoff

#### Phase 08 follow-up: phone margins (player request)

```text
PLAYER-VISIBLE CHANGE: Phones no longer lose about 30 % of their width to margins. Boards sit 12 dp from the screen edge and text starts about 24 dp in, where it used to start 46–66 dp in. Desktop gutters are unchanged.
CODE CHANGED: GameTheme.kt Spacing/LocalSpacing (compact under 600 dp); App.kt provides it and every overlay now uses safeDrawing insets, not safeContent, which had added the ~30 dp back-gesture zones; Board default padding reads the token; MenuScreens title gutter; GestureEdges expect/actual so the timeline scrubber opts out of Android edge gestures; DioramaDeviceTest pauses only if the story is playing. New test DesktopSpacingUiTest: phone shelf at 26 dp failed before the change and passes at 22 dp after it; Desktop stays at 34 dp.
BUILDS & TESTS: PASS. Host suite with --rerun-tasks 180 tests (76 + 75 + 22 + 6 + 1); iOS compiles; :shared:connectedAndroidDeviceTest 74 and :androidApp:connectedDebugAndroidTest 2 on the Pixel 9 AVD (app test passed twice).
VISUAL EVIDENCE: build/verification/atlas-08/redesign/android-spacing/S01–S12 (Pixel 9 portrait and landscape, standard and 1.3× text, with uiautomator measurements in the run log), spacing-desktop-wide and spacing-desktop-short (Desktop unchanged).
KNOWN GAPS: In landscape the camera cutout still reserves about 44 dp along that whole edge. Timeline's Rename, Watch and Fork buttons and the scrubber are still stock Material controls, not wooden pieces.
```

#### Phase 08 redesign evidence (Painted Wooden Toy Set)

```text
ACTIVE PHASE / STATUS: 08 — COMPLETE (mechanics) with the replacement shell built; awaiting human acceptance
PLAYER-VISIBLE CHANGE: The rejected dark-glass "Keeper's Atlas" shell is gone. The live island fills the opening, framed per aspect so its shoreline reads. A beech name sign sits top-left and a low tray holds one painted Begin/Continue block (island and day stamped) over Stories, World Atlas, Sandbox and Settings. A new player can begin The Fading Grove straight from the title. Chapter paint means something: weld = The Fading Grove, woad = The Last Crossing, leaf = sandbox, umber = earlier rules. Story cards carry a numeral token that flips from bare wood to its paint when the chapter opens. The HUD, petition, guide, pause, settings, Shape, Inspect, Timeline, Living Echo and chapter-closing surfaces are all wooden boards, blocks and pegs. Scrolling sheets show a fade and chevron while more lies below. Past-moment petitions and blocked interventions say how to fork from the Timeline. A closed chapter offers "Revisit island", and the closing sheet marks the current history without recommending one.
VISUAL EVIDENCE: build/verification/atlas-08/redesign/round3-desktop-wide (Desktop Vulkan, real App + renderer, 1280×800: title, stories, atlas, sandbox, settings, Last Crossing world, herd's way, pause, closing, closed, petition, Shape). round3-desktop-short (850×390 at 1.3× text: title, settings, world, timeline). android/ (Pixel 9 API 36 AVD 1080×2424 and 2424×1080): 01–12 first-run menus and the 1.3× flow; 80–85 latest title, landscape world/pause and portrait world/pause at 1.3×. Earlier rounds (final-*, review-*, 4x/6x/7x) are kept for comparison. Review packet: .impeccable/review/*.png.
CODE CHANGED: GameTheme.kt (Wood tokens, chapter paints, ink-on-paint), GameComponents.kt (woodPiece edge/contact/grain, Board depth, Block sizes incl. Compact with Button/RadioButton/Checkbox semantics, PegButton focus ring, ChapterStamp flip, WoodSwitchRow as Role.Switch, ScrollColumn overflow cue, Reduce Motion snaps set-down, press and switch motion, WorldCameraReset), MenuScreens.kt, WorldOverlays.kt, App.kt (title placement, HUD, dock, short-landscape side column), Chronicle.kt, LivingEchoUi.kt, ShapeControls.kt, WorldInspector.kt, TimelineControls.kt, BranchComparison.kt; renderer PreviewCamera.kt (opening framing per aspect) and PreviewController.kt (wooden camera-reset peg); MainActivity light status icons. WoodBridge.kt removed. Fonts Fredoka + Nunito (OFL, assets/fonts); Fraunces removed; README credit corrected. Simulation, saves, profile and rules untouched.
BUILDS & TESTS: PASS. ./gradlew --offline --console=plain --max-workers=2 -Pkotlin.incremental=false :shared:jvmTest :shared:testAndroidHostTest :renderer:jvmTest :renderer:testAndroidHostTest :desktopApp:test :desktopApp:createDistributable :androidApp:assembleDebug :androidApp:lintDebug :shared:compileKotlinIosArm64 :shared:compileKotlinIosSimulatorArm64 --continue --rerun-tasks — 178 host tests (76 + 75 + 20 + 6 + 1), 0 failures. :shared:iosSimulatorArm64Test (LastCrossing, Village, WorldShaping, Atlas) — 17 PASS. :shared:connectedAndroidDeviceTest 74 PASS and :androidApp:connectedDebugAndroidTest 2 PASS on the Pixel 9 AVD, on three builds including the last. DioramaDeviceTest now scrolls the settings sheet and waits until the tick-tools switch reports on. It used to assume the switch was visible and toggled instantly, and failed against the taller wooden rows. New test: titleBeginsTheFirstStoryWithoutVisitingTheStoryShelf failed before the title change and passes after it.
SIMULATION / SAVE / BRANCH: No rules or codec changes. Pinned v9/v10 hashes still pass on JVM, Android host, Android device and shared iOS. Desktop captures ran on fixture copies built by AtlasFixtureBuilder in build/verification/atlas-08/redesign/cairnwater; the emulator ran as a read-only cold boot with -no-snapshot-save, so its stored snapshot and data were neither loaded nor written. Pushing fixture saves into the emulator app was refused by the session's safety check, so the Android Cairnwater closing/petition/Protect flow was verified on Desktop only this round.
PERFORMANCE: Apple M4 Vulkan, Cairnwater refuge history at 850×390: first asset frame 1.24–1.44 s, 14.1 MB buffers, 13.1 MB textures. Frame-time percentiles NOT MEASURED for the redesign.
PLAYTEST: Scripted Desktop and AVD walkthroughs only. Independent unaided playtest, physical Android and TalkBack NOT TESTED.
DECISIONS / EXPERIMENTS: The player chose Painted Wooden Toy Set. The independent Impeccable finish review returned "fix" with eight items. After two correction rounds, the second verdict scored four of the five carried items resolved and one partial, and raised the two gaps below. The HUD top row and dock now inset by safeDrawing (taps only, so the back-gesture edges are safe) to keep the 8 dp dock gaps on a 411 dp phone. DESIGN.md and .impeccable/design.json document the system as built.
KNOWN GAPS: The independent finish review's second verdict left two items open. (1) Once scrolled down, the short-Desktop Settings sheet at 1.3× clips its top row with no upward cue. (2) In phone-portrait play with no card open, a band of open sea sits between the island and the dock; the portrait in-world camera needs reframing. Phone portrait also still crops the island's sides. Long sheets (settings and closing at 1.3×) scroll, with a chevron under the content. Requests arrive as boards but do not yet animate as "placed pieces" beyond the shared set-down; the sign-to-shelf unfolding motion in the contract is not built. Photo mode still saves no image; bookmarks are not drawn on the scrubber. SaveControls and IslandPicker remain unreachable legacy code.
NEXT ACTION: Ask the player to judge the new look on their own device/Desktop; if accepted, accept Phase 08 and plan Phase 09.
PHASE APPROVAL: AWAITING
```

#### Phase 08 evidence

```text
ACTIVE PHASE / STATUS: 08 — COMPLETE, awaiting human acceptance
PLAYER-VISIBLE CHANGE: BranchWorld now opens on a game-style title menu over the live island: Continue (with the island, day and history), Stories, World Atlas, Sandbox and Settings, plus one line on what to do next. Stories shows illustrated chapter cards with real island maps; The Last Crossing opens once the Fading Grove is closed. In The Last Crossing on Cairnwater, a deer herd keeps a dry-season home at the lake and a wet-season pasture in the west, and its worn trail crosses the high pass. At tick 960 Cairnholt asks to settle the pasture. Protect habitat keeps it a refuge, so calves follow but crowded families leave. Building within only delays the move. Leaving it to the village settles the pasture and the herd raises few calves. When a chapter's crisis has played out, the top card offers Close this chapter; the closing sheet compares every history's real outcome and records the chosen ending in the Atlas. The Atlas lists every island kept with its histories, closed chapters and marked moments. The sandbox shapes any gallery island or typed seed with every power. Play is kept automatically. The pause menu offers the timeline, Mark this moment, photo mode, settings, the Atlas and the title screen. Settings for sound, interface clicks, motion, text size and tick tools persist. The Keeper's guide gives one short hint the first time each moment matters. Deer ease to a smaller size near village structures.
VISUAL EVIDENCE: build/verification/atlas-08/desktop holds Desktop Vulkan frames at 1280×800, with the real renderer under the real App via DesktopAtlasCapture: title, stories, atlas, sandbox, settings, Last Crossing world, herd's way, pause, closing, closed. build/verification/atlas-08/android holds Pixel 9 API 36 AVD frames at 1080×2424: title, stories, atlas, sandbox, settings, imported earlier-rules grove, closing, closed, Last Crossing opening and petition, Protect habitat, refuge queued, herd's way.
CODE CHANGED: Shared ecology Habitat.kt and Ranges.kt (rules v10 chapter marker, seasonal ranges, route, disturbance, refuges, herd passage log); Wildlife migration, avoidance, wet-season pools and calving; EcologyEngine v10 wiring, ProtectRegion and the crossing warning; VillageRules Pasture plan, refuge-aware building, crowding relief and the Crowding departure; canonical habitat bytes; TimelineSaveCodec schema 8 with shared-habitat validation; ChapterOutcome and closing rules; WorldRenderProjection range pose and herd route. Profile and slots: PlayerProfile.kt, named-file WorldSaveStore, Android and Desktop atomic slot writers, legacy import, autosave, ShellState home screens and session keys with chapters. Compose: GameTheme, GameComponents, GameIcons, MenuScreens, WorldOverlays, Stories, a rebuilt App shell, chapter-aware names and wording, Protect power controls, and the Kenney interface sounds. Renderer: HerdTrail (trail, direction chevrons, refuge edges) and village-proximity wildlife scale. Fonts: Fraunces and Nunito (OFL) in composeResources/font, licences in assets/fonts.
BUILDS & TESTS: ./gradlew --offline --console=plain --max-workers=2 -Pkotlin.incremental=false :shared:jvmTest :shared:testAndroidHostTest :renderer:jvmTest :renderer:testAndroidHostTest :desktopApp:test :desktopApp:createDistributable :androidApp:assembleDebug :androidApp:lintDebug :shared:compileKotlinIosArm64 :shared:compileKotlinIosSimulatorArm64 --continue --rerun-tasks — PASS (76 shared JVM, 75 shared Android host, 19 renderer JVM, 6 renderer Android host, 1 Desktop). ./gradlew ... :shared:iosSimulatorArm64Test --tests '*LastCrossingTest*' --tests '*VillageTest*' --tests '*WorldShapingTest*' --tests '*AtlasTest*' — 17 PASS. Connected Android instrumentation suites (DioramaDeviceTest was updated for the new menus) NOT RUN.
SIMULATION / SAVE / BRANCH: LastCrossingTest gate on seed 271828: from the warning at 960, the village's own choice, Protect habitat and Build within reach 7,200 with deer [5, 7, 5], calves [2, 4, 2], people [12, 8, 18]. Hashes 12c4be589ed70975, 4e8533e3ea535f68, 94d59b38d4f8d27a are pinned and match on JVM, Android host and shared iOS. Schema 8 save/restore and full command replay match. 24,000-tick rules-v10 soaks on all three gallery islands keep settlements and every species. AtlasTest covers slot names, unlock order, profile round trip and corrupt profiles, a one-time legacy import that never writes the original, reopening and autosaving a slot, never overwriting an unreadable slot, and chapter closing only after the crisis with outcomes read from state. Desktop imported the Phase 07 v9 Android save into story-fading-grove.json and reopened it with hash ca961eb3e01bcf0c, original unchanged. The Pixel 9 imported its Phase 06 v8 save into island-104729-w2-r8.json byte-identically; a JVM replay of that slot and of the Android-made Last Crossing slot matched every branch, including the original 872f8b772885c3d5. The emulator's walkthrough slots were backed up to build/verification/atlas-08/android-walkthrough-worlds and removed. Its original save is unchanged (SHA b01f1ba6…).
PERFORMANCE: Apple M4 Vulkan at a 1152×720 viewport in the Cairnwater refuge history: renderer p95 15.46 ms, Compose delivery p95 23.7–29.9 ms, 149 draws, 14.1 MB buffers, 17.5 MB textures, first asset frame 1.06 s. Pixel 9 API 36 AVD at 1080×2424: renderer p95 18.21–18.99 ms (25.01 ms in one sample with two settlements), 122–181 draws, 3.5–7.6 MB buffers, 17.7–34.5 MB textures, first asset frame 2.67–2.99 s (Phase 07: 1.70 s). Saved-history restore at 7,200 ticks × 3 branches: 175 ms cold JVM, 233–358 ms in the Desktop app. Local samples only.
PLAYTEST: Compose Desktop tests and the AVD walkthrough exercised every menu, continued the imported history, closed it, began The Last Crossing, reached the petition, returned to the turning point, branched, declared a refuge and followed the herd's way. During one AVD wait, an endorsement and a resume were made by touches outside the script (the Chronicle recorded "The Keeper answered"); the script itself never taps there, and a paused clock stayed paused for 60 s when tested separately. Independent unaided playtest and physical Android NOT TESTED.
DECISIONS / EXPERIMENTS: The Last Crossing with Protect habitat; per-island autosaved slots; player-closed chapters; village-proximity wildlife scale; Keeper's Atlas menus; rules v10 / schema 8. Experiments covered seasonal range placement, wet-season pools and displaced calving, crowding relief, and shared-habitat validation (Experiment Ledger).
KNOWN GAPS: Only two crafted stories exist; Ember Season and The River Remembers remain planned. Photo mode hides the interface but does not yet save an image. Bookmarks live in the profile and are listed in the Timeline panel, but are not drawn on the scrubber. The herd's route is fixed per island; settlements can disturb its pasture but rarely block the pass itself. Families may leave a protected village repeatedly over long games. The portrait phone cannot frame the whole herd route. Android first asset frame rose to about 3 s on Cairnwater. Connected instrumentation suites, physical Android and unaided playtest were not run. SaveControls and IslandPicker remain in the code but are no longer reachable.
NEXT ACTION: Ask for Phase 08 acceptance. If accepted, plan Phase 09 from the Phase 09 build list: start with seasonal migration and long-term settlement development across more islands, and add a third story (Ember Season or The River Remembers) only after an unaided playtest of the first two.
PHASE APPROVAL: AWAITING
```

- Phase 08 Desktop Vulkan. [Title](build/verification/atlas-08/desktop/01-title.png) · [Stories](build/verification/atlas-08/desktop/02-stories.png) · [Atlas](build/verification/atlas-08/desktop/03-atlas.png) · [Sandbox](build/verification/atlas-08/desktop/04-sandbox.png) · [Settings](build/verification/atlas-08/desktop/05-settings.png) · [The Last Crossing](build/verification/atlas-08/desktop/06-crossing-world.png) · [Herd's way](build/verification/atlas-08/desktop/07-herd-way.png) · [Pause](build/verification/atlas-08/desktop/08-pause.png) · [Closing](build/verification/atlas-08/desktop/09-closing.png) · [Closed](build/verification/atlas-08/desktop/10-closed.png)
- Phase 08 Android GLES. [Title](build/verification/atlas-08/android/android-title.png) · [Stories](build/verification/atlas-08/android/android-stories.png) · [Imported history](build/verification/atlas-08/android/android-continue-world.png) · [Closing](build/verification/atlas-08/android/android-closing.png) · [Closed](build/verification/atlas-08/android/android-closed.png) · [Petition](build/verification/atlas-08/android/android-crossing-petition.png) · [Protect habitat](build/verification/atlas-08/android/android-crossing-protect.png) · [Herd's way](build/verification/atlas-08/android/android-crossing-herd-way.png)
- Phase 08 verification. [Final host suite](build/verification/atlas-08/gradle-final-host.log) · [Shared iOS](build/verification/atlas-08/gradle-native.log) · [Save replays](build/verification/atlas-08/save-replay.log) · [Desktop renderer log](build/verification/atlas-08/desktop-probe.log) · [Android renderer log](build/verification/atlas-08/android-renderer.log) · [Android walkthrough](build/verification/atlas-08/android_atlas.py) · Captures are re-runnable with `DesktopAtlasCapture` and `AtlasFixtureBuilder`, which run only when build/verification/atlas-08/capture.txt or fixture.txt exists.

#### Phase 07 evidence

```text
ACTIVE PHASE / STATUS: 07 — COMPLETE, accepted
PLAYER-VISIBLE CHANGE: Hollowmere is a named village. The Pell, Rook and Quill households have coloured homes, a field, stores and animated villagers who farm, cut timber, quarry, build or haul water by day and sit by their doors at dusk. When the drought makes thirst severe, a petition card asks the Keeper to dig a well or settle the river flats, and the clock holds for the answer. Answering is a timeline command; an unanswered petition is decided by the village before dusk. Rain at the warning prevents the thirst; without it the Quills leave at 480, unless the Pells found Reedford by the basin. A crowded village later asks whether to clear the near woods or build within. The Village life panel lists households, current work and a natural-language Chronicle with Follow buttons. Living Echo adds pale ghost homes and wells, cleared-woods marks and family lanterns.
VISUAL EVIDENCE: build/verification/village-07/desktop holds native Desktop Vulkan frames at 1152×648 (survey, rain day, drought at 420, dusk, well under construction, Reedford at 1500, Hollowmere-focused Echo). build/verification/village-07/android holds Pixel 9 API 36 AVD GLES frames at 1080×2424: opening, warning, compact petition, endorsement, Village life panel, dusk close-up and the reloaded village.
CODE CHANGED: shared ecology Village.kt and VillageRules.kt (rules v9 households, labor, construction, coppicing, grazing, flooding, hardship, petitions, rolling Chronicle, saved-state validation); EcologyEngine/EcologyState/SettlementState/SettlementEconomy (v9 wiring, spaced lots, v8 path unchanged); EndorsePlan command; CanonicalEcologyState v9 section; TimelineSaveCodec schema 7; Timeline endorse/cancel/chronicle and petition and departure moments; LivingEchoProjection village kinds; WorldRenderProjection VillagePose. Compose: Chronicle.kt (Chronicle wording, PetitionCard, VillagePanel), App/LivingEchoUi/TimelineControls/IslandSession integration, strings. Renderer: VillageModels.kt (instanced KayKit/Kenney village with one shared atlas, pooled animated villagers), pine clearing on plots, Village camera focus, ghost buildings in LivingEcho.kt. Tools: tools/assets/build_villagers.py. Removed SettlementModels.kt and the unused Ranger.glb runtime copy.
BUILDS & TESTS: ./gradlew --offline --console=plain --max-workers=2 -Pkotlin.incremental=false :shared:jvmTest :shared:testAndroidHostTest :renderer:jvmTest :renderer:testAndroidHostTest :desktopApp:test :desktopApp:createDistributable :androidApp:assembleDebug :androidApp:lintDebug :shared:compileKotlinIosArm64 :shared:compileKotlinIosSimulatorArm64 --continue — PASS (65 shared JVM, 65 shared Android host, 18 renderer JVM including DesktopVillageUiTest, 6 renderer Android host, 1 Desktop). ./gradlew ... :shared:iosSimulatorArm64Test --tests '*VillageTest' --tests '*WorldShapingTest' — 8 PASS. Connected Android instrumentation suites NOT RUN this session.
SIMULATION / SAVE / BRANCH: VillageTest gate scenario on seed 104729: from the same warning, rain, grove and grove + river flats reach 1500 with residents [9, 6, 8], homes [3, 3, 4] and hashes 5b833cb3915a6c38, b9bef37b5d213fe0, 0b45d2a4adaf82c4, identical on JVM, Android host and shared iOS. The grove history records the Quills leaving because of thirst and a village-chosen well; the endorsed history founds Reedford with no departure through 540. Schema 7 save/restore and full command replay of every branch match. 24,000-tick runs on all three gallery islands keep villages and every species (soak hashes 2a1f3c4db748694c, fce54f20fec1c014, e57e180be1763c18). The Pixel 9 AVD saved a schema 7 history, reopened it after force-stop with Hollowmere and Reedford intact, and a JVM replay matched its Main hash ca961eb3e01bcf0c. Phase 05/06 v8 and Phase 04 v7 Android saves replayed with their original hashes. The emulator's original Phase 06 save was backed up and restored byte-for-byte (SHA b01f1ba6…).
PERFORMANCE: Apple M4 Vulkan at 1152×648, village scene renderer p95 15.97 ms (Phase 06 15.77), Compose delivery p95 23.92 ms, 132–146 draws, 22.1 MB buffers, 33.6 MB textures. Pixel 9 API 36 AVD at 1080×2424, renderer p95 18.14–19.02 ms (Phase 06 17.53), 133–143 draws, 5.7 MB buffers, 34.5 MB textures (Phase 06 23.3; three villager textures added). First asset frame 1.18 s Desktop, 1.70 s AVD. Local samples only.
PLAYTEST: Compose desktop test and the Android AVD walkthrough reached the warning, played to the petition at 420, endorsed, changed the answer, watched the plan begin, opened Village life and read the Chronicle. Independent unaided playtest and physical Android NOT TESTED.
DECISIONS / EXPERIMENTS: Named households; petitions as commands with village defaults; rules v9 / schema 7; severe-thirst threshold and one hauler; coppiced woodcutting; production-based growth and arrival cooldown; two-cell lots; one shared KayKit atlas; compact petition on phones; house colour kept by building.
KNOWN GAPS: Exaggerated deer bodies are larger than homes and can crowd the village and hide Echo lanterns at close range. The whole-island Echo view makes Reedford's ghost house small. Petitions exist for water and growth only; no family relationships yet. Android texture memory rose by about 11 MB. Connected instrumentation suites, physical Android and unaided playtest were not run.
NEXT ACTION: Ask for Phase 07 acceptance. If accepted, begin Phase 08 by turning the three Fading Grove futures into a closing chapter state, then review wildlife scale near settlements against these captures before adding the next world-story.
PHASE APPROVAL: ACCEPTED (2026-11-22)
```

- Phase 07 Desktop Vulkan. [Survey](build/verification/village-07/desktop/desktop-survey-rain-300.png) · [Rain day](build/verification/village-07/desktop/desktop-hollowmere-rain-day.png) · [Drought at 420](build/verification/village-07/desktop/desktop-hollowmere-thirst-420.png) · [Well under construction](build/verification/village-07/desktop/desktop-hollowmere-well-work-600.png) · [Dusk](build/verification/village-07/desktop/desktop-hollowmere-dusk.png) · [Reedford](build/verification/village-07/desktop/desktop-flats-1500.png) · [Echo at Hollowmere](build/verification/village-07/desktop/desktop-echo-hollowmere-flats.png)
- Phase 07 Android GLES. [Petition](build/verification/village-07/android/android-petition.png) · [Endorsed](build/verification/village-07/android/android-endorsed.png) · [Village life](build/verification/village-07/android/android-village-panel.png) · [Village at dusk](build/verification/village-07/android/android-village-world.png) · [Reloaded village](build/verification/village-07/android/android-reloaded-village.png)
- Phase 07 verification. [Final host suite](build/verification/village-07/gradle-final-host.log) · [Shared iOS](build/verification/village-07/gradle-native.log) · [Save replays](build/verification/village-07/save-replay.log) · [Android v9 save](build/verification/village-07/android-v9-save.json) · [Desktop renderer log](build/verification/village-07/desktop-probe.log) · [Capture probe](build/verification/village-07/VillageProbe.kt) · [Android walkthrough](build/verification/village-07/android_village.py)

#### Phase 06 evidence

```text
ACTIVE PHASE / STATUS: 06 — COMPLETE, accepted
PLAYER-VISIBLE CHANGE: Living Echo compares two histories at the same moment in one full-frame island. Select either history, scrub or jump between named moments, and focus the actual field, drinking bank or grove sites. Alternate trees appear as pale crowns. The chronicle shows shared ancestry once and allows each logged intervention to be audited at the moment it ran. Continue this history returns to the selected branch and moment, paused.
VISUAL EVIDENCE: build/verification/living-echo-06 holds native Desktop Vulkan captures at 1152×648 and Android GLES captures at 1080×2424 and 2424×1080. Android also captured 1.3× text, Reduce Motion, the actual intervention result, and an eight-second silent recording at 720×1616. The packaged Desktop UI was exercised and visually reviewed at 2240×1300.
CODE CHANGED: Timeline and TimelineExplorer assemble inherited intervention trails, named turning points, and an immutable comparison from reconstructed snapshots. IslandSession rejects stale comparison results, replays one command outcome, and preserves the live history while comparing. Compose adds the slim Living Echo dock, site focus, natural-language scrub markers, details, and the route into a selected history. The Kool layer draws a single depth-tested Echo mesh in the visible scene and reuses it on a repeat switch.
BUILDS & TESTS: 143 host tests, 61 connected Android tests, and 8 targeted shared iOS simulator tests PASS. Android lint, Debug APK, Desktop package, and both shared iOS compilation targets PASS.
HOST AND PACKAGES: ./gradlew --offline --console=plain --max-workers=2 -Pkotlin.incremental=false :shared:jvmTest :shared:testAndroidHostTest :renderer:jvmTest :renderer:testAndroidHostTest :desktopApp:test :desktopApp:createDistributable :androidApp:assembleDebug :androidApp:lintDebug :shared:compileKotlinIosArm64 :shared:compileKotlinIosSimulatorArm64 --continue — PASS.
SHARED NATIVE: ./gradlew --offline --console=plain --max-workers=2 -Pkotlin.incremental=false :shared:iosSimulatorArm64Test --tests '*WorldShapingTest' --tests '*TimelineExplorerTest' — PASS. Xcode emitted debug-cache warnings.
CONNECTED ANDROID: ./gradlew --offline --console=plain --max-workers=2 -Pkotlin.incremental=false :shared:connectedAndroidDeviceTest :androidApp:connectedDebugAndroidTest --continue — PASS on retry, 59 shared and 2 app tests with zero failures or skips. The first attempt was blocked by automatic approval review. Gradle removed the emulator app at teardown. The verified APK and byte-identical disposable save were restored to the emulator.
SIMULATION / SAVE / BRANCH: At tick 240, rain and grove siblings have the identical warning hash 7aa3494f9b661522. Both interventions execute at 241. At 360, B2 leaves 12 water and six crops versus zero and two in C2. At 900, C2 has three rooted patches used by wildlife during replay. The emulator produced a 185,697-byte schema 6 save identical to the Phase 05 fixture. After a force-stop, it reopened Branch 3. A fresh JVM replay followed every branch’s ordered commands from its start and matched Main 5c6715ee9bf1559f, rain ee5d2afb4f668209, and grove 872f8b772885c3d5. Shared tests cover pending-command isolation, inherited events, same-tick command selection, stale results and adopting a history. Real rules-v5, v6 and v7 fixtures independently replayed with their original hashes.
PERFORMANCE: Apple M4 Vulkan at 1152×648, Echo on/off renderer p95 15.77/16.03 ms, Compose frame-delivery p95 24.30/24.71 ms, 114/113 draws, 13.23/12.98 MB of GPU buffers, and 27.98 MB of textures in both samples. The same camera stayed exact across 20 warm switches with the changed bitmap observed in 19–29 ms. Pixel 9 API 36 AVD at 1080×2424 sampled 17.53/17.56 ms renderer p95, 114–119/113 draws, and 3.81 MB buffers and 23.32 MB textures in both segments. PSS was 168/184 MiB at the sampled times. Twelve touch-to-changed-screenshot samples took 288–324 ms including ADB and PNG readback. These local measurements do not isolate compositor delay or establish a hardware baseline.
PLAYTEST: Played both forks from the warning in the packaged Desktop game and the Pixel 9 AVD. On Android, saved both siblings and Main, relaunched via Continue, compared the shared warning, inspected the harvest and grove, replayed the B2 result, rotated and checked larger text. The final Desktop package opened an isolated copy of the Android save, reproduced the two harvest values, and continued Branch 2 at 360. Live walkthroughs used the default branch labels. Compose tests verified custom naming. Independent unaided playtest and physical Android NOT TESTED.
DECISIONS / EXPERIMENTS: Used the selected single aligned scene. Moved place controls out of the portrait dock, softened the first wireframe trees, and cached the two orientations after reviewing the captures. The rules and schema stay at v8 and 6.
KNOWN GAPS: Taller pines can occlude some crowns. On short landscape, refocusing the site after rotation brings it clear of the dock. Current river differences are wet-bank conditions and access. Independent playtesting and physical Android are unverified.
NEXT ACTION: After Phase 06 acceptance, reopen the B2 and C2 siblings in Living Echo. Audit Hollowmere’s water, crop labor, stored food, homes, and visible settler behavior through the matched harvest and recovery. Trace the earliest meaningful settlement divergence through SettlementEconomy and SettlementModels before introducing new Phase 07 rules.
PHASE APPROVAL: ACCEPTED (2026-11-21)
```

- Phase 06 captures. [Android switch recording](build/verification/living-echo-06/android-living-echo.mp4) · [Dry harvest](build/verification/living-echo-06/android-reloaded-harvest-grove.png) · [Rain harvest](build/verification/living-echo-06/android-reloaded-harvest-rain.png) · [Harvest values](build/verification/living-echo-06/android-harvest-values.png) · [Alternate grove](build/verification/living-echo-06/android-reloaded-grove-echo.png) · [Rooted grove](build/verification/living-echo-06/android-reloaded-grove-present.png) · [Short landscape after focusing](build/verification/living-echo-06/android-landscape-refocused.png) · [1.3× text](build/verification/living-echo-06/android-large-text-grove-echo.png) · [Desktop Vulkan grove](build/verification/living-echo-06/desktop/desktop-grove-alternate.png)
- Phase 06 verification. [Final host and packages](build/verification/living-echo-06/gradle-final-host.log) · [Connected Android tests](build/verification/living-echo-06/gradle-connected.log) · [Post-test save verification](build/verification/living-echo-06/android-post-test.log) · [Shared iOS tests](build/verification/living-echo-06/gradle-native.log) · [Android save log](build/verification/living-echo-06/android-fresh-runtime.log) · [Android restart and matched moments](build/verification/living-echo-06/android-review-runtime.log) · [JVM full replay](build/verification/living-echo-06/android-save-full-replay.log) · [Legacy replays](build/verification/living-echo-06/legacy-and-phase05-replay.log) · [Packaged Desktop replay](build/verification/living-echo-06/desktop-packaged.log)
- Phase 06 timing. [Desktop Echo on](build/verification/living-echo-06/desktop-echo-on.log) · [Desktop Echo off](build/verification/living-echo-06/desktop-echo-off.log) · [Android Echo on and off](build/verification/living-echo-06/android-echo-off-runtime.log) · [Android switch samples](build/verification/living-echo-06/android-benchmark.log)

#### Phase 05 evidence

```text
ACTIVE PHASE / STATUS: 05 — COMPLETE, accepted
PLAYER-VISIBLE CHANGE: The Fading Grove stops at Hollowmere’s drought warning. Shape offers a temporary shower or six possible C2 planting sites. Players can preview, queue, cancel, watch a harvest, and return to the shared warning. Hollowmere’s water reserve, crop output, hardship, and recovery follow the simulation. Planted habitat takes time to root and is used by wildlife.
VISUAL EVIDENCE: build/verification/shaping-05 holds native Desktop Vulkan frames and drainage video at 1152×648. The packaged Desktop UI was played and reviewed at 2240×1300 window pixels. Pixel 9 GLES captures show portrait warning, rain and grove previews, both comparisons, and the reloaded grove at 1080×2424. The reloaded comparison was also reviewed at 2424×1080. The Android gameplay recording is 720×1616.
CODE CHANGED: EcologyEngine and WorldShaping own the branch-local opportunity, retained water, and planted root biomass. SettlementEconomy owns water supply, crop labor, and first shortage and recovery. Timeline and save adapters support the new command, cancel before execution, original warning navigation, canonical hashes, and schema 6. Compose adds Shape, a compact forecast, inspection, and comparison welfare. Kool adds target marks, local cloud, rain, wet soil and river rings, sapling emergence, and a settler response. The original shower chime reuses the existing sound settings and platform adapters.
BUILDS & TESTS: 141 host tests, 58 shared and 2 application tests on the Pixel 9 AVD, and 3 shared iOS simulator tests PASS across the final runs. Android lint, Debug APK, and Desktop distributable PASS. Both shared iOS compilation targets PASS.
FINAL HOST AND PACKAGES: ./gradlew --offline --console=plain --max-workers=2 -Pkotlin.incremental=false :shared:jvmTest :shared:testAndroidHostTest :renderer:jvmTest :renderer:testAndroidHostTest :desktopApp:test :desktopApp:createDistributable :androidApp:assembleDebug :androidApp:lintDebug :shared:compileKotlinIosArm64 :shared:compileKotlinIosSimulatorArm64 --continue — PASS.
SHARED DEVICE: ./gradlew --offline --console=plain -Pkotlin.incremental=false :shared:connectedAndroidDeviceTest :androidApp:connectedDebugAndroidTest --continue — shared 58 PASS. The app test initially looked for the old Restore Grove action. The next attempt reproduced a camera timing race and a separate Kotlin lint resolver crash in an existing test file.
ANDROID APP AND LINT FOLLOW-UP: ./gradlew --offline --console=plain --max-workers=2 -Pkotlin.incremental=false :shared:jvmTest :shared:testAndroidHostTest :renderer:jvmTest :renderer:testAndroidHostTest :desktopApp:test :desktopApp:createDistributable :androidApp:assembleDebug :androidApp:lintDebug :androidApp:connectedDebugAndroidTest --continue — PASS. Instrumentation waits for live terrain before injecting gestures. The final warning-ancestry fix also passed the packaged Android playthrough below.
FINAL PRESENTATION POLISH: ./gradlew --offline --console=plain --max-workers=2 -Pkotlin.incremental=false :renderer:jvmTest :desktopApp:createDistributable :androidApp:assembleDebug :androidApp:lintDebug --continue — PASS. The preceding landscape pass also ran :renderer:testAndroidHostTest and both shared iOS compilation targets.
SHARED NATIVE: ./gradlew --offline --console=plain --max-workers=2 -Pkotlin.incremental=false :shared:iosSimulatorArm64Test --tests '*WorldShapingTest' — 3 PASS. The Native linker emitted debug-cache warnings.
SIMULATION / SAVE / BRANCH: Seed 104729 with warning at 240. At 360, rain B2 had 12 water in reserve and six crops. Main and C2 had zero reserve and two crops. Main’s first shortage was at 360 and recovery at 540. At 900, C2 had three rooted groves, with wildlife feeding or resting there during replay. JVM, Android, and shared iOS agree on untreated 5c6715ee9bf1559f, rain ee5d2afb4f668209, and grove 872f8b772885c3d5. The Android schema-6 save held two forks from Main at 240, both commanded at 241. It reopened after force-stop. A JVM run rebuilt all three Android-saved histories from commands and verified real rules-v5, v6, and v7 saves. Shared tests also round-trip a queued action and active shower. In a separate 24,000-tick JVM stress run with recurring interventions, all three gallery islands sustained rabbits, deer, and foxes.
PERFORMANCE: Apple M4 Vulkan at 1152×648 sampled 16.02 ms renderer p95 and 25.34 ms Compose delivery p95, versus 15.67 and 25.15 ms in Phase 04. The first complete frame took 2.14 s versus 1.88 s in the earlier sample. The scene used 9.01 MB GPU buffers and 11.21 MB textures at the first completed frame, with 79–103 draw commands across the walkthrough. Pixel 9 API 36 AVD at 1080×2424 sampled 18.70 ms renderer p95 versus 19.17 ms, first asset frame 1.73 s versus 2.23 s, 77 opening draws and 142 MiB sampled PSS. The loaded Android comparison sampled 118–119 draws and 18.74 ms renderer p95. The separate M4 JVM stress run sampled 44–51 μs authoritative step p95. These are local samples.
PLAYTEST: Packaged Desktop and Android reached the named warning and chose both alternatives through ordinary player controls. Desktop showed 0 versus 12 water, 2 versus 6 crops, and later 0 versus 3 rooted groves in the same viewport. Android cancelled and requeued rain, played both forks, saved all three histories, and reopened both alternatives after force-stop. The first automated walkthrough missed the Save button’s clickable parent. Resuming through the normal Save button completed the check. The emulator’s previous Phase 04 test save was backed up byte-for-byte and also opened through Continue. Independent unaided playtest NOT TESTED.
DECISIONS / EXPERIMENTS: Kept one intervention until natural recharge, B2 targeting for immediate settlement relief, and C2 as the worked example of slower ecological recovery. Reserved root biomass while leaving surplus browse available. Added actual target framing and made the rain streaks originate below the cloud after reviewing the initial captures.
KNOWN GAPS: Physical Android and independent unaided play NOT TESTED. Closest views can crop the cloud and foreground pines can cover some planting marks. Hollowmere’s water-hauling response uses the existing settler model and a short path. Video captures are silent. Desktop time to the first complete frame was slower in this local sample.
NEXT ACTION: Review the matched harvest and rooted-grove captures. After Phase 05 acceptance, begin Phase 06 by auditing aligned switching and event replay using these three saved histories. Use the B2 shower and C2 grove as the first two spatial differences for the Living Echo.
PHASE APPROVAL: ACCEPTED
```

- Phase 05 Desktop Vulkan. [Rain target](build/verification/shaping-05/desktop/desktop-rain-preview.png) · [Untreated Hollowmere](build/verification/shaping-05/desktop/desktop-drought-360.png) · [Rain at the same harvest](build/verification/shaping-05/desktop/desktop-rain-360.png) · [C2 saplings](build/verification/shaping-05/desktop/desktop-saplings.png) · [Rooted C2](build/verification/shaping-05/desktop/desktop-rooted-grove.png)
- Phase 05 Android GLES. [Drought warning](build/verification/shaping-05/android-warning.png) · [Rain target](build/verification/shaping-05/android-rain-preview.png) · [Gameplay recording](build/verification/shaping-05/android-call-rain.mp4) · [Matched harvest](build/verification/shaping-05/android-harvest-comparison-details.png) · [Grove targets](build/verification/shaping-05/android-grove-preview.png) · [Reloaded rooted habitat](build/verification/shaping-05/android-rooted-values.png) · [Reloaded rain history](build/verification/shaping-05/android-reloaded-rain.png) · [Landscape comparison](build/verification/shaping-05/android-landscape-comparison.png)
- Phase 05 verification. [Final host suites and packages](build/verification/shaping-05/gradle-warning-ancestor.log) · [Shared Android device tests](build/verification/shaping-05/gradle-android-device.log) · [Android app and lint rerun](build/verification/shaping-05/gradle-final-validation.log) · [Presentation polish](build/verification/shaping-05/gradle-polish.log) · [Shared iOS replay](build/verification/shaping-05/gradle-ios-drought.log) · [Android save and reload](build/verification/shaping-05/android-save-reload.log) · [Three-history save fixture](build/verification/shaping-05/android-v8-save.json) · [Saved histories rebuilt on JVM](build/verification/shaping-05/android-full-replay.log) · [Legacy saves](build/verification/shaping-05/legacy-replay.log) · [Stress run](build/verification/shaping-05/shaping-stress.log) · [Desktop performance](build/verification/shaping-05/desktop-runtime.log) · [Android performance](build/verification/shaping-05/android-opening-runtime.log)

#### Phase 04 evidence

```text
ACTIVE PHASE / STATUS: 04 — COMPLETE, accepted
PLAYER-VISIBLE CHANGE: Named rabbits, deer, and foxes share the three islands. Follow tracks the selected animal while time runs, even after closing the inspector. Wildlife history records births, new feeding grounds, and losses. Its entries focus the animal or the site. Grove restoration changes available shelter and forage, with observable population differences in matched histories.
VISUAL EVIDENCE: build/verification/wildlife-04/desktop-review contains 1152×648 Vulkan sunlight, dusk, rain, fox and pursuit close-ups, and matched futures. Native Desktop pursuit video is 1152×648. Android GLES captures record Pippin, Russet, daylight, rain, dusk, the matched histories and their reload at 1080×2424. The Android gameplay recording shows Pippin approaching cover at 720×1616, and the landscape inspector was captured at 2424×1080.
CODE CHANGED: Shared Wildlife and EcologyEngine own habitat, autonomy, resource demand, age and predation. TimelineSaveCodec writes schema 5 for rules v7. IslandSession distinguishes rules versions on identical terrain. Compose adds wildlife inspection, playback during follow, history and comparison details. Kool uses the bundled Kenney fox and custom coats, actions, camera framing, contact shadows and berry shrubs.
BUILDS & TESTS: 134 host tests and 57 connected Pixel 9 AVD tests PASS across the verification and follow-up runs, with no skips in the final reports. Both shared iOS targets compile. Initial full run failed the Android app startup test, which found the text before the clickable launch button was ready. The follow-up added a semantic launch click and updated the return-to-history label. Both Android app tests then passed.
HOST, BUILD AND SHARED DEVICE: ./gradlew --offline --console=plain :shared:jvmTest :shared:testAndroidHostTest :renderer:jvmTest :renderer:testAndroidHostTest :desktopApp:test :desktopApp:assemble :desktopApp:createDistributable :androidApp:assembleDebug :androidApp:lintDebug :shared:compileKotlinIosArm64 :shared:compileKotlinIosSimulatorArm64 :shared:connectedAndroidDeviceTest :androidApp:connectedDebugAndroidTest --continue
ANDROID APP RETRY: ./gradlew --offline --console=plain :androidApp:connectedDebugAndroidTest — PASS.
FINAL PACKAGES: ./gradlew --offline --console=plain :renderer:jvmTest :renderer:testAndroidHostTest :desktopApp:createDistributable :androidApp:assembleDebug :androidApp:lintDebug — PASS.
SHARED NATIVE: ./gradlew --offline --console=plain :shared:iosSimulatorArm64Test --tests '*WildlifeTest' — 5 PASS with exact shared hashes. The Native linker printed debug cache warnings, and the tests ran successfully.
SIMULATION / SAVE / BRANCH: Each gallery island sustained all three species for 24,000 ticks. Valley hash f2bc429d02f45a8d, Cairnwater 4a69d3a852cf1a14, and Heron Reach daffc693157f3e49 matched across JVM, Android and shared iOS. In seed 104729, fork at 260 and plant once in C2. At 900, Main had six rabbits, while the planted history had eight. Shared replay verified animals using the restored patch, and Android saved and reloaded both hashes a53706087e3542d9 and bdb89cca564bb231. Real Phase 02 and Phase 03 save artifacts kept their terrain digests and both original branch hashes.
PERFORMANCE: Apple M4 Vulkan at 1152×648 sampled 15.67 ms renderer p95 and 25.15 ms Compose delivery p95, versus 16.02 and 25.44 ms in Phase 03. The final sampled first complete frame was 1.88 s versus 1.29 s, with 77–79 draws, 9.01 MB GPU buffers and 11.21 MB textures. Process RSS was 614 MiB in that sample. Pixel 9 API 36 AVD at 1080×2424 sampled 19.17 ms renderer p95, 2.23 s first asset frame, 77 draws, 2.53 MB GPU buffers, 12.13 MB textures and 142 MiB PSS. Phase 03 AVD sampled 17.67 ms, 2.49 s, 65 draws and 148 MiB PSS. JVM simulation p95 was 66–85 μs across the three 24,000-tick tests. Shared iOS simulator p95 was 1.59–1.78 ms. These are local samples.
PLAYTEST: Android Continue reopened the v6 valley. The gallery opened a new v7 history of the same island. The walkthrough followed Russet and Pippin, looked at rain and dusk, rewound, planted C2, switched the aligned comparison, and reopened both histories after force-stop. Desktop Compose tests exercised wildlife selection, camera persistence, and the compact inspector. Native Vulkan review captured the pursuit and both matching branches, then resized and reopened the scene. Independent unaided playtest NOT TESTED.
DECISIONS / EXPERIMENTS: Kept visible pursuit and implied catches. Rooted groves become refuges. Wildlife and the village draw on the same forage. Balanced upland fox replacement with a shared wet-season den and reserve thresholds. Retained the smooth water apron after the dry-bank rendering trial introduced visual gaps.
KNOWN GAPS: Physical Android and independent playtesting NOT TESTED. Desktop interaction uses Compose tests paired with native Vulkan captures. Tight banks and foreground pines can still partially obscure animals. Android frame pacing and Desktop first-frame loading were slower in the sampled final runs.
NEXT ACTION: Review the pursuit capture, Android wildlife inspector, and matched C2 branches. After Phase 04 acceptance, begin Phase 05 by auditing Restore Grove, regional targeting, and intervention feedback against the drought dilemma before adding another player power.
PHASE APPROVAL: ACCEPTED
```

- Phase 04 Desktop Vulkan. [Opening](build/verification/wildlife-04/desktop-review/desktop-opening.png) · [Fox](build/verification/wildlife-04/desktop-review/desktop-fox.png) · [Pursuit recording](build/verification/wildlife-04/desktop-reviewed-pursuit.mp4) · [Rain](build/verification/wildlife-04/desktop-review/desktop-rain.png) · [Dusk](build/verification/wildlife-04/desktop-review/desktop-dusk.png) · [Main](build/verification/wildlife-04/desktop-review/desktop-main.png) · [C2](build/verification/wildlife-04/desktop-review/desktop-grove.png)
- Phase 04 Android. [Russet](build/verification/wildlife-04/android-fox.png) · [Pippin in flight](build/verification/wildlife-04/android-flight.png) · [Pippin gameplay recording](build/verification/wildlife-04/android-pippin.mp4) · [Landscape inspector](build/verification/wildlife-04/android-pippin-landscape.png) · [Matched populations](build/verification/wildlife-04/android-comparison.png) · [Reloaded Main](build/verification/wildlife-04/android-reloaded-main.png) · [Reloaded C2](build/verification/wildlife-04/android-reloaded-grove.png) · [Phase 03 Continue](build/verification/wildlife-04/android-phase03-continue.png)
- Phase 04 verification. [Final packages and lint](build/verification/wildlife-04/gradle-packages-final.log) · [Shared device and host suites](build/verification/wildlife-04/gradle-final-acceptance.log) · [Android app tests](build/verification/wildlife-04/gradle-android-instrumentation.log) · [Shared iOS run](build/verification/wildlife-04/gradle-ios-final.log) · [Android save hashes](build/verification/wildlife-04/android-save-hashes.log) · [Earlier saves](build/verification/wildlife-04/legacy-replay.log) · [Desktop timing](build/verification/wildlife-04/desktop-review-runtime.log) · [Android timing](build/verification/wildlife-04/android-opening-runtime.log)

#### Phase 03 evidence

```text
ACTIVE PHASE / STATUS: 03 — COMPLETE, accepted
PLAYER-VISIBLE CHANGE: The Fading Grove follows a living river from pine hills to Reedmouth. Explore Islands opens Cairnwater and Heron Reach. Inspect focuses named landmarks and describes local soil conditions. The valley recedes between rains and refills with visibly wetter shallows. Dry-season rain and wetter seasonal storms change forage recovery and crop yield. Restore Grove still uses the timeline command system.
VISUAL EVIDENCE: build/verification/geography-03 contains three native 1152×648 Desktop Vulkan landscapes and viewpoints, plus valley dry, rain, storm, dusk, animal focus, and aligned histories. Android GLES captures record the three islands, landmark inspection, rain and dusk, and the original and reloaded comparisons at 1080×2424. The final art pass was recaptured on Android in portrait and at 2424×1080 landscape.
CODE CHANGED: Shared WatershedGenerator, Watershed, Environment, EcologyEngine and SettlementEconomy, versioned saves and scene projections, island and landmark controls in Compose, shared Kool inland water and landscape palette, arrival cameras, regression fixtures, and README walkthrough.
BUILDS & TESTS: PASS. ./gradlew --offline --console=plain :shared:jvmTest :shared:testAndroidHostTest :renderer:jvmTest :renderer:testAndroidHostTest :desktopApp:test :desktopApp:assemble :desktopApp:createDistributable :androidApp:assembleDebug :androidApp:lintDebug :shared:compileKotlinIosSimulatorArm64 :shared:connectedAndroidDeviceTest :androidApp:connectedDebugAndroidTest --continue
RENDERER FOLLOW-UP: PASS. ./gradlew --offline --console=plain :renderer:jvmTest :renderer:testAndroidHostTest :desktopApp:createDistributable :androidApp:assembleDebug :androidApp:lintDebug. The final host and connected results had 124 host tests and 52 Android AVD tests without failures or skips.
FINAL REPLAY AND CONTRAST FOLLOW-UP: PASS. ./gradlew --offline --console=plain :shared:jvmTest :shared:testAndroidHostTest :renderer:jvmTest :desktopApp:createDistributable :androidApp:assembleDebug :androidApp:lintDebug :shared:compileKotlinIosSimulatorArm64 :shared:connectedAndroidDeviceTest. The fixture now pins the same C2 command from the Android save, at tick 261 with region index 9 and sequence zero.
SHORT LANDSCAPE OPENING FOLLOW-UP: PASS. ./gradlew --offline --console=plain :renderer:jvmTest :desktopApp:createDistributable :androidApp:assembleDebug :androidApp:lintDebug :shared:compileKotlinIosSimulatorArm64.
SIMULATION / SAVE / BRANCH: PASS. All sampled v2 terrain drained to the coast and had two reachable drinking banks. Valley digest b9916f92a21d5e42. Android rewound to tick 260, planted in C2, and compared at 900. Main aaed96a98a819688 and child 283d1af71c324474 matched after schema 4 save and relaunch. A save generated by the Phase 02 package reopened on worldgen v1 with terrain cb401f464692a079 and both rules-v5 hashes intact.
PERFORMANCE: Apple M4 Vulkan at 1152×648. The final valley opening sampled 16.02 ms renderer p95 and 25.44 ms Compose delivery p95, against 16.03 and 23.13 ms for the same-resolution coastal control. Complete Compose frame took 1.29 s against 0.99 s. Draws increased from 62 to 67. GPU buffers increased from 5.59 to 8.59 MB with textures stable at 9.81 MB. Sampled process RSS was 404 MiB versus 391 MiB. Warmed terrain-generation samples had medians of 5.26 ms and 5.33 ms for Cairnwater and Heron Reach. Valley sampled 5.46–12.58 ms across two runs and the coast sampled 0.66 ms. Pixel 9 API 36 AVD at 1080×2424 sampled 17.67 ms renderer p95, 65 draws, 2.41 MB buffers, 12.13 MB textures, and 148 MiB PSS. The coastal Phase 02 sample was 18.48 ms, 63 draws, 1.61 MB buffers, 12.13 MB textures, and 141 MiB PSS. First complete asset frame took 2.49 s versus 2.14 s, measured after the world was handed to the renderer. Cold valley terrain generation took 1.02 s and warm generation for the two other islands took 26–40 ms. These are local measurements.
PLAYTEST: The Android walkthrough used gallery, landmark focus, timeline controls, and Continue for both old and new histories. It reached dry soil at 299, rain at 479, dusk at 645, and wet-season rain at 1450. Desktop's normal gallery and landmark controls ran in Compose tests for all three worlds. The packaged renderer separately verified Vulkan output, resize, and scene release. Independent playtest NOT TESTED.
DECISIONS / EXPERIMENTS: Used a priority flood and shared integer terraces over the existing grid. Retained the same clock, timeline, commands, and world dimensions. Smoothed banks and unified the coast and freshwater treatment after reviewing the first device and Vulkan captures.
KNOWN GAPS: Physical Android and independent unaided play NOT TESTED. Computer Use approval for interaction with the isolated packaged Desktop review window was denied. Desktop interaction evidence is from Compose tests and native renderer probes. World generation takes longer than v1 and the larger terrain raises GPU buffer use. A few narrow shoreline steps remain visible close up.
NEXT ACTION: Implement rules v7 wildlife decisions and preserve rules-v6 histories on the same terrain. Verify against the three Phase 03 terrain profiles.
PHASE APPROVAL: ACCEPTED
```

- Phase 03 Desktop Vulkan. [Seed gallery](build/verification/geography-03/desktop-seed-gallery.jpg) · [Reedmouth](build/verification/geography-03/valley-final/desktop-landmark-estuary.png) · [Cairnwater Lake](build/verification/geography-03/cairnwater-final/desktop-landmark-basin.png) · [Heron Marsh](build/verification/geography-03/heron-reach-final/desktop-landmark-basin.png) · [Wet-season storm](build/verification/geography-03/valley-final/desktop-wet-season.png)
- Phase 03 Android GLES. [Valley portrait](build/verification/geography-03/android-final-valley-opening.png) · [Valley landscape](build/verification/geography-03/android-final-valley-landscape.png) · [Cairnwater](build/verification/geography-03/android-final-cairnwater.png) · [Heron Reach](build/verification/geography-03/android-final-heron-reach.png) · [Landmark inspection](build/verification/geography-03/android-reedmouth.png) · [Tracked deer](build/verification/geography-03/android-valley-deer.png) · [Dusk](build/verification/geography-03/android-dusk.png) · [Main at tick 900](build/verification/geography-03/android-main-900.png) · [C2 planted history](build/verification/geography-03/android-riverbank-900.png) · [Comparison after relaunch](build/verification/geography-03/android-v6-reloaded.png) · [Phase 02 save reopened](build/verification/geography-03/android-phase02-continue.png)
- Phase 03 logs. [Acceptance suite](build/verification/geography-03/gradle-acceptance.log) · [Renderer follow-up](build/verification/geography-03/gradle-renderer-final.log) · [Final replay follow-up](build/verification/geography-03/gradle-final-replay.log) · [Short landscape follow-up](build/verification/geography-03/gradle-opening-final.log) · [Android walkthrough](build/verification/geography-03/android-walkthrough.log) · [Android runtime](build/verification/geography-03/android-runtime.log) · [Android final performance](build/verification/geography-03/android-final-runtime.log) · [Desktop valley performance](build/verification/geography-03/desktop-valley-final.log) · [Desktop coastal control](build/verification/geography-03/desktop-legacy.log)

#### Phase 02 evidence

```text
ACTIVE PHASE / STATUS: 02 — COMPLETE, accepted
PLAYER-VISIBLE CHANGE: The Fading Grove has matte pine variants, wind in the canopy and meadow, softer terrain transitions, coastal foam, rain ripples, and clear dusk. Springs follow terrain and inhabited homes emit chimney smoke. A new creature focus chooses a more open viewing angle. Original surf, wind, rain and bird layers respond to the displayed scene.
VISUAL EVIDENCE: build/verification/presentation-02 holds native Vulkan frames at 1152×648, Android GLES frames at 1080×2424 and 2424×1080, and a recording with live game audio. The packaged Desktop opening and inspector were also reviewed at 2240×1300 window pixels.
CODE CHANGED: Shared day-phase projection, Compose ambience and camera requests, compact inspector, Android and Desktop sound adapters, Kool coastal shaders and scene details, pine GLBs and original audio assets, Desktop frame delivery and stable camera focus, tests, and asset provenance in README.
BUILDS & TESTS: PASS. ./gradlew --offline --console=plain :shared:jvmTest :shared:testAndroidHostTest :renderer:jvmTest :renderer:testAndroidHostTest :desktopApp:test :desktopApp:assemble :desktopApp:createDistributable :androidApp:assembleDebug :androidApp:lintDebug :shared:compileKotlinIosSimulatorArm64 :shared:connectedAndroidDeviceTest :androidApp:connectedDebugAndroidTest
FOLLOW-UP BUILDS & TESTS: PASS. ./gradlew --offline --console=plain :renderer:jvmTest :desktopApp:createDistributable. The rebuilt Desktop package passed input and compact inspector checks. Across the two runs, 115 host tests and 48 Pixel 9 API 36 AVD tests passed with zero failures or skips.
SIMULATION / SAVE / BRANCH: PASS. The rules-v5 C2 fork at tick 900 returned Main 5dce8f94926c905d and Restored Forest 4689c1bc6a8d2df2. The branch grew six saplings and stored 124 wood versus Main's 76. Android saved two histories and restored the same Main hash at tick 900 after restart.
PERFORMANCE: Apple M4 Vulkan at 1152×648, renderer p50/p95 12.98/15.97 ms and Compose delivery 22.12/26.44 ms. First complete Compose frame 1.29 s. Opening had 62 draw commands, 5.59 MB GPU buffers, 9.81 MB textures and 373 MiB process RSS. Camera input to changed Desktop bitmap was 11 ms in one probe, before window presentation. Pixel 9 API 36 AVD GLES at 1080×2424 showed renderer p50/p95 16.65/18.48 ms, first complete asset frame 2.14 s, 63 draw commands, 1.61 MB buffers, 12.13 MB textures and about 141 MiB process PSS. The latest injected drag reached the first changed Android screenshot in 69 ms including capture overhead. These are local samples.
PLAYTEST: Desktop packaged play reached the live story and tracked a deer. The final package showed wheel zoom, arrow orbit, Shift-arrow pan and Home reset. The Compose input test exercised primary and secondary drags and keyboard zoom. Native captures covered sun, rain, dusk and aligned histories, with a resize and two clean scene disposals. Android reached rain at 330 and clear dusk at 645, rotated, rewound to 300, planted on C2, compared both histories at 900, and reloaded the save. Instrumentation exercised reduced motion, orbit, pan, pinch, recreation, and background pause. Independent unaided playtest NOT TESTED.
DECISIONS / EXPERIMENTS: Kept both current renderer adapters and the landscape composition. Reworked matte pine materials and stripe-like water after reviewing native frames. Adopted the 60 FPS Desktop scheduling cap and unscheduled single-in-flight readback after separate pacing trials. The device tests also exposed and verified a fix for a stale Reduce Motion camera request.
KNOWN GAPS: Physical Android and independent player reactions NOT TESTED. The final compact Android inspector recapture was BLOCKED by automatic approval review because the workspace was out of credits. The shared compact layout has a passing Compose regression test. Cold Android shader and asset startup was slower in the matched AVD samples. The island's geography and landmark variety remain Phase 03 work.
PHASE APPROVAL: ACCEPTED
```

- Phase 02 Desktop Vulkan frames. [Opening](build/verification/presentation-02/release/desktop-opening.png) · [Rain](build/verification/presentation-02/release/desktop-rain.png) · [Dusk](build/verification/presentation-02/release/desktop-dusk.png) · [Tracked deer](build/verification/presentation-02/release/desktop-creature.png) · [Main at 900](build/verification/presentation-02/release/desktop-main-900.png) · [Restored Forest at 900](build/verification/presentation-02/release/desktop-grove-900.png)
- Phase 02 Android gameplay. The MP4 review copy raises the original game audio by 16 dB. [Opening](build/verification/presentation-02/android-release-opening.png) · [Gameplay with sound](build/verification/presentation-02/android-coastal-review.mp4) · [Original recording](build/verification/presentation-02/android-coastal-release.webm) · [Rain](build/verification/presentation-02/android-rain-portrait.png) · [Dusk in landscape](build/verification/presentation-02/android-dusk-landscape.png) · [Planted history](build/verification/presentation-02/android-comparison-grove.png) · [Reloaded comparison](build/verification/presentation-02/android-comparison-reloaded.png)
- Phase 02 verification. [Acceptance suite](build/verification/presentation-02/gradle-acceptance.log) · [Desktop camera tests](renderer/build/test-results/jvmTest/TEST-id.branchworld.renderer.DesktopCameraInputTest.xml) · [Compact inspector test](renderer/build/test-results/jvmTest/TEST-id.branchworld.renderer.DesktopEcologyUiTest.xml) · [Desktop performance](build/verification/presentation-02/desktop-release.log) · [Android playtest](build/verification/presentation-02/android-walkthrough.log) · [Android runtime](build/verification/presentation-02/android-runtime.log)

#### Phase 01 evidence

- Android opening and play. [Before opening](build/verification/experience-01/android-before-opening.png) · [Before gameplay](build/verification/experience-01/android-before-world.png) · [Live opening](build/verification/experience-01/android-after-opening.png) · [Gameplay](build/verification/experience-01/android-after-world.png) · [Landscape](build/verification/experience-01/android-landscape-opening.png) · [Landscape at 1.3× text](build/verification/experience-01/android-landscape-large-text.png)
- Android intervention and branches. [Creature focus](build/verification/experience-01/android-creature-focus.png) · [Planting preview](build/verification/experience-01/android-planting-preview.png) · [Main](build/verification/experience-01/android-final-comparison-main.png) · [Restored Forest](build/verification/experience-01/android-final-comparison-grove.png) · [Comparison details](build/verification/experience-01/android-final-comparison-details.png)
- Desktop native Vulkan frames. [Prior wide render](build/verification/phase05/macos-wildlife-tick-0.png) · [Opening composition](build/verification/experience-01/desktop-opening.png) · [Rain](build/verification/experience-01/desktop-rain.png) · [Creature focus](build/verification/experience-01/desktop-creature.png) · [Main](build/verification/experience-01/desktop-main-900.png) · [Restored Forest](build/verification/experience-01/desktop-grove-900.png)
- Verification logs. [Gradle](build/verification/experience-01/gradle-acceptance.log) · [Android walkthrough](build/verification/experience-01/android-walkthrough.log) · [Android save, restore, and renderer](build/verification/experience-01/android-runtime.log) · [Desktop timing](build/verification/experience-01/desktop-perf-1152.log)

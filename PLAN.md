# BranchWorld — Vision & Master Plan

**Tagline:** *Create a world. Fill it with life. Watch what it becomes.*

**Purpose:** This file defines **WHAT** BranchWorld must become, what players should experience, the major delivery outcomes, concrete acceptance gates, and the persistent progress record. `AGENTS.md` defines **HOW** autonomous agents work.

**Development philosophy:** **Maximum quality and originality. No invented development schedule, no artificial feature ceiling, and no contest-only shortcut.** Pursue a standout premium game, integrate it steadily, and refuse shallow or visually weak results. This is ambition with real engineering evidence, not a promise that every idea can be built immediately.

**Context:** The Kotlin Multiplatform Contest remains a key standard of excellence (novelty 40%, genuine KMP utilization/shared Kotlin 40%, coding conventions 20%). The competition has its own external submission requirements; **this product plan does not use a deadline to cap the game's ambitions.** Verify contest-specific information from https://kotlinconf.com/contest-rules/ when relevant.

**Implementation honesty:** Only real code, executed tests and authentic captures count as evidence. Audit the repository before changing a system or marking a milestone complete.

---

## 1. The game we are building

### High concept

**BranchWorld is a premium, single-player, visually captivating, three-dimensional living-world sandbox.** Players create small 3D worlds, set down animals, plants and (later) structures by hand, and watch them live: rabbits graze and dig warrens, wolves form packs and hunt, deer keep together, populations boom and crash, fire sweeps through dry woods and grass returns. The player can step in at any moment, or rewind and try the same world another way.

The player is not a manager of numbers or a follower of objectives. They are a curious experimenter: **"What happens if I put wolves here? What if there were more trees? What if I take the wolves away?"**

The signature is **experimentation with alternate histories**: the same world, run twice from one moment with one change, compared side by side. The same rules generate every outcome. The game never replaces real consequences with fictional charts, scripted events or cosmetic effects.

### Premium promise

A player should want to launch BranchWorld because:

1. **The world is breathtaking.** At a glance it resembles a carefully art-directed living toy diorama, not a debugging viewport.
2. **Life is worth caring about.** A named rabbit escaping into its warren, a pack howling at dusk, a family of pups: changes are local, individual and visible.
3. **Their hand matters.** Three wolves set down beside a warren change everything: rabbits learn to stay near cover, the pack grows, then hunger comes. Consequences emerge from systems, not scripts.
4. **Time is expressive.** Pause, speed up, rewind and compare without erasing earlier histories.
5. **Every world tells a different story.** Procedural islands, weather, fire and the creatures' own decisions make each world unfold differently, yet understandably.
6. **There is always something to try.** A growing palette of species, plants, landforms and weather, named worlds the player keeps, and discoveries the world itself records give endless reasons to return.

### What it is NOT

- A screensaver with a small floating island and a collection of counters.
- A tech demo whose controls are raw ticks and diagnostics.
- A menu-heavy idle simulator where the world is merely decorative.
- A random generator with no coherent ecological consequences.
- A mission game: no objectives, requests or guided steps stand between the player and their world.
- A scripted experience where outcomes claim to be simulated but are predetermined.
- A clone of an existing ecology, god or sandbox game.

### Inspiration without imitation

Study why these experiences work: [Tiny Glade](https://store.steampowered.com/app/2198150/Tiny_Glade/) for scene intimacy and satisfying craft; [Terra Nil](https://store.steampowered.com/app/1593030/Terra_Nil/) for legible environmental transformation; [Equilinox](https://store.steampowered.com/app/853550/Equilinox/) for calm ecosystem building. Borrow *design lessons*, never specific art, interfaces, mechanics, marketing or identities. **BranchWorld's differentiator is autonomous life the player places directly, and alternate histories of the same world compared in place.**

---

## 2. The player loop

**Create → Populate → Observe → Experiment → Discover.**

| Beat | Player experience | Required in-game manifestation |
|---|---|---|
| **Create** | Make a world of their own | Pick an island shape or seed, Empty land or Wild, name it; it gets its own save |
| **Populate** | Set life down by hand | A palette of animals and plants; tap or click the 3D world; a ghost shows where and whether it can go; things drop in at once |
| **Observe** | Watch life go its own way | Readable behaviour (grazing, alarm, flight, stalking, pack hunts, howling, digging, courting), a creature card that says what an animal is doing and why, time controls from pause to 8× |
| **Experiment** | Change something and see what follows | Lift animals out, add more, plant trees; rewind to any moment and start another history beside the first; compare them |
| **Discover** | Notice what the world did | A truthful chronicle of moments (first kill, pack formed, escaped, starved, boom, crash, first litter) with causes, populations over time |

**Principle:** The player never has to do anything. A world left alone keeps living; every change the player makes is a question the world answers through its own rules.

### The player's hand — direct placement, autonomous life

The player has direct, cell-level verbs: **set down** rabbits, wolves, deer and foxes; **plant** young pines and grass; **lift out** an animal or a planting; **look** at and follow any creature. Animals are placed, never steered: once set down they decide everything themselves. Later tools add terrain shaping, water, structures, weather and more species, always as shared, tick-indexed commands that replay exactly.

### Depth without tedious micromanagement

Players decide **what lives where**, not where an animal walks. They can set down, lift out, inspect and follow a creature, but wild animals always make their own decisions. The most interesting setups have delayed, interpretable ripple effects.

### Failure and recovery

Worlds are allowed to struggle, collapse, recover and change. A species dying out is a story and a new question, not a failure screen. The player keeps every earlier history and may branch again.

---

## 3. A distinctive art and presentation system

### World composition

The opening is a **hero image generated by the actual game renderer**: the live island fills the screen behind a small title shelf. Most of the play viewport is occupied by layered, readable landscape with creatures large enough to read; the camera moves smoothly between survey views, close creature tracking and framing a selected animal.

- Treat world scale, zoom, focal length, terrain composition and viewport layout as first-class art problems; the 3D world should occupy roughly **two-thirds or more** of the usable frame in the standard hero shot.
- Keep diagnostics and tick counters behind the developer setting. Normal play shows the world's name, time, population pegs, the dock and contextual inspection.
- Use close creature moments to create empathy: a creature card, follow camera and readable animation.
- Animate **the world itself**: grass, trees, water, weather, fire, animal gait and behaviour, the placement ghost and drop-in.

### Art language

- **Visual language:** warm toy-diorama low-poly forms, strong silhouettes, painted materials, lush vegetation variation, soft directional lighting, rich water, readable atmospheric perspective. Cohesive rather than photorealistic.
- **Palette:** vivid but controlled; lighting and weather alter tone without losing readability.
- **Camera:** graceful orbit, pan, zoom, tap-to-select, follow-animal, safe bounds, smooth touch/mouse input and adjustable motion intensity.
- **Assets:** use the downloaded KayKit/Kenney packs as material; normalize scale, pivots and materials so they read as one set. Select or author better assets when a pack cannot meet the target.
- **Sound:** wind, water, birds, wildlife calls, weather and fire, restrained music, and interaction sounds.
- **UI:** the Painted Wooden Toy Set (see `DESIGN.md`): a few chunky wooden pieces over a full-bleed world; no developer-dashboard identity.

### Non-negotiable image-quality test

Capture authentic game screenshots at representative Desktop and Android sizes under: **a sunny day, dusk, rain or fire, a close-up creature, and two histories compared.** Review clarity at thumbnail size and up close. Any phase modifying graphics must keep good framing and avoid popping, clipping, broken shadows, unreadable silhouettes or clutter. A concept illustration is not evidence of in-engine quality.

---

## 4. The first essential experience — rabbits and wolves

**A player creates a world, places rabbits and wolves, and watches them behave and interact without further commands.**

- **Create:** New world → an island (gallery or seed) → *Empty land* → Create. The island opens with grass, pines and water and no animals.
- **Populate:** the palette's Rabbit peg is chosen; a green peg shows where a rabbit can go (madder where it cannot, with the reason). Clicks or taps set rabbits down; they drop in at once, even while paused. Wolf, then a few clicks on the far shore.
- **Observe:** rabbits graze, dig warrens near cover and pair off; when a moving wolf comes within sight they thump, the alarm spreads to neighbours, and they run for warren or thicket, jinking when close. Lone wolves find each other and form a pack with a den, howl at dusk, rest by day and hunt at night: the leader chases while the others cut ahead. Some rabbits escape, some are caught; fed packs raise pups; packs that eat everything starve. Each creature's card says what it is doing ("Chasing Clover with the pack", "Hiding from Fern").
- **Experiment and discover:** speed up to 8×, rewind to the moment the wolves arrived and start a history without them, and read in *What happened* why the rabbits boomed in one and crashed in the other.

**Playable proof:** an independent tester does all of this with normal controls, without diagnostics or a developer's explanation, and the same placements replay to the same world on every target.

---

## 5. World systems and how they connect

BranchWorld's depth comes from **interacting systems** that the player can see and reach into. Never build a subsystem as a disconnected collection of toggles.

```text
Procedural island: terrain, water, soils
                  |
                  v
   Weather, seasons, moisture, fire
                  |
                  v
     Plants: grass, trees, forage, cover, regrowth
                  |
                  v
   Animals: needs, perception, fear, packs, mating, death
                  |
                  v
       Populations, booms and crashes
                  |
                  v
   Chronicle of moments with recorded causes
                  |
                  v
      Alternate histories of the same world

   The player's hand (set down, plant, lift out, later shape) enters at every level as recorded commands.
```

### Authoritative shared Kotlin model

- Seed, rules version, world state, tick index and stable entity IDs.
- `WorldCommand` for every player action, with an explicit tick and ordering (today: place a creature, remove a creature, plant, clear a planting).
- One engine step as the only way state advances; creatures decide from the previous tick's snapshot with counter-based random rolls, so processing order never changes an outcome.
- Island generation for terrain, biomes, water networks and props.
- A chronicle of world events with causes recorded only where the rules actually know them.
- Read-only render projections (creature poses with thoughts and pace, life census, packs, warrens, fire) for the UI and renderer.
- Timeline branches, snapshots and replay; a versioned save schema behind Android and Desktop storage adapters.

**Hard invariant:** simulation rules never depend on Kool scene nodes, Compose recomposition timing, wall-clock time or device scheduling. Do not call anything deterministic unless cross-target replay fixtures agree.

### Deterministic simulation is a feature

The same seed, rules, command sequence and tick recreate the same state and canonical hash. That makes histories comparable and shareable, saves portable and troubleshooting possible.

### Scalable world representation

Design spatial queries, instanced vegetation, animation budgets, camera-based level of detail and culling carefully, and size population caps against measured step costs on named devices. Never let frame rate change authoritative outcomes.

---

## 6. Development phases (outcome-driven, not timeboxed)

Phases are milestones with **observable player value**. Tests, art, camera, input, sound, saves and performance work continue across every phase. A verified playable build stays playable as systems grow.

### Foundations already built (Phases 01–09)

These work today and are the base for every later phase:

- **Rendering:** Kool on Android (GLES under Compose) and Desktop (Vulkan delivered to Compose), full-bleed islands with water, lighting, weather, day and night, instanced pines, creature models with animation, and fire, smoke and regrowth.
- **Islands:** procedural watershed islands from a seed (terrain, drainage, lakes, soils, biomes, props) and three named gallery islands: The Fading Grove, Cairnwater and Heron Reach.
- **Rules and determinism:** fixed-tick shared-Kotlin rules with versioned rule sets, canonical-bytes hashing, and replay fixtures that agree on JVM, Android and iOS.
- **Ecology:** forage and regrowth driven by soil moisture, seasons and rain, wildfire with wind, curing and succession, and named wildlife with needs and perception.
- **Time:** timeline branching, rewind and comparison, a chronicle with "Why?" cause chains, bookmarks, and BW1 share codes that rebuild every history.
- **Saves:** per-world atomic slots, a player profile, save integrity checks and safe handling of unreadable or newer saves.
- **Interface:** the Painted Wooden Toy Set (`DESIGN.md`), settings for sound, Reduce Motion and text size, and photo mode.
- **Tooling:** Desktop GPU capture harness, Compose UI tests, Android instrumentation tests and gated simulation probes.

### PHASE 10 — Living Sandbox: Create, Populate, Observe

**Player milestone:** A player creates a world, sets down rabbits and wolves by touching the 3D island, and watches them behave and interact on their own.

**Build:** Rules v12 living worlds (empty or wild start), direct placement commands for animals and plants with reasons when refused, wolves with packs, dens, howling and cooperative hunts, rabbits with sight, alarm thumps, warrens, sexes and litters, 8-direction movement with stamina, emergent chronicle moments with causes, readable creature thoughts, 3D picking on Desktop and Android, a placement ghost, selection, a living HUD with time controls up to 8×, and named world slots.

**Gate:** On Desktop and Android, a player with no coaching creates a world, places rabbits and wolves and sees chases, escapes, packs and litters emerge; the same placements replay to identical hashes on JVM, Android and iOS; earlier saves keep their exact bytes; performance stays within budget at the population caps.

### PHASE 11 — Believable Ecosystems

**Player milestone:** Every world grows into a web the player can read: more species and plants, seasons and weather that matter, succession, food chains that rebalance after a change.

**Gate:** Long soaks show varied, interpretable outcomes rather than a single equilibrium or certain extinction; predation, not caps, regulates prey; each species' role is visible in play.

### PHASE 12 — Creation Tools

**Player milestone:** The player shapes the land itself: raise and lower ground, dig ponds and streams, plant forests, place dens, fences, burrows and structures, and set weather.

**Gate:** Edited terrain is part of deterministic state and saves; animals and water respond to it; tools are touch- and mouse-friendly with clear previews.

### PHASE 13 — Experiment & Discovery

**Player milestone:** Comparing worlds becomes the joy of the game: side-by-side histories of placements, population graphs over time, a field journal of discoveries the world actually produced, and shareable experiments.

**Gate:** Players can answer their own "what if" questions without developer help, and the answers are true to the simulation.

### PHASE 14 — Masterpiece-Level Craft, QA & Publication

**Player milestone:** The whole experience feels intentionally designed, stable, immersive and recommendable.

**Build:** Cohesive art and sound across every scene, animation, lighting state and interaction; polish of the opening, camera, world feedback and time transitions; balance from real playtests; accessibility, input parity, localization-ready text, save repair and migration, clean installs, reliable recovery, performance profiles and platform-native behaviour; README, authentic screenshots and footage, release builds and contest materials if applicable. Evaluate further platforms (an iOS 3D renderer, Web) from actual renderer support.

**Gate:** Independent testers learn, play and return to the sandbox without developer coaching; recorded feedback supports improvements. Android and Desktop run the same shared-Kotlin gameplay; no known save-corruption or branch-contamination defects remain; performance is measured on named devices; screenshots and footage are authentic.

---

## 7. The competition advantage (without a date-driven scope ceiling)

The official Kotlin Multiplatform Contest judging criteria are **creativity/novelty 40%, KMP use/shared Kotlin 40%, Kotlin conventions 20%**. Optimize the *substance* of the project for these criteria while still aiming at a remarkable commercial-quality game.

| Contest dimension | BranchWorld's convincing proof |
|---|---|
| **Creativity / novelty** | A living 3D world the player fills by hand, where autonomous creatures write their own stories, and alternate histories of the same world compared in place |
| **Meaningful KMP engineering** | Shared deterministic simulation, worldgen, creature behaviour, placement commands, snapshots and replay, save schemas, chronicle and test fixtures, identical on Android, Desktop and iOS |
| **Idiomatic Kotlin craft** | Clear domain APIs, Kotlin conventions, focused modules, reproducible tests, safe state transitions, appropriate Flow/coroutines, portable models and honest documentation |

The most persuasive technical presentation is a **single real demonstration**: the same island, the same placements and tick count producing the same world and hash on every platform, shown alongside the creatures living it out.

External contest requirements (eligibility, repository licence, README, essay and entry mechanism) must be checked if a submission is made. Do not imply that the competition has no closing date; this plan simply avoids using that date to constrain the game.

---

## 8. Cross-phase acceptance and research program

### Five proofs of excellence

| Proof | Verify by | Disqualifying weakness |
|---|---|---|
| **First-frame beauty** | Authentic captures on target screen sizes, compared to curated art-quality references | Empty UI, tiny world, asset pile without lighting or material cohesion |
| **Delightful interaction** | Live mouse and touch walkthroughs; placement feedback latency and clarity | Debug actions, confusing targeting, no immediate response |
| **Real emergent consequence** | Fixed-seed scenario tests of placements → behaviour → populations → visible outcomes | Scripted outcomes, untraceable numbers, one dominant result |
| **Trustworthy alternate histories** | Exact command replay, branch isolation and save/restart tests | A parent history changing after a fork, mismatched state, nonrepeatable scenarios |
| **Player desire to return** | Independent play sessions, recorded confusion and delight, voluntary replay, later market research | Fun or sellability declared without evidence |

### Design experiments

- **Camera & composition:** orbit centres, field of view, creature scale and framing, judged on authentic side-by-side captures.
- **Art direction:** lighting, palette, fog, materials and vegetation density for beauty and readability.
- **Ecology & balance:** same-seed placement scenarios over long soaks; compare cycles, extinctions, carrying capacity and whether players can explain what happened.
- **Comparison UI:** spatial overlays, synchronized views and quick switching; can first-time players tell what changed and why?
- **Performance:** frame-time distributions, simulation step cost at the caps, memory and load time on named devices.
- **Desirability:** blind playtest observation, voluntary replay, and later informed willingness-to-pay research. None are assumed successful beforehand.

Log experiments in the ledger near the end of this file. Keep or revert based on evidence; do not declare visual quality through FPS alone.

### Performance and platform truth

Aim for smooth interaction on a nominated Desktop machine and representative Android devices (about 60 FPS on Desktop and 30+ on Android are provisional goals, not facts). Size population caps from measured step costs. Scale visual settings without altering deterministic simulation.

### Source asset inventory (verify in repository)

KayKit Forest Nature, KayKit Medieval Hexagon, KayKit Adventurers, KayKit Character Animations, Kenney Nature Kit, Kenney Cube Pets, Kenney Interface Sounds and Vuena Pets live under `assets/`. Import only models, textures and clips verified for runtime use on Android and Desktop.

---

## 9. Starting or resuming a session

> Read `AGENTS.md` and `PLAN.md` entirely. Inspect the actual BranchWorld repository, Git status, installed Skills and builds. Resume the active phase from the progress record below. Build real gameplay and visuals, capture real screenshots, test every KMP target, and iterate until the phase's player, visual and engineering gates are met. Keep `PLAN.md` current for seamless continuation and request approval before moving to the next phase.

---

## 10. Persistent progress record (AI-agent maintained)

Keep this section accurate and concise. Record what actually ran, with PASS / FAIL / BLOCKED / NOT RUN.

### Current State

**Active phase:** 10 — Living Sandbox: Create, Populate, Observe. Built and verified on Desktop and Android; awaiting human acceptance. Not committed. See Latest Handoff.

**Verified foundations:** see §6. Living worlds use **rules v12** and **save schema 10**, adding `life` sections to the state and to each creature only when present, so earlier saves keep their exact bytes and replay hashes. Each world has its own slot (`worlds/world-<n>.json`) beside the player profile.

**Player experience:**
- **Title:** the live island fills the screen. A beech sign reads *Create a world. Fill it with life. Watch what it becomes.* The shelf offers Continue (the last world, by name and day), **New world**, **My worlds** and Settings.
- **New world:** pick The Fading Grove, Cairnwater or Heron Reach terrain, or type or roll a seed; choose *Empty land* or *Wild* (8 rabbits, 4 deer, 2 foxes, no wolves); name it; Create.
- **In the world:** a name board shows the day and population pegs. The dock has Pause/Play and 1×/2×/4×/8×; a palette of wooden pegs (Look, Rabbit, Wolf, Deer, Fox, Tree, Grass, Lift out); What happened; Timeline; Menu. Hovering or touching the island shows a peg ghost (green where the tool can act, madder where it cannot, with the reason in words). A click or tap sets the animal or plant down; while paused, the world takes one tick so it appears at once, dropping in. Look selects a creature and opens its card: name, sex, age, what it is doing and why, food/water/energy/stamina, pack, kills and litters, Follow and Lift out.
- **Life:** rabbits graze (taking water from grass), dig warrens, pair off and raise litters. They see moving predators, thump an alarm that neighbours hear, run for warren or thicket and jink when a hunter is close; desperate hunger or thirst eventually outweighs fear. Lone wolves seek each other and form packs with a den, rest by day, howl at dusk and patrol at night; the leader chases while the others cut ahead; packs share kills, catch deer only together, raise pups as a breeding pair, range wider when hungry and starve when prey is gone. Foxes hunt alone and avoid wolves; deer keep near each other and run. Fire, lightning, rain and regrowth run in the same world.
- **What happened:** the chronicle records hunts, packs forming, escapes, starvation, booms, crashes, first litters and the player's own acts, and "Why?" follows recorded causes.
- **Developer only:** content from earlier rule sets stays in the code so existing saves open; it is reachable only through a developer switch in Settings.

### Phase Ledger

| Phase | Engineering status | Player / visual quality | Evidence | Human acceptance |
|---|---|---|---|---|
| 01–09 | COMPLETE | Foundations listed in §6 | Git history up to `573fc23` | — |
| 10 | COMPLETE | Living sandbox: create, place rabbits and wolves by touch or click, watch hunts, packs, warrens and litters; captured on Desktop and Pixel 9 | `LifeRulesTest`, `GroundPickingTest`, `DesktopLivingSandboxUiTest`, `LivingSandboxDeviceTest`, `build/verification/living-sandbox-10` | PENDING |
| 11 | NOT STARTED | — | — | — |
| 12 | NOT STARTED | — | — | — |
| 13 | NOT STARTED | — | — | — |
| 14 | NOT VERIFIED | — | Independent gate review pending | PENDING |

### Decision Log

| Decision | Rationale | Status |
| Maximize premium game quality; do not run a deadline-compressed product lane | Explicit player direction; existing screenshots are well below target quality | Product direction accepted; implementation pending |
| Two root planning files only: AGENTS.md (how) and PLAN.md (what) | One source of truth survives context resets | In force |
| Kool renders behind a renderer adapter; Android GLES under Compose, Desktop Vulkan delivered to Compose | Proven on both targets; no engine types in authoritative state | In force |
| Authoritative state advances only in shared Kotlin at fixed ticks with versioned rules | Same seed, rules and commands give the same world on every target | Verified by replay-hash fixtures on JVM, Android and iOS |
| A new rule set adds optional state sections encoded only when present | Older saves keep their exact bytes and replay hashes | Rules v12 follows it; every earlier pin is unchanged |
| Canonical-bytes FNV hash of the whole state, including fire and chronicle | Replay checks must catch any divergence | In force |
| Each world has its own atomic save slot beside a profile; a slot that fails to load is never overwritten | No silent loss of a player's world | In force |
| **BranchWorld is a freeform living-world sandbox** | Player direction (2026-11-22): Create → Populate → Observe → Experiment → Discover; no scripted outcomes, no objectives | PLAN, AGENTS, PRODUCT, DESIGN and README describe only this direction |
| Content from earlier rule sets stays in code but out of play | Player choice: existing saves must keep opening and nothing may break | Title offers Continue / New world / My worlds / Settings; earlier content is reachable only through a developer switch in Settings |
| New worlds start as *Empty land* or *Wild* | Player choice; the first act is placing animals | Wild seeds 8 rabbits, 4 deer, 2 foxes and no wolves, so wolves stay the player's experiment |
| Rules v12 adds `EcologyState.life` and `Creature.life` (NEVER-encoded) and carries only environment, wildlife, fire, chronicle and life sections | Earlier saves keep their bytes and hashes; a living world runs only living-world rules | Every earlier pin passes unchanged; save schema 10 |
| Placement is four shared commands: PlaceCreature, RemoveCreature, PlacePlant, ClearPlant | All consequences through tick-indexed commands; replay, saves and world codes carry them | Checked again when applied; refusals name a reason (water, unreachable, unsuitable, occupied, burning, species full, island full) |
| Placing while paused advances exactly one tick | Commands apply at the next tick; the player must see the result at once | `WorldClock.step()` |
| Keep the 64×64 cells; move in 8 directions with a stride accumulator | Sub-cell positions would rebase cover, patches, fire and bytes for little visible gain; the grid look came from 4-way staircases | Octile fields (no corner cutting), centi-cell strides, sprint stamina; renderer glides at each creature's own pace |
| Counter-based rolls `lifeRoll(seed, tick, id, purpose)` | Decisions never depend on processing order; a placement in one place cannot reshuffle another animal's dice | Creatures decide from the previous tick's snapshot |
| Wolf body is the Kenney Cube Pets dog with a grey KSL coat | Same rig and art set as the other animals; idle/walk/run/eat verified in the file | `diorama/animal-wolf.glb` |
| v12 caps rabbits 40, deer 14, foxes 8, wolves 12, island 80 | Provisional, under the measured step budget | p95 step 79–291 µs on the M4 JVM (see E-L1) |
| 4× and 8× batch 2 and 4 ticks per published frame | The interface is never asked to redraw faster than 40 Hz | WorldClockTest pins identical states at every speed |
| Screen picking is plain Kotlin on the shared camera | Testable without a GPU; one field of view constant for scene and picks | GroundPickingTest; Desktop clicks and Android taps (touch slop, 350 ms) become taps |

### Experiment Ledger

| Hypothesis / candidate | Baseline scenario | What was actually measured or observed | Keep / revert / pending |
| Fill the frame with a coastal landscape | Seed `104729`, default camera and scrolling dashboard on Pixel 9 AVD and Apple M4 | Full-frame water removes the exposed sea rim. The closer landscape makes creatures and terrain visible on both targets. Portrait crops the coast intentionally. | KEEP. Check the remaining near-field and canopy detail in Phase 02 after approval. |
| E-L2 run 1: rabbits and wolves left alone | 6 rabbits near the spawn, 3 wolves 11–14 cells out, all three gallery islands, 6,000 ticks (`LifeProbe`) | Wolves formed a pack at once but never caught a rabbit: rabbits always reached cover, which was a perfect refuge, and wolves burned 1.33 food a tick. All wolves starved by tick ~640. | Revert: cover only lowers catch odds (warrens stay absolute); wolves eat 0.5 a tick |
| E-L2 run 2 | Same | Wolves now caught rabbits but ate all of them and starved: rabbits never bred, because a resting wolf in sight kept them alarmed | Revert: only a moving or hunting predator alarms; newcomers settle before hunting; rabbits dig warrens as they arrive |
| E-L2 runs 3–4 | Same, 12,000 ticks | Heron Reach showed a real boom, predation crash and recovery; the other two islands still collapsed. Rabbits could not breed while merely wary | Keep: mating while only wary; warrens become a rabbit's home |
| E-L2 run 5 | Same | Packs fed only the catcher, and wolves never reached their mating rule; once fixed, packs grew to 6, but most rabbits died of hunger or thirst | Keep pack kill-sharing and wolf mating; investigate deaths |
| E-L2 runs 6–9 (death causes traced per animal) | Same, death cause and last positions logged | Animals died on errands: a food trip ignored critical thirst; hiding never ended; path fields ran out when every animal wandered to a different spot, leaving some standing still; wolves searched only near the den | Keep: the most urgent reserve wins; desperation leaves cover; short trips steer directly with a four-way fallback; crowded patches lose appeal; hungry packs range 30 cells; rabbits take water from grass; starving wolves keep hunting |
| E-L3 chase balance (first experience) | Desktop capture, Cairnwater, 6 rabbits and 3 wolves placed 5 cells apart | The pack ate 4 of 6 rabbits within the first in-game day, before any litter | Keep: placed hunters settle 400 ticks, wolves breed after 1,800, a pack shares 900 food per rabbit, hunger thresholds 560 at night and 360 by day |
| E-L2 final soak with the shipped rules | 6 rabbits and 3 wolves, 24,000 ticks (20 days), all gallery islands (`build/verification/living-sandbox-10/E-L2-soak-24000.txt`) | Heron Reach: the pack lives beside the rabbits all 20 days, 3–9 wolves, 82 catches. Cairnwater: 3 wolves live 17 days and die of old age after 34 catches, without raising pups. Fading Grove: wolves starve by day 2 after one catch. Rabbits sit at their cap of 40 on all three; 108 Cairnwater rabbits die of hunger or thirst through overgrazing | Keep for Phase 10. Pending (Phase 11): predation does not yet regulate rabbits, which the cap does instead; the Cairnwater pack never breeds |
| E-L1 step cost at the caps | Same final soak, Apple M4 JVM | p50 71–74 µs, p95 140–226 µs per tick, worst single tick 24 ms (one-off, JIT warm-up). Budget 400 µs | Keep caps rabbits 40, deer 14, foxes 8, wolves 12, island 80. Pixel 9 step cost not yet measured |
| Placement ghost visibility | Desktop capture 1280×800, survey camera | A flat ring sat under the faceted terrain and was invisible; a stake read as a pale sliver | Keep: a raised band, a stake and a painted knob (green or madder) |
| Capture harness at 4× | Desktop GPU capture with the real App | Compose test idling never settled at 4× with live GPU frames, so any click hung; at 2× with a pause before interface clicks every step completes | Keep for captures; the real app is unaffected (it never waits for idle) |

### Latest Handoff

#### Phase 10 evidence (Living Sandbox: create, populate, observe)

```text
ACTIVE PHASE / STATUS: 10 — COMPLETE (awaiting human acceptance)
PLAYER-VISIBLE CHANGE: BranchWorld is now a freeform living-world sandbox. The title offers New world / My worlds;
  a player names a world on any island or seed as Empty land or Wild, picks Rabbit or Wolf from a wooden palette and
  clicks or taps the 3D island to set animals down (a green or madder peg ghost shows where and whether; refusals are
  said in words). Left alone, rabbits graze, dig warrens, pair and raise litters, see moving predators, thump alarms
  and flee to warrens or thickets; wolves find each other, form packs with dens, howl at dusk, hunt together at night,
  share kills, raise pups and starve when prey is gone. Pause and 1×/2×/4×/8× sit in the dock; Look opens a creature
  card ("Chasing Clover with the pack"); What happened lists hunts, packs, escapes, litters, booms and crashes with
  "Why?". Content from earlier rule sets is reachable only through a developer switch in Settings.
VISUAL EVIDENCE: build/verification/living-sandbox-10/desktop-r3 (1280×800, Apple M4 Vulkan: title, new world, empty
  land, wolf placement, pack hunting at 2×, wolf card while following, What happened), desktop-r5 (placement ghost),
  desktop-r1 (first run, 4×); android/ (Pixel 9 AVD API 36, 1080×2424 portrait and 2424×1080 landscape: new world,
  empty land, 8 rabbits tapped down, 3 wolves, day 4 at 8× with 37–38 rabbits and 5 wolves in rain).
CODE CHANGED: shared ecology/LifeState.kt, LifeRules.kt (rules v12), EcologyEngine/EcologyState/CanonicalEcologyState,
  WildlifeState, WorldChronicle, TerrainNavigation (8-way octile fields), simulation/WorldEngine (4 commands),
  LifeProjection, WorldPointer, WorldClock (8×, step), WorldCamera; save/TimelineSaveCodec (schema 10),
  timeline/Timeline, WorldCode (tags 7–10); ShellState, PlayerProfile, IslandSession, App, LivingWorld.kt,
  HistoryUi, WildlifeInspector, GameIcons, life.xml; renderer GroundPicking, LifeDetails, Creatures (pace-aware 8-way
  glide, drop-in, grey wolf coat, larger living bodies), PreviewController (pick/tap/hover), DesktopCameraInput
  (click and hover), AndroidDiorama (touch slop taps), DioramaAssets (animal-wolf.glb from the Kenney Cube Pets dog);
  AGENTS.md, PLAN.md, PRODUCT.md, DESIGN.md and README.md describe only the living-sandbox direction.
BUILDS & TESTS:
  PASS ./gradlew --offline --max-workers=2 -Pkotlin.incremental=false :shared:jvmTest :shared:testAndroidHostTest
       :renderer:jvmTest :renderer:testAndroidHostTest :desktopApp:test :desktopApp:createDistributable
       :androidApp:assembleDebug :androidApp:assembleDebugAndroidTest :androidApp:lintDebug
       :shared:compileKotlinIosArm64 :shared:compileKotlinIosSimulatorArm64 --continue --rerun-tasks
       (248 host tests: 108 shared JVM, 101 shared Android host, 30 renderer JVM, 6 renderer Android host, 3 Desktop;
       log gradle-host-final.log)
  PASS :shared:iosSimulatorArm64Test --tests LifeRulesTest, WorldClockTest, SaveShapeTest, EmberSeasonTest (17; gradle-ios.log)
  PASS :androidApp:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=id.branchworld.game.LivingSandboxDeviceTest
       on Pixel_9 AVD (gradle-connected.log)
  NOT RUN the rest of the shared and app connected suites this phase (the known in-process memory gap below)
SIMULATION / SAVE / BRANCH: LifeRulesTest pins the same rabbits-and-wolves history at 0e8f9645aa123fff on JVM, Android
  host and iOS simulator; save → restore → continue, replay from start plus commands and a BW1 world code all reach
  the same hash. Every rules v3–v11 pin and SaveShapeTest byte check is unchanged. Placement under v11 is refused.
  Removing animals and clearing plants keep the birth/death and biomass ledgers exact.
PERFORMANCE: Apple M4 JVM, 24,000-tick soak, 6 rabbits and 3 wolves growing to caps: p50 71–74 µs, p95 140–226 µs per
  tick (budget 400). Pixel 9 AVD renderer frames p50 16.7 ms, p95 19.6–23.4 ms. Pixel 9 simulation step NOT MEASURED.
PLAYTEST: scripted walkthroughs only (Desktop UI test with real mouse input, GPU captures, Android touches). NOT TESTED
  by an unaided player.
DECISIONS / EXPERIMENTS: Decision Log rows from "2026-11-22 direction change"; Experiment Ledger E-L1, E-L2 runs 1–9
  and final soak, E-L3, ghost visibility, capture harness at 4×.
KNOWN GAPS:
  1. Predation does not yet regulate rabbits: they sit at the cap of 40 and overgraze; on Cairnwater the pack never
     breeds; on Fading Grove the wolves starve by day 2. Each island differs, but the cap rather than wolves sets
     rabbit numbers (Phase 11).
  2. The Android full device suite was not rerun in one process; it ran out of memory there before Phase 10 (islands kept open across tests).
  3. Placement markers do not show on scrubber; the timeline's "start a new history here" prompt is built but was not
     captured; world codes still cap at 64 commands, so busy worlds cannot be shared.
  4. iOS keeps its 2D fallback viewport and cannot place animals (no 3D renderer there); shared rules are verified.
  5. In the Desktop GPU capture harness, clicks at 4× never let Compose idle; captures use 2× (real app unaffected).
  6. The follow camera can sit very close at dusk; shadows under creatures still follow cells, not the glide.
NEXT ACTION: Ask the player to accept Phase 10 after creating a world on Desktop and a phone and placing rabbits and
  wolves by hand. On approval begin Phase 11 with gap 1: tune rabbit carrying capacity and wolf breeding so predation
  regulates prey (record as E-L2 follow-ups), then add deer and grass dynamics that let packs persist.
PHASE APPROVAL: AWAITING
```


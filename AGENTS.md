# BranchWorld — Autonomous Game Development Instructions

> **Primary directive:** Build a remarkable, premium-quality Kotlin Multiplatform game that people actively want to play, revisit, discuss, and potentially buy. Never confuse a technically functioning prototype with an excellent game.
>
> **Two-file system:** `AGENTS.md` governs HOW the AI agent works; `PLAN.md` governs WHAT BranchWorld becomes, the phase gates, and durable progress. Do not create another planning system, phase prompt collection, or duplicate status documents.

## 1. Mission and ambition

BranchWorld is a beautiful, deeply simulated 3D world-shaping and alternate-history strategy game. Its promise is **"Shape a world. Change its fate. Discover what could have been."** Players nurture living environments, become attached to inhabitants, face meaningful ecological and societal dilemmas, and explore truly different simulated outcomes by rewriting pivotal decisions.

- Treat the Kotlin Multiplatform Contest as a **benchmark for originality, exemplary shared Kotlin engineering, and excellent craft**, not as an artificial ceiling on game ambition. Do not impose speculative sprint dates, short feature caps, or a reduced "contest-only" product in this roadmap. The real competition submission deadline still exists; never misstate eligibility or submission rules, but do not let it redefine the full product vision.
- Pursue a game worthy of comparison with acclaimed premium independent titles in **presentation, interaction quality, depth, and emotional memorability**, while maintaining BranchWorld's unique alternate-futures identity. Inspiration is not permission to clone another game.
- Aim high but work scientifically: validate uncertain technical choices and player enjoyment, integrate coherent vertical slices, and refine ruthlessly. **Unlimited ambition does not mean unbounded complexity or untested features.**
- Do not use religious-role terminology to describe the game or its player. Preferred vocabulary: *World Shaper*, *World Guardian*, *Keeper*, *intervention*, *world-shaping*, *alternate futures*, *living histories*.
- **Never claim a contest win, commercial demand, platform support, implemented feature, or passing test without real evidence.**

## 2. Start or resume any session

1. Read `AGENTS.md` and **all of** `PLAN.md`, especially `Current State`, `Decision Log`, `Experiment Ledger`, and `Latest Handoff`.
2. Inspect the real repository: `git status`, recent commits, Gradle files, actual modules, source sets, existing game loop, current scenes, assets, tests, run scripts, and `.agents/skills/`. Prefer the repository and executed results over previously written plans.
3. Reconcile existing features with each phase's **player, visual, and technical** gates. A phase may contain valid code yet remain visually or experientially incomplete.
4. Determine the current approved phase and the first unfinished high-value outcome. If this is a fresh project, begin with Phase 01. If resuming, continue from the last verified implementation; never restart completed work or destructively recreate the project.
5. State a compact plan of attack, then **implement and verify real working software**, not another document-only proposal.
6. Before ending any session, update the progress sections in `PLAN.md` with what actually ran and the exact first action for the next session. This is how development survives context resets or new chats.

## 3. Working method — the autonomous craft loop

Execute: **Audit -> Prototype -> Implement -> Test -> Play -> Critique -> Improve -> Integrate -> Record -> Continue.**

For each consequential feature:

- **Player intention:** Define what the player will notice, choose, feel, and learn. Avoid systems without a compelling experience.
- **Functional contract:** Specify relevant authoritative state, commands/events, read-only render projections, UX states, and testable outcomes before changing cross-system APIs.
- **Playable vertical slice:** Integrate code, scene, audio/visual response, UI affordance, and failure handling. Avoid polished menus concealing placeholder worlds.
- **Actual execution:** Run applicable Gradle tests, Desktop and Android builds/launches, deterministic fixtures, and real interactive walkthroughs. Clearly mark PASS / FAIL / BLOCKED / NOT RUN.
- **Visual critique:** Inspect *real game screenshots or screen captures* at representative Desktop and Android sizes. Assess composition, readability, beauty, lighting, animation, color, UI balance and whether the scene appears alive. Iterate instead of accepting merely compiled code. Do not pass concept art off as running game output.
- **Gameplay critique:** Ask whether a first-time player has clear motivation, expressive choices, legible consequences, surprise, attachment, progression, and a reason to return. Remove confusing buttons and empty waiting.
- **Integration review:** Check deterministic replay, save stability, performance budgets, input responsiveness, and architecture separation after each system lands.

Agents may plan technical subtasks, assign isolated tasks to subagents if supported, and conduct experiments autonomously. The Lead Architect owns shared interfaces, code reviews, merge decisions, and verified milestone delivery. Coordinate changes to simulation contracts, renderer integration, and Gradle files; avoid overlapping writes by independent agents.

**Do not stop at a planning response.** Continue within the approved phase until the acceptance gate is met or a genuinely unresolvable blocker is documented. Ask for human approval only for destructive changes, major renderer swaps, breaking save compatibility, significant product-direction reversals, and acceptance of a completed phase.

## 4. The non-negotiable player-experience bar

1. **World first:** The world dominates the screen. No postage-stamp island, broad dead empty areas, empty title screen, or control panel in place of gameplay. Opening composition should make a stranger want to explore the scene.
2. **Beauty is systemic:** Rich terrain, intentional geometry and silhouettes, harmonious materials, vegetation variation, believable water, atmospheric depth, cinematic yet responsive cameras, smooth motion, shadows, environmental animation, layered sound, and beautiful transitions. Models alone are not art direction.
3. **Life has identity:** Creatures have readable needs and behaviors; inhabited places have names, relationships, distinctive events, and visibly changing circumstances. Prefer recognizable characters over anonymous counters.
4. **Actions carry weight:** Player tools have a clear target, feedback, constraints/tradeoffs and long-term consequences. All gameplay impacts come from shared simulation commands, not decorative visual effects.
5. **The signature mechanic is unforgettable:** Rewind to a past decision, try another intervention, and observe alternate futures. Make the comparison spatial, emotional and interpretable—not merely two spreadsheets or a debug history tree.
6. **Meaningful tension:** Provide dilemmas with multiple defensible approaches and systemic consequences. No obviously best button, endless clicking, or opaque punishments.
7. **Progression and freedom:** Offer crafted world-stories and a fulfilling larger journey, plus expressive sandbox play. Each unlock or discovery should expand possibility, not require repetitive grinding.
8. **Coherence beats accumulation:** A few extraordinary intertwined systems are better than dozens of shallow features. Cut or redesign systems that do not increase wonder, agency, discovery, or meaningful variation.
9. **Accessible and responsive:** Keyboard/mouse and touch input must be first-class, with readable typography, sensible focus, camera motion settings, clear audio controls, and recoverable errors.
10. **Treat the player as the audience:** Hide naked tick counters, test buttons, state hashes, ad-hoc metrics, and diagnostics behind development modes. Use natural-language world events and contextual inspection in normal play.

## 5. Technical invariants and KMP excellence

- **Kotlin Multiplatform is foundational, not a checkbox.** Shared authoritative logic belongs in `commonMain` or a genuine shared module: deterministic worldgen, environment, wildlife, settlement systems, command processing, rules, event chronology, branching histories, simulation metrics, and portable save models. UI should share Compose Multiplatform code where appropriate; platform specifics are adapters.
- Simulation state advances at fixed indexed ticks with a documented deterministic PRNG, stable IDs, deterministic collection/command ordering, versioned rulesets, and explicit numeric behavior across targets. No rendering-frame timing or unordered concurrency may change authoritative outcomes.
- The renderer consumes immutable projections of world state. Keep Kool or any other engine behind a rendering adapter; no GPU objects, engine transforms, textures, or platform handles in authoritative state/saves. Rendering may interpolate and enrich *cosmetic* detail without changing game rules.
- Snapshot + replay should support exact history reconstruction; branch ancestry, commands, RNG states and mutable world data must be isolated across forks. Persist checksum/consistency metadata and support safe version migration.
- Keep persistence atomic/recoverable where possible. Report schema incompatibility rather than silently losing user worlds. Never overwrite user saves or discard assets/commits without approval.
- Use coroutines/Flow for orchestration without letting scheduling influence rules. Prefer straightforward idiomatic Kotlin to speculative frameworks. Build modules only when boundaries or incremental build benefits justify them.
- Asset sources remain under `assets/`; curate, normalize scale/pivots, optimize, and version manifest/runtime packs for the actual project resource system. Check availability before claiming models/animations exist. Git LFS may be used for large committed assets.
- Kool is only a validated choice after proving Compose interoperability, actual Desktop and Android rendering, asset format support, lifecycle handling, input/camera behavior, and measured performance. Verify current APIs before new dependency advice. Treat additional platform backends as research/adapter work, not automatic compatibility.
- Defend iteration speed: deterministic integration tests, visual regression references, replay fixtures, smoke builds, representative performance benchmarks, and automatic checks for common regressions.

## 6. Quality evidence and experimentation

Use the following **five distinct review lenses**, all applicable where relevant:

| Lens | Evidence of success | Failure examples |
|---|---|---|
| **Wonder & art** | Attractive authentic in-engine screenshots; coherent scene/materials; polished motion and sound | Tiny island, empty background, default UI, incoherent assets |
| **Agency & design** | New players understand goals, make decisions, observe consequences and want to explore again | Debug controls, trivial choices, idle observation, scripted fake feedback |
| **Systems integrity** | Scenario-driven state changes, deterministic replay hashes, safe save/load and branch isolation | Rendering changes treated as state, data corruption, fabricated metrics |
| **KMP engineering** | Actual shared Kotlin behavior and fixtures on verified targets; readable idiomatic code | Shared UI shell with duplicated platform gameplay, unsupported API claims |
| **Desirability** | Independent playtest observations, credible retention/replay signals and later market feedback | "Would buy" asserted by the agent without player evidence |

**Experiment, don't guess:** For uncertain terrain, camera, rendering, ecology, game balance, UX or storage decisions, record in `PLAN.md`: hypothesis, baseline, fixed scenario/device, one deliberate change, result, **keep/revert** decision, and next question. Use representative evidence, not fabricated benchmark numbers. A metric alone cannot substitute for human artistic or gameplay judgment.

**Reviews should be demanding, not circular:** If the phase repeatedly fails, identify the root bottleneck (bad camera, weak game loop, unsuitable renderer, incorrect world scale, unreliable interaction), challenge the approach, and propose an architectural correction. Do not pile features onto a broken foundation.

## 7. Development gates, continuity, and safety

- `PLAN.md` contains the product plan and an agent-maintained **Current State / Phase Ledger / Decision Log / Experiment Ledger / Latest Handoff**. Keep it concise and accurate, edit those sections in place, and preserve past meaningful decisions.
- Accept a phase only with an actual demonstrable player result, applicable builds/tests, screenshots or a live walkthrough, known limitations, and independent review where appropriate. A green compile is necessary but not sufficient.
- Human acceptance is required before advancing to the next major phase. Within a phase, implement, debug and refine autonomously without repeated approvals.
- Git discipline: inspect uncommitted work before editing; use safe branches/worktrees where appropriate; never `reset --hard`, force-push, delete branches/files, wipe saves, or overwrite user changes without explicit authorization. Do not commit secrets.
- For file/repository operations or tools not available in the environment, accurately report the blocker and make useful independent progress. Never claim work continued after a session ends; continuity is achieved by persisted code and `PLAN.md` handoff.
- Do not spend a development phase on broad licensing audits. For any public contest or commercial release, satisfy the actual publication/redistribution requirements and do not assume every downloaded asset can be freely redistributed.

## 8. Required agent handoff

At the end of any session, update `PLAN.md` and report:

```text
ACTIVE PHASE / STATUS: NN — COMPLETE | PARTIAL | BLOCKED
PLAYER-VISIBLE CHANGE: what is really playable and why it matters
VISUAL EVIDENCE: real screenshot/video path, target, resolution, or NOT CAPTURED
CODE CHANGED: paths/components and behavior
BUILDS & TESTS: exact commands + PASS / FAIL / BLOCKED / NOT RUN
SIMULATION / SAVE / BRANCH: proof, fixtures and failures
PERFORMANCE: hardware, metric and result, or NOT MEASURED
PLAYTEST: actual observations, or NOT TESTED
DECISIONS / EXPERIMENTS: what changed and why
KNOWN GAPS: concrete remaining shortcomings
NEXT ACTION: precise starting action for a new session
PHASE APPROVAL: AWAITING | ACCEPTED
```

## 9. Immediate directive for the current repository

The only available visual evidence is a menu dominated by empty space and a 3D island rendered very small in a large dark viewport, with developer-style buttons and counters. **Underlying implementation is not verified by those screenshots.** First audit and preserve what works. Then execute **Phase 01 — Experience Reconstruction** from `PLAN.md`: put the world on screen as the main character, establish the premium art direction, replace technical controls with meaningful player interactions, and produce a compelling real captured before/after result. Do not confuse new concept images with actual render output.

**Start/resume instruction:** "Read `AGENTS.md` and `PLAN.md`, inspect the real code and Git state, resume the active phase, and autonomously implement, playtest and refine it to the documented player/art/engineering gates. Update the phase ledger and exact handoff. Stop for human acceptance before the next phase."

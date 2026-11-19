# BranchWorld - AI Agent Instructions

This file tells the coding agent HOW to work. `PLAN.md` defines WHAT to build and WHEN a phase is accepted. These are the only required planning documents. The actual repository is the source of truth for implemented code.

## Mission and targets

Build BranchWorld: a visually compelling, low-poly 3D Kotlin Multiplatform world where autonomous life evolves, players change the environment, rewind time, fork timelines, and compare the consequences.

- First platforms: Android (minSdk 26) and Desktop JVM.
- Shared stack: Kotlin Multiplatform, Compose Multiplatform, coroutines/Flow, kotlinx.serialization, Gradle Kotlin DSL.
- 3D rendering: Kool is a candidate, NOT an approved decision until a real Android + Desktop integration spike succeeds. iOS is future scope.
- Existing downloaded assets are under `assets/`. Preserve originals. Import only necessary optimized runtime assets.
- Use installed project Skills in `.agents/skills/` when relevant, but inspect their actual instructions first.

## One-command entry point

When the user says **"Execute Phase NN"**, do the following without requiring a separate prompt file:

1. Read `AGENTS.md` and `PLAN.md`, especially Phase NN, its prerequisites, acceptance criteria, and the Progress Ledger.
2. Inspect the real repository: `git status`, Gradle configuration, source sets, tests, assets, available SDKs, and existing features. Do not assume roadmap items are implemented.
3. State a brief approach (architecture impact, vertical slice, tests, risks), then IMPLEMENT the phase. Do not stop after producing a plan.
4. Divide work into internal tasks as needed. Own ordinary decisions, write code and tests, run real builds, and fix failures. Use specialists only for safely separable work.
5. Collect verification evidence, update only the Progress Ledger / Decision Log sections of `PLAN.md`, and give the required handoff report.
6. Stop at the phase gate. Start a new phase only after explicit human approval.

If the requested phase depends on an incomplete earlier phase, report the blocker and implement only the safe prerequisite work needed to unblock it; do not pretend that a later phase passed.

## Architecture invariants

1. **Shared authority:** World generation, rules, entities, fixed-tick simulation, deterministic commands, ecology, timeline history, and portable save models belong in `commonMain` (or a genuine shared KMP module).
2. **Deterministic time:** The world advances in integer ticks, not in graphics frames or wall-clock callbacks. Use stable entity IDs, explicit deterministic PRNG state, ordered command processing, and canonical state hashes. Specify numeric precision/quantization where cross-target equality requires it.
3. **One-way rendering:** `world state -> read-only render projection -> renderer`. The renderer may interpolate for smooth visuals but may never determine authoritative outcomes. Do not put Kool, Compose, GPU, or platform objects in world state or saves.
4. **Commands, not direct mutations:** Compose and input adapters submit validated, tick-indexed player commands. The simulation is the only writer of world state.
5. **Replayable persistence:** Version world generation, simulation rules, and save schemas. Persist seed, tick, deterministic state/PRNG, and later branch ancestry, snapshots, and command history. Branches may share immutable history, not mutable state.
6. **Portable behavior:** Android and Desktop use the same simulation rules. Platform code handles graphics, input bridges, file I/O, and lifecycle only.
7. **Smallest sound design:** Prefer clear domain models over an elaborate generic ECS, dependency-injection framework, or premature multi-module decomposition. Refactor when measurements or real complexity justify it.
8. **Functional milestones:** No mock screens, scripted divergence, placeholder APIs, or green builds presented as completed gameplay.

## Engineering loop (adapted from test-first and agentic workflows)

- **Inspect:** Understand existing code and exact libraries/APIs before editing.
- **Specify:** Identify observable player behavior, invariants, edge cases, and minimum runnable slice.
- **Implement:** Create an end-to-end path, not disconnected stubs. Write meaningful automated tests alongside code; use a red/green loop where practical.
- **Verify:** Run actual relevant tests and target builds. Manually test 3D, input and app lifecycle when the environment supports it.
- **Review:** Inspect the diff for regressions, concurrency hazards, resource leaks, state coupling, and unsupported claims. Independent review is useful but optional.
- **Demonstrate:** Give exact commands, seed/tick, player actions, expected observations, and test evidence.
- **Gate:** Mark COMPLETE / PARTIAL / BLOCKED based on evidence. Wait for human acceptance before the next phase.

### AI experiments (Karpathy-style, bounded)

Use controlled experiments for uncertain design choices: terrain quality, ecological parameters, rendering throughput, snapshot intervals, pathfinding costs, and similar measurable questions.

1. Establish a working baseline and a reproducible scenario.
2. State one hypothesis and a measurable metric, including correctness constraints.
3. Change one principal variable at a time; keep a fixed seed, ruleset, device and build mode for comparisons.
4. Test and measure; keep an improvement only if it preserves correctness and improves the target metric. Otherwise revert the experiment's own changes.
5. Record the decision and evidence in the `PLAN.md` Decision Log. Do not run open-ended optimization loops or optimize away determinism.

## Verification contract

Every reported check must have one honest status:

- `PASS`: actually executed and met the criterion.
- `FAIL`: executed and failed.
- `BLOCKED`: impossible to run in the available environment; say why.
- `NOT RUN`: not attempted.

Capture the exact command or manual procedure, environment/device, relevant seed and ticks, observed result, and log/commit where available. A JVM unit test does not prove Android rendering works. No fabricated benchmark numbers or unexecuted "passes".

Use stable baseline scenarios when relevant:

- Main world seed: `104729`; alternate seeds: `271828`, `424242`.
- Determinism: compare canonical state at ticks `0`, `300`, `600`, `900` after equivalent runs/reloads.
- Branching: fork at tick `300`, apply reforestation to the child, advance both to tick `900`, compare real measured vegetation/food/wildlife values, save/reload, and re-check branch isolation.

These are target fixtures, not claims that the fixtures already exist. Do not assert predetermined ecological outcomes without simulation evidence.

## Autonomy, Git and safety

- A lead agent owns integration. Subagents may work in distinct branches/worktrees when the environment supports safe isolation; never have concurrent agents edit the same shared contracts or Gradle files.
- Do not discard user changes or use `git reset --hard`, destructive `clean`, force-push, delete assets, or overwrite saves without permission.
- Request human approval before changing the renderer choice, making a major architecture reversal, changing public save compatibility, deleting user data, expanding platform scope, or taking irreversible actions.
- Keep source asset packs in `assets/`; place curated runtime assets in the actual project's resource location after verifying format, scaling, animations, and loading. Use Git LFS for large committed assets as appropriate. Do not undertake asset-license audits as part of this engineering plan.
- If a device/SDK is missing, continue independent work and record the missing verification. If a dependency fails, produce a minimal reproduction and evidence before proposing replacement.
- Keep changes reviewable; update code/docs when behavior changes. Avoid unnecessary new files and planning bureaucracy.

## Phase completion report

At the end of each requested phase, reply in this format:

```text
PHASE: NN - name
STATUS: COMPLETE | PARTIAL | BLOCKED
PLAYER-VISIBLE RESULT: what actually works
IMPLEMENTATION: key files/modules changed
VERIFICATION: command or manual test -> PASS/FAIL/BLOCKED/NOT RUN
DETERMINISM / SAVE / BRANCH: relevant hash or integrity checks
PERFORMANCE: observed metrics and device, or not measured
DEMO: reproducible steps, seed and ticks
LIMITATIONS: unresolved defects and constraints
DECISIONS: important trade-offs
APPROVAL NEEDED: yes
```

Update `PLAN.md` Progress Ledger with the same factual status and evidence. `COMPLETE` means ready for review, NOT automatically accepted. Mark a phase `ACCEPTED` only after an explicit user decision. Never begin the next phase silently.

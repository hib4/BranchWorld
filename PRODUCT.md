# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users
Cozy strategy and diorama players: people who enjoy tending calm, crafted miniature worlds, watching them live, and returning to a world they care about. They play in short and long sessions, often pausing to look, follow a creature or read what happened.

## Product Purpose
BranchWorld is a 3D world-shaping game about living islands. The player is the Keeper: they watch an island's ecology and villages, make a few consequential interventions (call rain, restore a grove, protect habitat, answer a village's request), rewind to a turning point, try another way, and compare the futures that result. Success is a player who cares about an island, understands why its futures differ, and wants to come back to it or to the next story.

## Positioning
Playable counterfactual history. Two futures are simulated independently from the same past by the same deterministic rules, and the difference is shown in the world itself (the Living Echo), not in a chart. No outcome is scripted.

## Operating Context
- One shared Compose Multiplatform interface runs on Android phones (portrait, touch) and Desktop (landscape window, mouse and keyboard); both are equal targets. iOS compiles but is not yet a shipping target.
- The live 3D island (Kool renderer) is always behind the interface, from the title screen onward; the interface sits over it.
- Play is paused often: to read a village's request, compare histories, inspect a creature or choose an intervention.
- Crafted stories: The Fading Grove (Chapter I, drought) and The Last Crossing (Chapter II, a deer herd's seasonal pasture against a growing village). Sandbox on any island. A World Atlas keeps every island's histories, closed chapters and marked moments.

## Capabilities and Constraints
- Deterministic shared-Kotlin simulation; every number and event shown comes from real state. Never display fabricated outcomes.
- Autosave per island, profile with settings and progression, legacy save import.
- Timeline with branches, rewind to the turning point, Living Echo comparison, chronicle of village events.
- Interventions are limited (one charge until rain refills it); villages raise petitions the Keeper may answer.
- Diagnostics (tick tools) stay behind a developer setting.

## Brand Commitments
- Name: BranchWorld. Tagline: "Shape a world. Change its fate. Discover what could have been."
- Vocabulary: Keeper, intervention, world-shaping, alternate futures, living histories. Never religious-role terms for the player.
- Place names are part of the content: Hollowmere, Reedford, Cairnholt, Westmead, Cairnwater, Heron Reach.

## Evidence on Hand
- Real in-engine captures under `build/verification/*` (Desktop Vulkan and Pixel 9 AVD).
- Asset packs: KayKit Forest, Medieval, Adventurers, Character Animations; Kenney Nature, Cube Pets, Interface Sounds (`assets/`).
- No player testimonials, sales, review scores or player counts exist; none may be invented.

## Product Principles
1. The island is the subject; the interface serves looking at it.
2. Every consequence is real and traceable to the simulation.
3. Calm, not idle: the player always knows what is happening and what they could do next.
4. Choices are tradeoffs, never a correct button.
5. Histories are kept, never silently discarded.

## Accessibility & Inclusion
- Touch targets at least 48 dp; text in sp that follows system font scale, verified to 1.3×.
- Reduce Motion setting for camera and decorative motion.
- Keyboard and system Back reach every exit; screen-reader labels on icon-only controls.

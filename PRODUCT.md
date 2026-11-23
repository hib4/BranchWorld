# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users
Sandbox and simulation players: people who like to set something up, let it run and see what happens. They enjoy small living worlds, following one animal for a while, and asking "what if" by changing a world and watching it answer. They play in short and long sessions, often pausing to look closely or speeding time up to see where things lead.

## Product Purpose
BranchWorld is a 3D living-world sandbox. The player creates a small island world, sets down animals, plants and (later) structures directly in the 3D scene, and watches them live on their own: rabbits graze, dig warrens and raise litters; wolves form packs and hunt together; populations boom and crash; fire and rain change the land. The core loop is **Create → Populate → Observe → Experiment → Discover**. Success is a player who keeps returning to their worlds because something surprising always happens, and who understands why it happened.

## Positioning
A living world that writes its own stories. Nothing is scripted and there are no objectives: every chase, escape, boom and collapse comes from deterministic rules shared by every platform. The player can rewind any world and try it another way, then compare the two histories in the world itself.

## Operating Context
- One shared Compose Multiplatform interface runs on Android phones (portrait and landscape, touch) and Desktop (window, mouse and keyboard); both are equal targets. iOS runs the same shared rules but shows a 2D fallback view without placement.
- The live 3D island (Kool renderer) is always behind the interface, from the title screen onward.
- Title: Continue, New world, My worlds, Settings. New world: island or seed, Empty land or Wild, a name.
- In the world: a name board with population pegs, and a dock with time controls (pause, 1×–8×), the placement palette and What happened, Timeline and Menu pegs. Taps and clicks on the island place, select or lift out.
- Play is often paused to place things, read a creature's card or look back through what happened.

## Capabilities and Constraints
- Deterministic shared-Kotlin simulation (rules v12): perception, alarms, flight, warrens, mating and litters, packs, dens, cooperative hunts, starvation, wildfire and regrowth. Every number and event shown comes from real state; outcomes are never fabricated.
- Placement is a small set of recorded commands (set down, lift out, plant, clear) with stated reasons when refused and caps measured against a performance budget.
- Each world has its own autosaved slot; the timeline keeps branches that can be compared and shared as codes; a chronicle records moments with their causes.
- Diagnostics stay behind a developer setting.

## Brand Commitments
- Name: BranchWorld. Tagline: "Create a world. Fill it with life. Watch what it becomes."
- Vocabulary: world, life, set down, lift out, watch, experiment, history. The player is simply the player; never religious-role terms.
- Island names (The Fading Grove, Cairnwater, Heron Reach) and creature names are part of the content.

## Evidence on Hand
- Real in-engine captures under `build/verification/living-sandbox-10` (Desktop Vulkan and Pixel 9 AVD).
- Asset packs: KayKit Forest, Medieval, Adventurers, Character Animations; Kenney Nature, Cube Pets, Interface Sounds (`assets/`).
- No player testimonials, sales, review scores or player counts exist; none may be invented.

## Product Principles
1. The world is the subject; the interface serves looking at it and reaching into it.
2. Nothing is scripted: every consequence is real and traceable to the simulation.
3. The player never has to act; a world left alone keeps living.
4. Every placement is a question the world answers in its own time.
5. Histories are kept, never silently discarded.

## Accessibility & Inclusion
- Touch targets at least 48 dp; text in sp that follows system font scale, verified to 1.3×.
- Reduce Motion setting for camera and decorative motion.
- Keyboard and system Back reach every exit; screen-reader labels on icon-only controls, including the palette and population pegs.

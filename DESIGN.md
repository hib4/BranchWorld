---
name: BranchWorld
description: Create a world. Fill it with life. Watch what it becomes.
colors:
  beech: "#E9CDA2"
  beech-light: "#F6E2C2"
  beech-edge: "#C99F6B"
  beech-disabled: "#D9C3A0"
  walnut: "#2B1D12"
  walnut-soft: "#5B4330"
  pale-ink: "#FFFAF2"
  weld: "#E2B13C"
  weld-edge: "#B08423"
  madder: "#B5462F"
  madder-edge: "#7E2C1C"
  woad: "#2F5D8A"
  woad-edge: "#1C3C5C"
  leaf: "#4E7B3A"
  leaf-edge: "#30521F"
  umber: "#7A5A3C"
  umber-edge: "#4F3824"
  switch-groove: "#B98F5C"
  outline: "#9C7448"
  outline-variant: "#CBA676"
  scrim: "#1A120A8C"
typography:
  display-large:
    fontFamily: "Fredoka, sans-serif"
    fontSize: "52px"
    fontWeight: 700
    lineHeight: "54px"
    letterSpacing: "0em"
  display-medium:
    fontFamily: "Fredoka, sans-serif"
    fontSize: "42px"
    fontWeight: 700
    lineHeight: "46px"
    letterSpacing: "0em"
  display-small:
    fontFamily: "Fredoka, sans-serif"
    fontSize: "34px"
    fontWeight: 700
    lineHeight: "38px"
    letterSpacing: "0em"
  headline-large:
    fontFamily: "Fredoka, sans-serif"
    fontSize: "30px"
    fontWeight: 600
    lineHeight: "34px"
    letterSpacing: "0em"
  headline-small:
    fontFamily: "Fredoka, sans-serif"
    fontSize: "23px"
    fontWeight: 600
    lineHeight: "28px"
    letterSpacing: "0em"
  title-large:
    fontFamily: "Fredoka, sans-serif"
    fontSize: "20px"
    fontWeight: 600
    lineHeight: "24px"
    letterSpacing: "0em"
  title-medium:
    fontFamily: "Fredoka, sans-serif"
    fontSize: "17px"
    fontWeight: 600
    lineHeight: "22px"
    letterSpacing: "0em"
  title-small:
    fontFamily: "Fredoka, sans-serif"
    fontSize: "15px"
    fontWeight: 600
    lineHeight: "20px"
    letterSpacing: "0em"
  body-large:
    fontFamily: "Nunito, sans-serif"
    fontSize: "17px"
    fontWeight: 500
    lineHeight: "24px"
  body-medium:
    fontFamily: "Nunito, sans-serif"
    fontSize: "15px"
    fontWeight: 500
    lineHeight: "21px"
  body-small:
    fontFamily: "Nunito, sans-serif"
    fontSize: "14px"
    fontWeight: 500
    lineHeight: "19px"
  label-large:
    fontFamily: "Fredoka, sans-serif"
    fontSize: "17px"
    fontWeight: 600
    lineHeight: "20px"
    letterSpacing: "0em"
  label-medium:
    fontFamily: "Fredoka, sans-serif"
    fontSize: "15px"
    fontWeight: 500
    lineHeight: "18px"
    letterSpacing: "0em"
  label-small:
    fontFamily: "Fredoka, sans-serif"
    fontSize: "13px"
    fontWeight: 500
    lineHeight: "16px"
    letterSpacing: "0em"
rounded:
  xs: "8px"
  compact: "10px"
  sm: "12px"
  md: "14px"
  lg: "18px"
  xl: "24px"
  full: "9999px"
spacing:
  hairline: "4px"
  tight: "6px"
  sm: "8px"
  board-sm: "10px"
  md: "12px"
  lg: "16px"
  xl: "24px"
components:
  block-large-painted:
    backgroundColor: "{colors.leaf}"
    textColor: "{colors.pale-ink}"
    typography: "{typography.headline-small}"
    rounded: "{rounded.lg}"
    padding: "6px 20px 12px"
    height: "78px"
  block-medium:
    backgroundColor: "{colors.beech}"
    textColor: "{colors.walnut}"
    typography: "{typography.title-large}"
    rounded: "{rounded.md}"
    padding: "6px 18px 12px"
    height: "56px"
  block-small-bare:
    backgroundColor: "{colors.beech-light}"
    textColor: "{colors.walnut}"
    typography: "{typography.label-large}"
    rounded: "{rounded.md}"
    padding: "6px 12px 12px"
    height: "52px"
  block-small-chosen:
    backgroundColor: "{colors.woad}"
    textColor: "{colors.beech-light}"
    typography: "{typography.label-large}"
    rounded: "{rounded.md}"
    padding: "6px 12px 12px"
    height: "52px"
  block-compact:
    backgroundColor: "{colors.beech-light}"
    textColor: "{colors.walnut}"
    typography: "{typography.title-small}"
    rounded: "{rounded.compact}"
    padding: "6px 8px 12px"
    height: "48px"
  palette-chosen:
    backgroundColor: "{colors.leaf}"
    textColor: "{colors.pale-ink}"
    typography: "{typography.title-small}"
    rounded: "{rounded.compact}"
    padding: "6px 8px 12px"
    height: "48px"
  palette-lift-out-chosen:
    backgroundColor: "{colors.madder}"
    textColor: "{colors.pale-ink}"
    typography: "{typography.title-small}"
    rounded: "{rounded.compact}"
    padding: "6px 8px 12px"
    height: "48px"
  block-disabled:
    backgroundColor: "{colors.beech-disabled}"
    textColor: "{colors.walnut-soft}"
  board:
    backgroundColor: "{colors.beech}"
    textColor: "{colors.walnut}"
    rounded: "{rounded.lg}"
    padding: "16px 16px 22px"
  peg-button:
    backgroundColor: "{colors.beech}"
    textColor: "{colors.walnut}"
    rounded: "{rounded.full}"
    size: "48px"
  peg:
    backgroundColor: "{colors.beech-light}"
    textColor: "{colors.walnut}"
    typography: "{typography.label-medium}"
    rounded: "{rounded.sm}"
    padding: "4px 10px"
---

# Design System: BranchWorld

## Overview

**Creative North Star: "The Painted Wooden Toy Set"**

Every control is a painted wooden piece set down on the diorama, like a toy on a play table. The live 3D island is always the subject and always full-bleed behind the interface; the interface is a small set of chunky oiled-beech pieces resting on it. A piece has a face, a visible thickness band under that face, and a soft contact shadow on the table. Nothing floats in glass, nothing is printed on paper.

The material language carries meaning. Bare wood means available but quiet; paint means chosen or primary, and each plant-dye paint has one role: leaf for creating and placing, woad for a chosen state, madder for lifting out and refusals. The same toy language reaches into the 3D world: the placement ghost is a painted peg standing on the island, green where a tool can act and madder where it cannot, and a picked creature stands in a turning cream ring. Letters are stamped in rounded Fredoka; anything you read for longer than a glance is set in Nunito. Density is low: few pieces, large faces, generous touch targets, and the island visible between them.

New states (a creature's card, a short note about where something can go, the prompt to start a new history in the past) arrive as new pieces placed on the table with a short drop and settle. Pressing a piece sinks its face into its own thickness. Dark glass, vellum, parchment and gold ornament are the anti-reference: none of them belong on this table.

**Key Characteristics:**
- Island full-bleed at every size; the interface is a few placed pieces, never a frame or panel wall.
- Pieces are physical: face, 6 dp thickness band in the paint's darker edge tone, two-skirt contact shadow, 1 dp top highlight.
- Bare beech = quiet/available; plant-dye paint = chosen/primary; one paint per role.
- Fredoka stamps every label and heading at 0 letter-spacing, mixed case; Nunito carries reading text.
- Motion is set-down (drop and settle) and press (face sinks); Reduce Motion snaps both.

## Colors

Oiled beech and walnut ink as the ground, with five plant-dye paints used only where a piece is chosen or primary, each for one role.

### Primary
- **Leaf Green** (leaf): the creating paint. Continue and New world on the title, Create world, the chosen animal or plant in the palette, the chosen island and start, Open on a world, the valid placement ghost; Material `tertiary`. Carries pale ink.
- **Woad Blue** (woad): the chosen-state paint. The current speed, Look when chosen, Following on a creature card, the selected option in a block choice, the switch knob when on, Material `primary`. Carries beech-light ink (about 5.4:1).

### Secondary
- **Madder Red** (madder): the taking-away and refusal paint. Lift out when chosen, the refused placement ghost, low meters, Material `secondary` and `error`. A paint for faces with pale ink; small text stays off madder.
- **Weld Yellow** (weld): the middle level of a creature's meters. The only paint that carries walnut ink (about 8.2:1).
- **Umber Brown** (umber): developer-only pieces that open content kept from earlier rule sets; carries beech-light ink (about 4.9:1).

### Neutral
- **Oiled Beech** (beech): the face of every board and every bare block; the app's surface and background role.
- **Beech Light** (beech-light): hovered bare blocks, bare blocks inside a board (so they read against the board), peg stamps, and the ink on darker dyes.
- **Beech Edge** (beech-edge): the thickness band under bare wood and the frame of the inlaid mini-map.
- **Walnut Ink** (walnut): all text and icons on beech and weld; the 2.5 dp keyboard focus outline.
- **Soft Walnut** (walnut-soft): secondary text (taglines, details, dates) on beech, about 6.0:1; disabled ink.
- **Pale Ink** (pale-ink): text on leaf and madder.
- **Edge tones** (weld-edge, madder-edge, woad-edge, leaf-edge, umber-edge): each paint's thickness band, about 62% of the paint's value. A paint never appears without its edge tone below it.
- **Scrim** (scrim): warm walnut at 55% under modal boards (pause, settings, photo hint states).

### Named Rules
**The Bare-or-Painted Rule.** A piece is either bare beech or painted; paint means chosen or primary. On any surface at most one or two pieces are painted, and the rest stay bare.

**The One Paint per Role Rule.** Each paint means one thing everywhere: leaf creates and places, woad marks what is chosen, madder takes away or refuses. Never reassign a dye for contrast or variety.

**The Ink Follows the Paint Rule.** Text colour on a piece is decided by `inkOn(paint)`: walnut on beech, beech-light and weld; pale ink on leaf and madder; beech-light on woad and umber. Do not pick ink per screen.

## Typography

**Display Font:** Fredoka (Medium 500, SemiBold 600, Bold 700), bundled, sans-serif fallback
**Body Font:** Nunito (Medium 500 for normal, Bold 700 for semibold/bold, ExtraBold 800), bundled, sans-serif fallback

**Character:** Fredoka's soft, round, heavy letters read as shapes stamped into wood and stay legible at dock size; Nunito is a warm rounded sans that keeps paragraphs calm and readable over long pauses.

All sizes ship in sp and follow the system font scale and the in-game text-size setting (verified to 1.3x); frontmatter values are the 1x sizes.

### Hierarchy
- **Display** (700, 52/42/34, line 54/46/38): the BranchWorld name sign only; medium on wide unshort windows, small elsewhere.
- **Headline** (600, 30/26/23, line 34/30/28): menu page titles (large), board sheet titles and the Large block label (small).
- **Title** (600, 20/17/15, line 24/22/20): Medium block labels, world row names, the world's name on phones, setting names, Compact block labels.
- **Body** (500, 17/15/14, line 24/21/19): a creature's current thought, hand notes, start descriptions; body-small for block details, dates, meters' labels and taglines.
- **Label** (600/500, 17/15/13): Small block labels and stamped pegs (day, population counts, "current").

### Named Rules
**The Stamped Letters Rule.** Every label and heading is Fredoka, mixed case, at 0 letter-spacing. No tracked uppercase, no kicker lines above titles.

**The Two Lines Rule.** Block labels and details clamp to two lines with ellipsis; a block never grows into a paragraph.

## Layout

The island fills the window; pieces anchor to its corners and edges inside the safe-content area, leaving the centre of the island open.

- **Title:** a name sign top-left and a shelf board bottom-left (max 352 dp wide). With a world already made: one Large leaf Continue block (the world's name and day as its detail), a Small New world block, and a row of My worlds / Settings. With none yet: a Large leaf New world block and the My worlds / Settings row. Gutter 24 dp when the width is 600 dp or more, 12 dp on phones; under 440 dp tall the tagline drops and the primary block becomes Medium.
- **Menu pages:** a heading board (peg back button + headline-large) over scrolling content. New world caps at 640 dp (island cards at 145 dp in a flow row, a seed board, a Life board with Empty land / Wild and the name field, then Create world); My worlds caps at 600 dp with world rows at 460 dp.
- **In-world HUD breakpoints:** *wide* is width at least 760 dp and height at least 420 dp; *shortLandscape* is not wide, at least 640 dp wide and wider than 1.45x its height; *tightDock* is under 460 dp wide. Side panels are used when wide or shortLandscape (up to 54% width, max 480 dp; 42% / 332 dp for the short-landscape side stack).
- **HUD composition:** the world's name board top-left (max 360 dp: name, day, population pegs), viewport pegs top-right, and a bottom-left column of the past-edit prompt, the creature card (max 340 dp), the hand note and the action dock. The dock is one board holding, in a flow row, the time controls (play/pause peg and 1×/2×/4×/8× Compact blocks), the palette (eight Compact blocks: labelled when wide, icon-only otherwise) and three pegs (What happened, Timeline, Menu). Its board padding tightens from 6 dp to 4 dp under tightDock.
- **Insets:** every board, menu and overlay sits inside the safe *drawing* area (status bar, navigation bar, display cutout). The side back-gesture zones are not reserved: pieces there are tapped, not swiped. The one horizontal drag, the timeline scrubber, opts out of system edge gestures on Android.
- **Phone spacing:** windows under 600 dp wide (`LocalSpacing`, compact) put pieces 12 dp from the screen edge and pad boards 12 dp, so text starts about 24 dp in. Larger windows keep each surface's own gutter (10–24 dp) and 16 dp board padding.
- **Pause board:** on short, wide windows (at least 620 dp wide and under 440 dp tall) the actions form rows of four equal Small blocks with Resume painted first, board max 820 dp. Otherwise Resume is a full-width painted block above a two-column grid of bare blocks, board max 428 dp.
- **Rhythm:** 8 dp between pieces is the default; 6 dp inside block choices; 10–12 dp between cards and menu sections; boards pad 16 dp, 12 dp on phones (10–13 dp for the title shelf and HUD boards).

## Elevation & Depth

Depth is physical, not atmospheric. Every piece is drawn as a block sitting on the table: a face, a thickness band offset below it in the paint's edge tone, and two translucent shadow skirts that feather the contact edge without a blur. There are no floating glass layers and no drop shadows detached from a piece. Stacking is literal: a block on a board has its own thickness on top of the board's.

### Shadow Vocabulary
- **Contact skirt, outer** (walnut shadow #201208 at 10%, offset by thickness + 3 dp, spread 3 dp): the wide soft edge of every piece's contact.
- **Contact skirt, inner** (same at 18%, offset by thickness + 1.5 dp, spread 1.5 dp): the tight dark edge under every piece.
- **Thickness band** (edge tone, offset 6 dp for blocks and boards, 4 dp for the title shelf, dock and round pegs): the piece's own side.
- **Top highlight** (white 21%, 1 dp tall, inset from the corners): the oiled sheen along the face's top edge.
- **Island text shadow** (walnut #1A120A at 80%, 0/2 offset, 10 blur): only for text set directly on the island.

### Named Rules
**The Real Thickness Rule.** Every interactive piece shows its thickness band. A flat chip or a borderless text button is not part of this world.

**The Press Sinks Rule.** Pressing lowers the face into its own thickness (blocks by thickness minus 1 dp, pegs by 3 dp); it never lifts, glows or scales.

## Shapes

Chunky rounded rectangles, sized by piece: boards 18 dp; Large blocks 18 dp, Medium and Small 14 dp, Compact 10 dp; stamped pegs and the setting row hit area 12 dp; the mini-map frame 12 dp outer and 8 dp inner; round pegs are full circles (40 dp drawn in a 48 dp target); the switch groove and knob are pill-shaped. No hard corners, no hairline borders: the edge of a piece is its thickness band, not a stroke. Bare wood carries faint, wavy grain lines (8 strands at 5.5–11% opacity) only on pieces shorter than 150 dp, so reading boards stay clean.

## Components

### Blocks (buttons)
Tactile and toy-like: a block is a wooden piece you press.
- **Shape:** rounded per size (18 / 14 / 14 / 10 dp); minimum heights Large 78, Medium 56, Small 52, Compact 48 dp; 6 dp face plus 6 dp thickness padding vertically, horizontal padding 20 / 18 / 12 / 8 dp; 10 dp between icon and label.
- **Painted (primary/chosen):** the role's paint with `inkOn` ink; optional 22 dp icon (26 dp on Large); optional detail line in body-small.
- **Bare:** beech, or beech-light when sitting on a board, with walnut ink and grain.
- **Hover:** bare goes to beech-light; painted shifts 7% toward white (walnut-ink paints) or black (light-ink paints).
- **Focus:** a 2.5 px walnut outline drawn 3 px outside the face, following the sink.
- **Disabled:** face #D9C3A0 with soft walnut ink.
- **Semantics:** plain blocks are buttons; with `selected` they are radio buttons, with `checked` checkboxes, so the painted state is announced.

### Pegs
- **Peg button:** a round beech peg, 48 dp target, one 18 dp walnut icon, 4 dp thickness; close, back, camera reset. Always carries a screen-reader label.
- **Stamped peg:** a small beech-light (or painted) tab with label-medium text and 10/4 dp padding; quiet information such as "Day 1 · dawn".
- **Population peg:** a stamped peg holding a 16 dp species icon and its count; announced as "Rabbits 4". Only species present are shown.

### Boards (cards and containers)
- **Corner Style:** 18 dp.
- **Background:** beech with grain when short; content ink follows `inkOn`.
- **Depth:** 6 dp thickness by default, 4 dp for the title shelf and the action dock.
- **Internal Padding:** 16 dp, plus the thickness at the bottom; 8 dp gap between children.
- **Board sheet:** a board with a headline-small title row and one close peg.
- **Scrolling:** content is never covered. While more lies below, a 22 dp strip under the scroll area holds an 18 dp soft-walnut down chevron; the strip disappears at the end of the list.

### Choice and settings
- **Block choice:** a row of Small blocks, 6 dp apart, the chosen one woad, the rest beech-light; a selectable group.
- **Wood switch row:** name in title-medium, one detail line in soft walnut, and a 54 x 30 dp slider: a groove (#B98F5C) with a knob that slides and shifts from beech-light to woad when on. Whole row is the 56 dp toggle target.

### Navigation (title shelf and action dock)
- **Title shelf:** one Large leaf block for the next action (Continue with the world's name and day, or New world) above bare Small blocks.
- **Action dock:** a 4 dp-deep board; see HUD composition.

### Placement palette
A selectable group of eight Compact blocks: Look, Rabbit, Wolf, Deer, Fox, Tree, Grass, Lift out, each with its line icon. Exactly one is always chosen: leaf for an animal or plant, woad for Look, madder for Lift out. On phones the blocks are icon-only and keep their spoken labels.

### Time controls
A round play/pause peg followed by four Compact speed blocks (1×, 2×, 4×, 8×) in a selectable group; the current speed is woad. Space and the number keys mirror them on Desktop.

### Creature card
A board (max 340 dp) for the creature the player picked: species icon, name in headline, "species, sex · age" in soft walnut and a close peg; the creature's current thought in body-large (a polite live region); four labelled meters (food, water, energy, stamina) as 10 dp rounded bars, leaf above 45%, weld above 20%, madder below; one line of facts (set down or born here, pack size, catches, litters, carrying young); then Follow (woad when following) and Lift out.

### Hand note and past-edit prompt
- **Hand note:** a small board with one body-medium line that says what the chosen tool does, or why a spot was refused; it fades after a few seconds.
- **Past-edit prompt:** when the player reaches into a past moment, a board names the day and offers "Start a new history here" (woad, branch icon) or "Back to now".

### Placement ghost and selection ring (in the 3D world)
- **Ghost:** a painted peg standing on the hovered cell: a raised band round the cell, a stake and a round knob, leaf when the tool can act there and madder when it cannot. It bobs gently unless Reduce Motion is on, and is slightly smaller for plants.
- **Selection ring:** a cream band with walnut notches round the picked creature, turning slowly and following it as it moves.

### Island mini-map (signature)
A painted map drawn from the island's real generated terrain, inlaid in a beech-edge frame with a sea inset; biomes shaded by height. It shows each island on New world and each world in My worlds.

## Do's and Don'ts

### Do:
- **Do** keep the island full-bleed and visible between pieces; on phone portrait the title leaves the middle of the screen as island only.
- **Do** build every control from Board, Block, PegButton, Peg, BlockChoice or WoodSwitchRow so thickness, press, focus and Reduce Motion come for free.
- **Do** paint at most the one or two pieces that are chosen or primary; everything else is bare beech.
- **Do** paint by role (leaf create, woad chosen, madder take away) and take text colour only from `inkOn`.
- **Do** keep touch targets at least 48 dp and text in sp so the 1.3x scale reflows rather than clips.
- **Do** honour Reduce Motion: set-down, press sink, switch slide, the drop-in of placed animals and the ghost's bob all snap to their end state.
- **Do** say why when a placement is refused, in plain words, beside the madder ghost.

### Don't:
- **Don't** use dark glass, vellum or parchment sheets, gold ornament or translucent HUD panels.
- **Don't** put objectives, requests or nagging guide notes in play; the world is never waiting for the player.
- **Don't** use tracked uppercase kickers or eyebrow lines over titles; headings are stamped Fredoka in mixed case.
- **Don't** build menus as icon-title-subtitle lists; a menu is a few blocks on a shelf.
- **Don't** draw a piece without its thickness band or add strokes and borders to outline it.
- **Don't** set small text in madder on beech; madder is a face paint with pale ink. Warnings on beech use walnut ink.
- **Don't** expose tick counters, hashes or diagnostics outside the developer setting.

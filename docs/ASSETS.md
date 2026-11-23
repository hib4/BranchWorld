# Assets and credits

[← Back to README](../README.md)

## Credits

| Creator | Packs | Licence |
| --- | --- | --- |
| [Kenney](https://www.kenney.nl) | Nature Kit, Cube Pets, Interface Sounds | CC0 1.0 |
| [Kay Lousberg](https://www.kaylousberg.com) | KayKit Forest Nature, Medieval Hexagon, Adventurers, Character Animations | CC0 1.0 |
| Popyca | Cute Pets & Wildlife (free sample) | CC0 1.0 |
| Fredoka and Nunito authors, via Google Fonts | Fredoka, Nunito | SIL Open Font License 1.1 |

Each pack's licence file sits next to it under `assets/`. The surf, wind, rain and bird ambience is original to BranchWorld.

## Source packs

The eight downloaded packs live under `assets/`. The formats and animation entries below were inspected in the local files. The intended uses are candidates for later integration.

| Pack | Inspected runtime candidates and alternatives | Animation and intended use |
| --- | --- | --- |
| KayKit Forest (`environment/kaykit-forest`) | 105 glTF models with BIN and PNG dependencies. FBX and OBJ/MTL variants. | Static trees, rocks, and vegetation for the diorama and island. |
| Kenney Nature (`environment/kenney-nature`) | 329 GLB models. FBX, OBJ/MTL, DAE, STL, and PNG previews. | Static rocks, ground props, and vegetation for the diorama and terrain. |
| Kenney Cube Pets (`animals/kenney-cube-pets`) | 24 GLB models plus FBX and OBJ/MTL. | Runtime rabbit, deer, fox and wolf use the CC0 models and their idle, walk, run and eat clips; the wolf is the dog model with a grey coat applied in the renderer. |
| Vuena Pets (`animals/vuena-pets`) | Three GLB and FBX models with PNG textures. | Skinned cat, fox, and owl with embedded clips including idle, walk, eat, and sleep. Candidates for additional wildlife. |
| KayKit Medieval (`buildings/kaykit-medieval`) | 221 glTF/BIN models plus FBX, OBJ/MTL, and PNG textures. | Static buildings and props; candidates for placeable structures. |
| KayKit Adventurers (`characters/kaykit-adventurers`) | Six skinned character GLBs, two animation GLBs, and glTF/BIN, FBX, OBJ/MTL, and PNG assets. | Separate Rig Medium general and locomotion clips. Candidates for future placeable people. |
| KayKit Character Animations (`characters/kaykit-character-animations`) | 14 GLB and 14 FBX animation files plus two mannequin variants in each format. | Separate Rig Medium and Rig Large motion clips. Candidate motion library for compatible characters. |
| Kenney Interface Sounds (`audio/kenney-interface-sounds`) | 100 OGG audio files. | Six clips are bundled as small mono WAVs: fork and save feedback, plus `select_002`, `confirmation_002`, `back_002` and `open_002` for the menus (converted with ffmpeg to 22.05 kHz mono). |
| Fredoka and Nunito (`assets/fonts`) | Static TTF instances from Google Fonts, SIL Open Font License 1.1 (`Fredoka-OFL.txt`, `Nunito-OFL.txt`). | Fredoka Medium, SemiBold and Bold for titles and controls and Nunito Medium, Bold and ExtraBold for interface text, bundled in `shared/src/commonMain/composeResources/font`. |

## Runtime assets

The runtime tree set uses Kenney Nature’s `tree_pineRoundA`, `tree_pineRoundB`, and `tree_pineTallA_detailed`, with in-engine matte materials and normalized ground pivots. The original BranchWorld surf, wind, rain, and bird layers can be regenerated with `python3 assets/audio/branchworld/generate_coast.py`. World Sounds controls the ambience and interaction cues. Reduce Motion holds decorative effects and camera transitions while keeping weather and lighting visible.

Keep the original packs in `assets/`. Import only the models, textures, and clips verified for runtime use on every platform. iOS reads the same `diorama/` folder through the shared glTF reader.

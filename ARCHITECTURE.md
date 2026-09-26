# OmniLogistics — NeoForge 1.21.1 (ATM10 line)

## 1. Dependency tree

```
omnilogistics
├── net.neoforged.moddev 2.0.146        (Gradle plugin, ModDevGradle 2)
│   └── net.neoforged:neoforge 21.1.249 (MC 1.21.1, Java 21)
│       ├── Capabilities.ItemHandler.BLOCK / EnergyStorage.BLOCK   (transfer + FE)
│       ├── DataComponents / DataComponentPatch                    (filter engine)
│       ├── RegisterPayloadHandlersEvent + StreamCodec             (network)
│       └── ModConfigSpec                                          (config; replaces Cloth Config)
├── test: junit-jupiter 5.10.2 via neoForge.unitTest               (boots vanilla registries)
└── optional / compileOnly, added only when the Extractor gets gem support:
    └── dev.shadowsoffire:Apotheosis (maven.blamejared.com) — reads the `apotheosis:sockets` component
```

Deliberately not added: Cloth Config (NeoForge has `ModConfigSpec`), Curios (no equipment slots
in scope), any AE2/RS API (the Exposer speaks plain `IItemHandler`, which both storage mods already read).

## Status (2026-09-05, v0.2.0)

| module | package | what ships |
|---|---|---|
| filter engine + shared GUI | `core` | FilterSpec (N reference stacks - a stack passes when it matches ANY of them - flags, tags, components), 9 flags, tag/component pickers (scroll + Enter, multi-select, values shown), dark textured GUI whose reference grid grows with the host's capacity (1 / 4 / 16 / 64, three panel sheets), `OmniConfig` (config/omnilogistics-common.toml) |
| conduits | `pipe` | 3 types (item / FE / fluid) x 5 tiers (Infinity = creative), Mekanism side modes, labelled from the neighbour: NORM / EXTRACT / INSERT / OFF with connector plates, per-conduit redstone mode (wrench on the centre, or GUI), chains flow forward, self-heal on first tick. Item pipes hold a stack for exactly one tier interval (`enteredAt`) before passing it on, so a chain moves one block per interval whatever the block entity tick order (8 / 6 / 4 / 2 ticks per block); `enteredAt` rides to clients in the update tag as "Entered" and `PipeBlockEntity.markDirty` syncs in the same tick the buffer changed, so the renderer slides the stack a full block per interval off the server clock (one tick behind, matching the broadcast lag) and the seam between two pipes is continuous. GUI (`PipeMenu` / `PipeScreen`) only on the ends of a run (`isEnd()`: touches a non-conduit with our capability, has an EXTRACT / INSERT face, or holds a card); item pipes carry one Logistics Card slot = the filter, empty slot = plain pipe |
| Inventory Exposer | `exposer` | filtered IItemHandler view of the target side |
| Wireless Router | `router` | GUI tick field (any 1..200 ticks, typed, nothing gates it; unset = 8), cards configurable in place (right-click in the GUI), 9 card kinds (item, energy, fluid, void, vacuum, activator, breaker, placer, detector), FE + fluid buffers exposed on all faces, redstone output, Speed x3 (each doubles the card actions per cycle) + Range x3 upgrades, per-card overclock via `CardUpgradeRecipe` |
| Component Extractor | `extractor` | GUI tick field (any 1..200 ticks, typed; unset = config `timeTicks`), 3 lanes max (Parallel upgrades), Speed x3, Component Module (embedded stacks, incl. `omnilogistics:embedded_items`), Fusion Module (FUSE mode), in-place cleaning, auto-eject |
| Void Miner | `miner` | 4 tiers (one block class each, one BE type), each cycle costs FE up front and rolls the tier loot table (`data/omnilogistics/loot_table/miner/<tier>.json`), Logistics Card slot = filter (edited in place, a miss still spends the cycle), GUI tick field (any 1..200 ticks, typed; unset = the tier time), Speed x3 (each doubles both the items and the FE per cycle), redstone pause, 4 output slots that auto-eject; `VoidMinerRenderer`: beam + spinning tier ring + the pending item descending + portal particles (client ticker) |
| Batch Distributor | `distributor` | one block against an AE2 Pattern Provider: 6 lanes, one Logistics Card per lane, one card = one destination in SPLIT mode; CLUSTER mode instead hands the whole batch to one machine and the next batch to the next, so five identical machines behind one Pattern Provider craft five times faster. Parallel Upgrades add 3 lanes each (6 -> 12). AE2 walks our slots carrying the remainder (`ExternalStorageFacade.insertExternal`), so each ingredient lands in the first lane whose filter matches and that lane delivers it to its own machine next tick; an unfiltered lane accepts nothing (so lane 0 cannot eat the batch), an INVERT-only lane is the catch-all, an EXTRACT card turns its lane into the return path into the wrenched face. Filters fill themselves: a bound INSERT card with no filter is a LEARNER and keeps the first ingredient that arrives (never one an explicit lane already claims), and an unfiltered EXTRACT lane pulls whatever the machine holds that is NOT an ingredient delivered to that same machine - the product - so a recipe is set up by binding cards and running the craft once. No AE2 pattern is read; it learns from what lands in the block. The whole AE2 integration is one `Capabilities.ItemHandler.BLOCK` registration |
| Machine Monitor | `monitor` | a screen for someone else's machine: hold a Logistics Card bound to a block, right-click the monitor with it and its front face shows that block's name, FE bar, fluid bar and first item stacks. Reads plain capabilities every 10 ticks and syncs only when the snapshot changes, so it works with any mod (AE2, Mekanism, Actually Additions...). No GUI, no menu: right-click empty-handed to take the card back |
| JEI / Jade | `compat` | JEI: Extraction category + info pages + a Void Miner category (one page per tier, loot weights as percentages, read from assets/omnilogistics/miner_loot.json); Jade lines (faces, redstone, buffers, energy, cards, signal). Both load only when the mod is present |
| tests | `src/test`, `PipeGameTests`, `MachineGameTests`, `debug/SelfTest` | 2 unit tests, 26 GameTests (`gradlew runGameTestServer`), automated client run (`gradlew runClientSelfTest`, 1600x900: creates a flat world, opens card / router / in-router card / pipe / in-pipe card / miner / distributor / 16-slot card GUIs, screenshots a showcase row into run/screenshots/omni_{showcase,machines,pipes,top,miner}.png and omni_gui_*.png, writes selftest.txt, quits). Hand testing: `gradlew runClient` skips the menus and opens run/saves/OmniTest, creating it with `debug/Showcase` when missing (every block, pipes of every tier, filled buffers, supply chests, signs, plus a row of other-mod blocks by registry id when installed: Powah creative cell and Mekanism creative cube feeding our cables and miners, a Functional Storage drawer as pipe target, an "other mods" supply chest, and a full AE2 bay in row 5: creative energy cell + ME Chest with a 4k cell + Drive + Pattern Provider + Interface touching each other = one powered ad-hoc grid, our pipe pushing a source chest into the Interface, an Exposer as storage-bus target, an Elite Void Miner ejecting into the Pattern Provider, a Router with two pre-bound cards, a hand-place-these-parts chest, plus a second bay where our FE cable feeds an ae2:energy_acceptor and our pipe fills an ME Chest cell with no clicks); delete that folder to rebuild. `gradlew runClientPrepare` builds it and quits (used to copy the world into the CurseForge profile); `/omni showcase` (ops) builds the same around the player in any world |

Wrench (ours or any c:tools/wrench): pipe arm = cycle mode, pipe centre = redstone mode (or re-enable the OFF face you look at), exposer face = set target; sneak + wrench = block and contents straight into the inventory (this is the only sneak + wrench action).
Art (2026-09-05, picked by a 3-design / 3-judge panel): industrial gunmetal, fixed 7-9 tone ramps, top-left light. Machines = riveted 6px frame, louvred bay, glass screen panel with one per-kind element (router signal bars, extractor gem hatch, exposer lens), louvre + port + LEDs on top. Conduits = glass-sided gunmetal tubes (one continuous window tinted by the carried type: cyan items / yellow FE / blue fluid, 2px tier-coloured rails along the edges, a glass port in the tier-coloured flange caps) so the contents show and tiers and types read from a distance and in the inventory icon; EXTRACT / INSERT arms in a bright green / orange ramp with an outlined arrow showing the physical flow (green into the conduit, orange out to the neighbour). Items = smart cards (band, chip, kind-coloured arrow, glyph, LED), PCB upgrade modules, adjustable wrench.
Textures: `tools/gen_textures.py`; preview sheets without launching the game (isometric blocks, items, GUI; needs Pillow): `tools/preview_textures.py`; GUI layout previews: `tools/preview_gui.py`; models/recipes/loot/guide: `tools/gen_resources.py`; test template: `tools/gen_structure.py`.
Build needs no system JDK: Gradle provisions one under `~/.gradle/jdks` (set `JAVA_HOME` to it for the wrapper).
Not unit-testable here: AE2 storage bus on the Exposer and real Apotheosis gems (neither mod is on the dev classpath); the exposer is covered by the hopper GameTest and gem extraction by the embedded_items GameTest, which exercises the same code path.

## 2. ComponentPredicateEngine

`src/main/java/.../ComponentPredicateEngine.java`. One static `test(stack, reference, flags)`.
Flags are a bitmask so every block stores an `int` and every GUI toggles bits:

| flag | meaning |
|---|---|
| MATCH_ITEM | same item id as reference |
| MATCH_COMPONENTS | same component *patch* as reference |
| HAS_ENCHANTS | ENCHANTMENTS or STORED_ENCHANTMENTS non-empty (reference may be empty) |
| HAS_MOD_COMPONENTS | any component in the patch whose registry namespace != `minecraft` (Apotheosis affixes, gems, etc.) |
| IGNORE_DAMAGE | strip DAMAGE before MATCH_COMPONENTS |
| INVERT | blacklist |

"Filter without knowing item IDs" = leave the slot empty and set HAS_ENCHANTS / HAS_MOD_COMPONENTS.
Patch-based on purpose: a modded item's default components don't count, only what was stamped on the stack.

## 3. Conduits

Files: `pipe/SmartPipeBlock` (one block class per type x tier, a `Connection` blockstate per side drives the multipart model),
`pipe/ConduitBlockEntity` (modes, redstone, tick loop, `isEnd()`, `config()` / `cycle()`), `pipe/PipeBlockEntity` /
`EnergyCableBlockEntity` / `FluidPipeBlockEntity`, `pipe/PipeMenu`, `pipe/PipeScreen`, `pipe/PipeConfigPayload`.

- Per side: `NORMAL | PULL | PUSH | NONE`, shown as NORM / EXTRACT / INSERT / OFF (read from the NEIGHBOUR: EXTRACT = the conduit takes out of it). Enum names, ordinals (NBT `Sides`) and blockstate values (`plain|pull|push`) never change - they are saved data. Item filter = the Logistics Card in the pipe's slot.
- Every `tier.interval` ticks: PULL sides extract up to `tier.items` into the 1-stack buffer, then the buffer goes round-robin to
  PUSH / NORMAL sides, skipping the conduit it came from. Direct `level.getCapability` lookups (no cache; see the ponytail note).
- Config edits call `sync()` = `setChanged()` + `sendBlockUpdated` → `getUpdateTag/getUpdatePacket`; menus read the block entity.
- Glass: pipe models are plain 6x6 tubes with `render_type` translucent on every model file (no rotated twins, they would
  show through the window; the window pixels have alpha 48, so what is inside stays readable).
  Seamless joins: arms are open-ended (no end faces), the strip texture (px 10..21 x 0..11) is one continuous window with
  2px tier-coloured rails and a type-tinted pane, and is mirror-symmetric because blockstate rotations flip it.
  The centre cube is six single-face multipart parts (`_strip_v` / `_strip_h` / `_cap`, rotated from a north-face base):
  a face shows the strip when exactly the two opposite sides in its plane are connected, else the cap (px 10..21 x 12..23),
  so a straight run reads as one tube and only bends, branches and ends show a knot.
  `ConduitRenderer` (BER on all three types) draws the contents: the item in transit slides in from `lastFrom` over one
  tier interval then spins in the knot (blocks 0.28, sprites 0.36, FIXED display context), fluid is a column (thickness = fill level, the
  fluid's still sprite + tint), energy a pulsing full-bright core (`block/glow` sprite, stitched via `atlases/blocks.json`).
  Both use `Sheets.translucentCullBlockSheet()`: it is flushed before the translucent chunk layer, so the glass blends over
  the contents (late-flushed entity render types would be hidden by the glass depth). Contents reach clients through
  `markDirty()` in `ConduitBlockEntity.serverTick`: items every tick, energy / fluid every 10 ticks or on empty <-> non-empty.
  Judge the result by measuring window pixels in a screenshot (blue = water behind glass, greenish = grass): eyeballing misleads.
- GUI (`PipeScreen`, 176x246): one row per side with the neighbour block's icon + name and a NORM / IN / OUT / OFF button,
  then the card slot (item pipes), the current contents (item icon + count, or mB / FE) and the redstone button.
- Client→server: `PipeConfigPayload(pos, index)` = "cycle entry index" (0..5 sides, 6 redstone). Accepted only from the player whose
  open `PipeMenu` targets that pos and who is in reach.

### Conduit GUI (done, 2026-09-05)
- Opens only at the ends of a run (`ConduitBlockEntity.isEnd()`); a conduit in the middle right-clicks like a plain cable.
- `PipeMenu`: modes read from the block entity on both sides (it is synced on every change), one card slot for item pipes.
  `PipeScreen`: 6 side buttons (3x2, NORM / IN / OUT / OFF) + redstone button; every click sends `PipeConfigPayload(pos, byte[7])`.
- Filtering = the Logistics Card in the slot (`PipeBlockEntity.spec()`), edited in place by right-clicking it
  (`SlotCardHost`, shared with the router through the `CardSlots` interface, layout `CARD_NOMODE` so no EXTRACT / INSERT button).
  Empty slot = `FilterSpec.EMPTY` = everything passes. Old per-pipe filters (v0.2.0 `Filter` / `Flags` NBT) are dropped on load.

## 4. Inventory Exposer / Virtual Buffer — implementation plan

Goal: one block that presents a *filtered view* of an adjacent inventory as an `IItemHandler`, so
AE2 Storage Bus / RS External Storage / any pipe sees only the items that pass the component filter.
No AE2/RS API required — both mods query `Capabilities.ItemHandler.BLOCK`.

1. **Registration** — copy the pipe pattern: `ExposerBlock`, `ExposerBlockEntity`, `EXPOSER_BE`.
   State: `Direction target` (which neighbour is wrapped), `ItemStack filter`, `int flags`.
2. **FilteredView implements IItemHandler** — wraps a `BlockCapabilityCache<IItemHandler>` on
   `pos.relative(target)`:
   - `getSlots()` → delegate.
   - `getStackInSlot(i)` → delegate, or `EMPTY` if `!test(stack, filter, flags)`.
   - `insertItem(i, s, sim)` → return `s` untouched unless `test(s)`; else delegate.
   - `extractItem(i, n, sim)` → `EMPTY` unless the slot's stack passes; else delegate.
   - `isItemValid` → `test(...) && delegate`.
   If the cache returns null, behave as a 0-slot handler.
3. **Capability** — in `RegisterCapabilitiesEvent`:
   `registerBlockEntity(Capabilities.ItemHandler.BLOCK, EXPOSER_BE.get(), (be, side) -> side == be.target ? null : be.view)`
   The `target` side returns null so the wrapped inventory can't be reached through itself (loop guard).
4. **Invalidation** — whenever target/filter/flags change call `invalidateCapabilities()` so AE2/RS drop
   their cached handler and re-query.
5. **Config** — reuse `PipeConfigPayload` shape: `ExposerConfigPayload(pos, byte target, int flags)`;
   same `canInteractWithBlock` check. Reuse the filter GUI with the six side buttons collapsed to one.
6. **Virtual buffer (only if a pack needs it)** — an internal `ItemStackHandler(9)` that accepts inserts
   when the target is full and drains into it on tick. Skip until someone hits the "target is full" case.
7. **Test** — a GameTest: chest with 1 enchanted + 1 plain sword, exposer with `HAS_ENCHANTS`, assert
   `getStackInSlot` hides the plain sword and `extractItem` on it returns EMPTY.

## 5. Component Extractor (Disenchanter/Gem puller) — plan

1. `ExtractorBlockEntity`: `ItemStackHandler(4)` = equipment in, book in, equipment out, extracted out;
   `EnergyStorage(100_000)` exposed via `Capabilities.EnergyStorage.BLOCK`.
2. Per operation (costs N FE, config via `ModConfigSpec`):
   - Enchantments: `book.set(STORED_ENCHANTMENTS, equip.get(ENCHANTMENTS)); equip.remove(ENCHANTMENTS)`.
     Input book must be `Items.BOOK`; output becomes `ENCHANTED_BOOK`.
   - Gems (only when `ModList.get().isLoaded("apotheosis")`): read `apotheosis:sockets`, push each gem
     stack to the extracted slot, write back an empty socket list. Isolated in one `ApothCompat` class so
     the mod loads without Apotheosis.
3. Auto-eject: every 8 ticks push the two output slots into any adjacent `IItemHandler` (reuse the
   pipe's `pushToAnySide` logic without a filter).
4. Bookshelves: accept `Items.BOOKSHELF` in the book slot and count it as 3 books.

## 6. Wireless Router (Modular Routers + LaserIO hybrid) — plan

One block, no cables. Everything is a *card* in the router; cards carry their config as data components.

Block: `RouterBlockEntity` with `ItemStackHandler(8)` card slots + 1 internal buffer stack (Modular
Routers style: items pass through the buffer, cards act on it in slot order every N ticks).

Cards (one item, a `CardType` enum component + reused engine filter + flags):
| card | LaserIO / Modular Routers equivalent | action |
|---|---|---|
| Extractor | LaserIO Extractor / MR Puller | pull matching items from the bound inventory into the buffer |
| Inserter | LaserIO Inserter / MR Sender | push buffer into the bound inventory (round-robin across cards) |
| Stock | LaserIO Stocker / MR Sender-stock | keep the bound inventory at N of the filter item |
| Energy | LaserIO Energy card | move FE between bound `EnergyStorage` caps |
| Fluid | LaserIO Fluid card | same with `FluidHandler.BLOCK` |
| Redstone | LaserIO Redstone / MR Detector | emit/read redstone via bound position |

Binding: sneak-right-click a block with a card stores `GlobalPos` (dimension + pos) and side as a
component. Range = config (`ModConfigSpec`, default 16, 0 = unlimited). Cross-dimension allowed if the
target chunk is loaded (`level.isLoaded(pos)`), no chunk-loading.

Channels (LaserIO): an `int channel` component on Inserter/Extractor; extractors only feed inserters
on the same channel. Priority (MR): card slot order; add an `int priority` component only if slot
order isn't enough.

Transfer: a `BlockCapabilityCache` per card, rebuilt when the card stack changes. Same simulate ->
extract -> insert dance as the pipe; rate from config x Speed Upgrades, interval typed in the GUI (1..200).

GUI: reuse the pipe screen pattern — 8 card slots + click a card to open a sub-screen with the filter
slot, flags, channel, side, and mode. One `RouterCardPayload(pos, slot, componentPatch)` writes the
card's components server-side after the same range check.

Build order: Extractor + Inserter cards first (that already beats a pipe), then Stock, then Energy /
Fluid / Redstone.

## 7. Void Miner (done, 2026-09-07)

Decision: one compact block instead of an Environmental-Tech-style multiblock (structure validation, formation UX and 20+ models
would only pay off with a flawless big animation; the mod's identity is compact). The animation budget went into a BER instead.

- `miner/MinerTier` (BASIC..ULTIMATE, same colours as the pipes): time = `miner.timeTicks >> tier`, cost = `miner.costFE << 2*tier`,
  buffer = 25 items, loot table `omnilogistics:miner/<tier>` (generated by `tools/gen_resources.py`, `MINER_LOOT`; datapacks override).
- `VoidMinerBlock` (one per tier, `MapCodec` carries the tier, client ticker for particles) + `VoidMinerBlockEntity`: a cycle spends the FE
  first, rolls the table up to 8 times until the card filter passes (a miss still runs the cycle), holds the result as `pending`, and after
  `interval()` ticks (the GUI tick field, defaulting to the tier time) puts it in the 4 output slots (auto-eject every 8 ticks, `sided` = extract-only). Redstone pauses. `CardSlots` so the card
  is edited in place from `MinerMenu` (right-click, `SlotCardHost`). Client state (`mining`, `cycleStart`, `cycleTime`, `pending`) is
  pushed with `sync()` only at cycle start / end.
- `VoidMinerRenderer`: purple beam (two `Quads.column`s, alpha fading upward) out of the top port, spinning tier-coloured ring, the
  pending item descending into the port over the cycle, `getRenderBoundingBox` +1.5 blocks up; `clientTick` spawns PORTAL particles that
  converge on the beam. Textures: `miner_<tier>_{side,top,panel}` (top = centred void port, panel = expanding sonar rings per frame).
- `core/FilterRef`: the card filter reference stack wrapped in a record with equals/hashCode; NeoForge dev runs reject a bare ItemStack
  component value (found by the miner GameTest, it also broke setting a reference in any card GUI in dev).
- Tests: `MinerGameTests` (chest fills + FE spent, card keeps only cobblestone, redstone pause); SelfTest opens the miner GUI and shoots
  `omni_gui_miner.png` / `omni_miner.png` (elite miner fed by the showcase cables).

## Compatibility runs (2026-09-08)

Dev NeoForge is 21.1.249, the same build as the CurseForge profile "OmniLogi" (`C:\Users\Adm1n\curseforge\minecraft\Instances\OmniLogi`,
AE2 + ExtendedAE, Mekanism, Apotheosis + Apothic*, Industrial Foregoing, Functional Storage, Powah, Jade, JEI, Patchouli, ...). That
profile's 24 jars sit in `run/mods`, so every dev run and the self-test load them; delete `run/mods/*` for a bare run. `gradlew build` +
copy `build/libs/omnilogistics-0.2.0.jar` into the profile's `mods` to test in CurseForge. Found this way: Jade 15 refuses an interface in
`registerBlockDataProvider` (now `TickingBlockEntity.class`), Patchouli 1.21 has no `shapeless_book_recipe` serializer (the guide recipe is a
vanilla shapeless recipe whose result carries the `patchouli:book` component), and Jade asserts on a missing `config.jade.plugin_<modid>.info` key.

AE2 interop facts, verified against appliedenergistics2-19.2.17 (do not re-derive): adjacent AE2 devices auto-connect into one grid with no cable
(`InWorldGridNode.findInWorldConnections`); an ad-hoc, controller-less network carries 8 channel devices; AE2 exposes `Capabilities.ItemHandler.BLOCK`
on every `AEBaseInvBlockEntity` (Drive = 10 cell slots on every side but its front, ME Chest = input slot 0 / cell slot 1), so a storage cell can be
inserted from our code with no AE2 dependency; an unconfigured Interface accepts any item and pushes it into the network, but pulling OUT of ME through
one needs a config click; a powered ME Chest input slot needs none; cable-bus parts (storage / import / export bus, terminals) need a cable in the bus,
so the showcase leaves them in a chest. Ids: the ME Chest is `ae2:chest` (there is no `ae2:me_chest`), `ae2:pattern_provider` defaults to
push_direction=all, `ae2:drive` / `ae2:chest` default to facing=down (the showcase sets south), `industrialforegoing:black_hole_unit` does not exist
(it is `pity_/simple_/advanced_/supreme_black_hole_unit`).

## 8. Batch Distributor (done, 2026-09-08)

Answers "AE2 crafting is fiddly when one recipe feeds several places" (chosen from 3 designs by a judge panel). No splitting algorithm and no AE2
dependency: `DistributorBlockEntity.view` exposes 6 slots on every face, `accepts(i, stack)` = lane i's card is a bound ITEM card in INSERT mode whose
filter is actually configured and matches, and AE2's `insertExternal` does the rest. `serverTick` skips the tick a batch landed in, so the whole pattern
arrives before anything leaves (no half-fed machine). Deliberately cut: destination probing (a foreign simulate inside `isItemValid`, hammered by
`adapterAcceptsAll`), speed upgrades, per-lane caps, range upgrades. Known limits, by design: a lane bigger than 64 spills to AE2's send list and retries;
two lanes with the same filter misroute silently, which is the amber pip in the GUI. Files: `distributor/{DistributorBlock, DistributorBlockEntity,
DistributorMenu, DistributorScreen}`, `Transfer.pull(..., int into, ...)`, one capability line in `OmniLogistics.registerCapabilities`.

## 9. Multi-reference filter cards (done, 2026-09-09)

`FilterSpec.refs` is a list; `ComponentPredicateEngine.anyRef` applies the reference-dependent flags (MATCH_ITEM / COMPONENTS /
SELECTED / ENCHANTS) to each reference and passes on the first hit, so with one reference the behaviour is exactly what it was.
Capacity lives on the item (`LogisticsCardItem.capacity`): 1 for the plain card, 4 / 16 / 64 for `advanced|elite|ultimate_logistics_card`,
reached in the crafting grid with gold / diamond / netherite through `CardUpgradeRecipe` (components are copied, so the filter and the
binding survive the upgrade). `FilterHost.filterSlots()` tells the menu how many ghost slots to build; the grid is 8 columns wide and
everything under it shifts down, with one panel sheet per height (`filter`, `filter_tall`, `filter_huge`; the last two are 512px sheets,
which `DarkScreen` picks up automatically). `FilterRef` keeps a list and still reads a single stack, so cards written before this change
load unchanged.

## 10. Free tick interval (done, 2026-09-10)

Speed used to be an unlockable GUI step; now it is a number the player types, and Speed Upgrades pay off in amounts instead.

- `core/IntervalHost` (MIN 1, MAX 200, `clamp`): implemented by `RouterBlockEntity`, `ExtractorBlockEntity`, `VoidMinerBlockEntity`.
  Each keeps one `int interval` in NBT under `"Interval"`, where **0 means "never typed, use my own default"** (router 8, extractor
  `extractor.timeTicks`, miner `tier().time()`). The old `"Speed"` step field is dropped on load, so worlds from before this change
  come back at the base rate rather than silently 8x faster.
- `core/IntervalPayload(pos, ticks)` is the only packet, range-checked against the player like the others; `core/IntervalBox` is a
  bordered-off vanilla `EditBox` that commits on Enter or blur and mirrors the machine while idle. `DarkScreen.addIntervalBox` places
  it, refreshes it every frame, swallows the keyboard while it has focus (digits must not swap hotbar slots) and flushes it in
  `removed()`. The panel recess behind it is drawn by `tools/gen_textures.py`.
- Upgrades moved to amounts: router `mult() = 1 << speedUpgrades()` card actions per cycle (the item buffer is one stack, so a
  bigger per-action rate would move nothing; the per-card overclock still scales the rate), miner `mult()` on both the yield and the
  FE per cycle, extractor `cost() = costFE >> speedUpgrades()`. Range Upgrades are untouched.

## 11. Cross-mod auto-craft bays (done, 2026-09-10)

`debug/Showcase.clusterBay` builds the same shape twice, once per foreign mod: a chest drips ingredients through a hopper
into a Batch Distributor in CLUSTER mode, which hands each whole batch to the next of five identical machines from
another mod, and a Wireless Router with one product-filtered EXTRACT card per machine (plus one INSERT card) brings the
results into a single chest. Nothing links against AE2 or Mekanism - blocks come from `BuiltInRegistries.BLOCK` by id and
everything else is capabilities.

- **AE2**: five `ae2:molecular_assembler` with one `ae2:creative_energy_cell` touching the row (adjacent AE2 devices form
  one ad-hoc grid, so all five are powered without a cable). Each is armed with an encoded pattern written as plain NBT:
  the component `ae2:encoded_crafting_pattern` is `{inputs: 9 sparse ItemStacks (empty = {}), result, recipeId,
  canSubstitute, canSubstituteFluids}`, applied through `DataComponentPatch.CODEC`. AE2 re-runs the recipe on load, so
  `recipeId` must name a real crafting recipe (`minecraft:iron_ingot_from_iron_block`: one block in, nine ingots out).
  The pattern only fits through the **null-side** handler (raw 11 slots: 0..8 grid, 9 output, **10 pattern**); the six
  side handlers validate every insert against the pattern, which is why an unarmed assembler refuses everything.
- **Mekanism**: five `mekanism:enrichment_chamber` (redstone to enriched redstone), powered by a second Router with five
  Energy Cards. Mekanism keeps a machine's whole side configuration in a **data component on its BlockItem**, and
  `level.setBlock` never applies item components - a machine placed by code has every face set to NONE, so it exposes an
  inventory on the null side and no item handler on any of the six directions. `Showcase.applyItemDefaults` does what the
  player's hand does: `be.applyComponentsFromItemStack(new ItemStack(block))`, from inside `place()`, for every foreign
  block. With the config applied, the input faces are found by probing (`inFace` simulates an insert on all six) and the
  output face is the machine's RIGHT, as Mekanism's own default says.
- `Showcase.verify` checks both bays and prints, on failure, which faces the foreign machine offers and what is still
  sitting in the hopper, the Distributor and the chests - the line that turned both of these bugs from guesswork into
  five minutes of reading.

## 12. The 2026-09-11 round: what the note asked for (done)

- **CLUSTER is per machine, not per face** (`DistributorBlockEntity.dispatch`): a machine is every lane card bound to the
  same block, so Mekanism's Metallurgic Infuser - item on one face, infusion on another - can be fed a whole batch.
  `takesAll` now checks the batch against all of that machine's faces before anything is handed over.
- **Auto-bind** (`autoBind`, the button next to the mode toggle): bind one machine by hand, drop blank cards in the free
  lanes, press it, and every identical block within `BIND_RADIUS` gets the same set of lanes - same faces, same filter.
- **The return face skips its feeder**: the capability now hands out a per-face view (`view(side)`), the block remembers
  which face pushed a batch into it (`fedFrom`, saved), and `home()` never sends the product back there. The showcase no
  longer needs the wrench pin it used to need.
- **Stock Keeper card** (`CardKind.STOCK`): tops the bound inventory up to one stack of what the router is carrying, x2
  per overclock step, and stops when it is full. No new GUI: the amount is the card's own overclock.
- **Card to card copy**: `LogisticsCardItem.overrideOtherStackedOnMe` - right-click a card onto another in the inventory
  and the filter, flags, tags, components and mode come with it (never the binding). Filling a 64-reference card twice
  was the reason multi-cards felt unusable.
- **Fluid filtering**: `FilterSpec.testFluid` reads the fluid out of a reference container, so a water bucket in the
  ghost slot means water. The Fluid Card holds four references, fluid conduits have a card slot (`FluidPipeBlockEntity`
  implements `CardSlots`, and `PipeMenu` carries which card kind the slot takes), and the router's fluid card obeys it.
- **Chunk Loader Upgrade**: a router upgrade that keeps its own chunk and its bound targets ticking, through NeoForge's
  `TicketController` (`OmniLogistics.CHUNK_TICKETS`, registered on the mod bus so tickets survive a restart), capped by
  `router.loadedChunks` and released in `setRemoved`.
- **Cross-dimension cards** were always possible - `targetLevel` only applies the range inside one level - but silent and
  free. They now cost `router.crossDimensionFE` per action and Jade says how many cards are away.
- **Module flags** (`config/omnilogistics-common.toml`, `[modules]`): `transport` and `mining`. `core/ModuleCondition`
  is a recipe condition (`omnilogistics:module`) that `tools/gen_resources.py` stamps onto the Void Miner recipes, so a
  pack that turns mining off gets a mod with no miner recipes and no miner in the creative tab, instead of an
  uncraftable block in JEI. The flags read `true` while the config has not loaded yet - recipes are read early, and a
  half-built mod is worse than an extra recipe.
- **`com.mertokan.omnilogistics.api`**: `FilterSpec`, `FilterRef` and `ComponentPredicateEngine` moved out of `core`.
  That is the whole filter contract, and the only package an addon would ever import; everything in `core` is still
  free to change.
- **The monitor shows kinds, not slots**: contents are folded per item type, sorted by count, and a "+N more kinds" line
  says what did not fit on the glass.

## 13. The comprehensive cross-mod test (done, 2026-09-11)

`runClientPrepare` now judges **24 checks** in one headless run, and two of them are the whole point of this mod.

**Row 8 - a real ME auto-craft loop, with nobody clicking.** Spine, all touching so it is one ad-hoc grid:
`ae2:creative_energy_cell` - `ae2:1k_crafting_storage` (a complete CPU on its own) - `ae2:drive` - `ae2:chest` -
`ae2:interface` - `ae2:pattern_provider` - **our Batch Distributor**. Our own item pipe drips redstone into the ME
chest, so the network has the ingredient. Then:

1. the Interface is configured to stock 32 of the product and carries a **Crafting Card** - without that card
   `InterfaceLogic.handleCrafting` never asks the network to craft, and the bay just sits there (this cost a run);
2. the network has none, so the CPU schedules the encoded **processing** pattern in the provider;
3. the provider pushes the whole batch into our Distributor in one tick, which is exactly what CLUSTER mode expects;
4. the Distributor hands it to a free Mekanism Enrichment Chamber, our Router pulls the product out and pushes it into
   the **provider's face** - a pattern provider's item handler is its *return* inventory, which is how a machine tells an
   ME network the job is done (the pattern itself therefore has to be written into the block entity's own `patterns`
   NBT, not through the capability);
5. the product lands in network storage and the Interface's stock fills up. The check reads that stock.

Both AE2 NBT shapes are written by hand, so there is still no AE2 dependency: `ae2:encoded_processing_pattern` is
`{sparseInputs, sparseOutputs}` of GenericStacks (`{"#t": "ae2:i", "id": ..., "#": amount}`, `{}` = empty slot), and the
interface's `config` is the same GenericStack list.

**Row 9 - the machine hall.** One Batch Distributor in SPLIT mode with one filtered lane per machine drives four
machines from three mods at once - `mekanism:crusher`, `mekanism:precision_sawmill`, `actuallyadditions:crusher`,
`ae2:charger` - fed from a single mixed chest through a hopper, powered by one Router with Energy Cards, and a second
Router with four product-filtered EXTRACT cards brings everything back to one chest. Which face takes what is never
hard-coded: `inFace`/`feFace` simulate an insert on all six sides and ask the machine.

## 14. Auto input / auto output (done, 2026-09-11)

A card names a **block**, never a face. Which of its six faces - or its unsided view - actually takes or gives the item
is the mod's problem, not the player's and not the machine's.

`LogisticsCardItem.insertTarget / extractTarget / energyTarget / fluidTarget` try the face the card was bound to first
(the player pointed at it for a reason), then the other five, then the block's unsided handler, and return the first one
that can really do the job. The router uses them for items, FE, fluid, the Stock Keeper and the detector; the Batch
Distributor uses the same idea in both modes - in CLUSTER, `faces()` offers a machine's whole block, which is why a
Metallurgic Infuser (item on one face, infusion on another) needs no special setup at all.

Consequences worth knowing:

- Nobody configures Mekanism sides for us, and binding a card to the "wrong" face is no longer a mistake
  (`autoFaceFindsTheWorkingSide` binds to a furnace's output face and the ore still lands in the input slot).
- The showcase stopped probing faces entirely - every card in it binds to UP - so the 24-check world run *is* the test
  for this feature.
- Probe amounts matter: Mekanism converts FE to Joules and rounds 1 FE down to zero, so the energy probe asks for 1000
  first and only then for 1. A one-unit probe reported "this machine takes no power" on a machine that was perfectly
  happy to be charged, and cost a run.

## 15. Card tier upgrade by hand (done, 2026-09-12)

Nobody could find a recipe for the 4 / 16 / 64 reference cards in JEI, and the reason is structural: `CardUpgradeRecipe`
is a `CustomRecipe` (it has to be - it copies the card's filter, binding, mode and overclock onto the new card) and JEI
hides special recipes. Two halves fix that.

- **The ritual.** Card in one hand, the tier core in the other (gold -> 4, diamond -> 16, netherite -> 64), hold
  right-click for `LogisticsCardItem.UPGRADE_TICKS` (50). `use` only starts it when the other hand really holds a core
  that upgrades *this* card, so a plain right-click still opens the filter GUI. `getUseAnimation` is `BRUSH` (the scrubbing motion: it reads as rubbing the core into the card), `onUseTick` spirals enchant particles inward and chimes a rising amethyst note every 10 ticks, and
  `finishUsingItem` spends one core, returns the next card with every component carried over, plays a level-up sting and
  awards the `omnilogistics:card_upgrade` advancement - the "you can craft this now" moment.
- **JEI.** `OmniJeiPlugin.cardUpgrades()` hands JEI three plain shapeless stand-ins (card + core -> next card). They are
  never registered with the game, so they cannot clash with the real recipe; they exist to be looked at. An info page on
  each upgraded card explains the hand version and says the crafting recipe does the same thing and automates.

The crafting recipe itself is unchanged and works everywhere, including AE2 automation: hard-gating it would have meant
breaking every automated setup for the sake of a tutorial, so the gate is the advancement (and the toast), not the table.

## 16. The null side is not ours (2026-09-12)

A card bound to a Molecular Assembler was stuffing iron into the pattern slot and the crafting output. The cause was the
last line of the auto-face resolver: after trying all six faces it fell back to `getCapability(..., null)`, and for an
AE2 machine the unsided handler is the **raw internal inventory** - 11 slots, no filter, pattern slot and output slot
included. The self-test now prints the proof: every face of an assembler exposes 10 slots, the null side exposes 11.

The rule, in `LogisticsCardItem.face()` and `fluidTarget()`: **if the block exposes any per-side handler at all, the
unsided one is off limits.** Forcing a working face is the feature; reaching behind a face the machine deliberately
declared is not. The unsided handler stays as the fallback for blocks that expose nothing per-side, which is the case it
was always meant for. `sides(null)` no longer smuggles the null side in as "face zero" for an unbound card either.

Two permanent checks in the world verify guard it: the assembler pattern slot still holds a pattern, and the output slot
holds no raw ingredient, after 700 ticks of the router hammering the machine.

## 17. A card that says what it is bound to (2026-09-12)

Coordinates are not something anyone remembers a day later. Two answers, both in:

- **Words.** `CARD_TARGET_BLOCK` is a new component holding the bound block id, written by `LogisticsCardItem.bind()` -
  one function that every binder now goes through (sneak-click, the Distributor auto-bind, the showcase, the tests), so
  the name is always recorded. The tooltip leads with **Bound to: Molecular Assembler** and drops the coordinates to a
  gray second line. The id is stored rather than looked up because the chunk is usually unloaded and often a dimension
  away when someone reads the tooltip.
- **Pointing.** `CardLocator` (client only) binds **V** by default: hold the card, press it, and the block flashes red
  for six seconds through whatever is in front of it - `DEBUG_LINES` with the depth test off, alpha pulsing on a sine.
  The action bar gives the block name and the distance, or says which dimension it is in when it is not this one. No
  packet is involved: the binding already rides along in a synced component, so the client knows the answer.

## 18. Names the recipe earns (2026-09-12)

Basic / Advanced / Elite / Ultimate is Mekanism's ladder word for word, and Speed Upgrade is its item name. Ours is the
crafting core instead, which the recipes already used: every tier is the previous one plus gold, diamond or netherite.

| id | was | is |
|----|-----|-----|
| `basic_*` | Basic X | **X** - the plain tier carries no adjective, like the plain Logistics Card |
| `advanced_*` | Advanced X | **Gilded X** (gold core) |
| `elite_*` | Elite X | **Crystal X** (diamond core) |
| `ultimate_*` | Ultimate X | **Netherforged X** (netherite core) |
| `infinity_*` | Infinity X | **Omni X** - creative only, and the mod's own word |
| `speed_upgrade` | Speed Upgrade | **Bandwidth Upgrade** - it raises amount per cycle, never the clock |
| `range_upgrade` | Range Upgrade | **Antenna Upgrade** |
| `parallel_upgrade` | Parallel Upgrade | **Lane Upgrade** - the GUI already called them lanes |

The names now teach the recipe: card + gold is a Gilded card, and the hand ritual says so while it happens. Registry
ids are untouched on purpose - renaming them would empty every chest in a world that already has these blocks in it,
and the id only ever shows in JEI's advanced tooltip. Turkish follows the same idea: Yaldızlı / Kristal / Netherit.

## 19. The press kit (2026-09-12)

Everything a CurseForge page needs is generated, so a rename or a new feature never leaves the description lying.

- **The logo** (`tools/gen_logo.py` -> `src/main/resources/logo.png`, 512x512, also `logoFile` in the mods.toml) is not
  drawn twice: it is the mod's own block. `gen_logo.textures()` runs the texture generator with `Img.save` swapped for a
  collector, so every face `gen_textures.py` draws is available in memory, and `iso_block()` projects the Wireless
  Router's real top, panel and side textures into an isometric cube. Three conduits plug into it on the block's own
  axes - items down-left, fluid down-right, FE onto the back of the top face - each a stack of shaded slices so the band
  slopes with the projection. Two details matter: straight 2:1 isometric makes a cube exactly as wide as it is high,
  which reads as a squashed box, so the vertical axis (and the side textures with it) is stretched by 1.3; and the block
  is drawn two pixels per texel on a 320px canvas, so it fills about 40% of the icon and sits in its own space. Earlier attempts - a flat cabinet with lanes swapping order, and the card itself - were dropped.
- **The screenshots** come from a new run config, `runClientPress`: it builds the OmniTest world and verifies it exactly
  like `runClientPrepare`, then switches to spectator (a camera does not fall, and has no body), hides the HUD, flies a
  fixed `TOUR` table of camera stops around the finished, *running* world and writes `run/screenshots/omni_press_*.png`
  at 1920x1080. The last two stops give the card upgrade ritual in third and first person, HUD on.
  Two things cost an afternoon: the camera fell out of the sky because PREPARE never enabled flight (spectator fixed
  it), and the ritual kept cancelling itself one tick in - Minecraft releases a used item the moment the use key is not
  down, so the script now holds `keyUse` with `KeyMapping.set(...)`.
- **The page text** (`tools/gen_press.py` -> `docs/curseforge-en.md`, `docs/curseforge-tr.md`) writes the prose from a
  template and the item tables straight out of the lang files, screenshot captions included.
- Every sign in the showcase now speaks English, because the showcase is what the screenshots are of.

## 20. Both hands do the rubbing (2026-09-12)

`UseAnim.BRUSH` animates the hand that is using the item; the core in the other hand just sat there. `RenderHandEvent`
in `ClientSetup.ritualHand` now moves both: the card is walked in toward the middle of the screen and back off the lens
(vanilla still scrubs it), and the core is brought over to meet it with a counter-swinging sine, so the two actually
scrape against each other. Third person keeps the plain brush pose - posing both arms there needs a model hook that is
not worth a mixin.

## 21. NBT rules (2026-09-26)

The flags cover the one-click cases; an `NbtRule` covers everything else. A rule is a path into the item's data, an
operator and a value, and the item's data is every component on the stack - item defaults included, so "damage = 0"
holds on a fresh sword - serialized to one compound keyed by component id (`NbtRule.components`).

- **Path as a key list**, not a dotted string: component and registry ids are full of dots and colons. `[3]` indexes a
  list; `*` means any element, which is what makes "a shulker box with diamonds in any slot" expressible.
- **Operators**: = != > >= < <= has lacks contains. Numbers compare by value whatever their NBT width; a value that is
  not SNBT is taken as a plain string, so nobody has to type quotes. `contains` is substring on text, element of a list,
  key of a compound.
- **ALL / ANY** is a flag (`NBT_ANY`), and `MATCH_NBT` follows whether there are rules, like the tag and component
  pickers. No enabled rule lets everything through.
- **Registries**: holder-carrying components (enchantments) only serialize with the registries, so the engine uses
  the server's when there is one in the process; the GUI passes the client level's.
- **Storage**: `CARD_NBT` on cards (synced), `Nbt` in `FilterSpec.save` for blocks, and `FilterConfigPayload` now
  writes itself field by field because a seventh field is past what `StreamCodec.composite` takes.
- **GUI**: the bottom row of the filter panel is now Tags / Comps / NBT, lined up with the flag grid. The NBT overlay
  lists rules (on/off box, path, operator, value, delete); `+ Pick` lists every leaf of the reference item's data,
  the changed-from-default ones first, and a click makes it a rule. Long paths lose their beginning, not their end,
  because the end is the part that says what the value is.

The idea comes from LogisticsNetwork's NBT filter. That mod is All Rights Reserved, so nothing was taken from its code;
this is a separate implementation that goes further in two places (any-element paths, numeric-by-value equality).

## Build

Needs JDK 21 on PATH (none found on this machine; CurseForge only ships a JRE) and Gradle 8.8+:
```
gradle wrapper --gradle-version 8.10
.\gradlew test        # boots vanilla registries, runs ComponentPredicateEngineTest
.\gradlew runClient
```

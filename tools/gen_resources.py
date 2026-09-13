"""Generates blockstates, models, recipes, loot tables, tags, the JEI miner-loot display copy and the Patchouli guide. Run: python tools/gen_resources.py"""
import json
import os

RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources')
A = 'assets/omnilogistics'
D = 'data/omnilogistics'


def w(p, o):
    p = os.path.join(RES, p)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, 'w', encoding='utf-8') as f:
        json.dump(o, f, indent=1, ensure_ascii=False)


def ing(v):
    return {"tag": v[1:]} if v.startswith('#') else {"item": v}


# ---- conduits: 3 types x 4 tiers, multipart with a plain / pull / push arm per side ------
TYPES = {"item": "item_pipe", "energy": "energy_cable", "fluid": "fluid_pipe"}
TIERS = ["basic", "advanced", "elite", "ultimate", "infinity"]
TIER_CORE = {"advanced": "#c:ingots/gold", "elite": "#c:gems/diamond", "ultimate": "#c:ingots/netherite"}
rot = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}, "up": {"x": 270}, "down": {"x": 90}}
faces = ("north", "south", "east", "west", "up", "down")


def cube(f, t, uv_cap, uv_side):
    """Box with cap-style uv on the faces perpendicular to the arm axis and the side strip elsewhere.
    East / west rotate the same strip 90 degrees, so one strip in the texture serves all four sides."""
    flip = [uv_side[0], uv_side[3], uv_side[2], uv_side[1]]     # down / west mirror along the arm axis, as in tube()
    return {"from": f, "to": t, "faces": {
        "north": {"uv": uv_cap, "texture": "#pipe"}, "south": {"uv": uv_cap, "texture": "#pipe"},
        "up": {"uv": uv_side, "texture": "#pipe"}, "down": {"uv": flip, "texture": "#pipe"},
        "east": {"uv": uv_side, "texture": "#pipe", "rotation": 90},
        "west": {"uv": flip, "texture": "#pipe", "rotation": 90}}}


def rotated(el, axis, origin):
    el = dict(el)
    el["rotation"] = {"origin": origin, "axis": axis, "angle": 45, "rescale": False}
    return el


CAP = [5, 6, 11, 12]     # knot face, texture px 10..21 x 12..23
STRIP = [5, 0, 11, 6]    # continuous glass run, px 10..21 x 0..11: uniform along v, mirror-symmetric in u (rotations flip it)
TRANSLUCENT = "minecraft:translucent"   # the tube windows have alpha
CONN = "plain|pull|push"
AXIS = {"x": ("east", "west"), "y": ("up", "down"), "z": ("north", "south")}
# centre cube faces are separate single-face models rotated by the blockstate (base = north face):
# strip_v runs along y, strip_h along x. A face shows the strip when exactly the two opposite sides in its plane are connected,
# so a straight run is one uninterrupted glass tube; bends, branches and ends get the cap.
STRIP_ROT = {("up", "z"): ("strip_v", 270, 0), ("up", "x"): ("strip_v", 270, 90),
             ("down", "z"): ("strip_v", 90, 0), ("down", "x"): ("strip_v", 90, 90),
             ("north", "y"): ("strip_v", 0, 0), ("east", "y"): ("strip_v", 0, 90), ("south", "y"): ("strip_v", 0, 180), ("west", "y"): ("strip_v", 0, 270),
             ("north", "x"): ("strip_h", 0, 0), ("east", "z"): ("strip_h", 0, 90), ("south", "x"): ("strip_h", 0, 180), ("west", "z"): ("strip_h", 0, 270)}
CAP_ROT = {"north": (0, 0), "east": (0, 90), "south": (0, 180), "west": (0, 270), "down": (90, 0), "up": (270, 0)}


def tube(z0, z1, v1=5, caps=()):
    """Open-ended square glass tube along z: no end faces, so nothing shows through the glass where pieces meet.
    down / west take the v-swapped rect: their tangent frames run -z where up / east run +z (Minecraft's FaceInfo), so one uv
    on all four faces makes the strip - and the IN / OUT arrow on it - run backwards along half of every arm. A v-swap mirrors
    only v; rotation 180 / 270 would fix v but mirror u too, flipping the barrel shading on those two faces."""
    uv, flip = [5, 0, 11, v1], [5, v1, 11, 0]
    f = {"up": {"uv": uv, "texture": "#pipe"}, "down": {"uv": flip, "texture": "#pipe"},
         "east": {"uv": uv, "texture": "#pipe", "rotation": 90}, "west": {"uv": flip, "texture": "#pipe", "rotation": 90}}
    for c in caps:
        f[c] = {"uv": CAP, "texture": "#pipe"}
    return {"from": [5, 5, z0], "to": [11, 11, z1], "faces": f}


def knot_face(uv, rotation=0):
    """The centre cube with only its north face."""
    f = {"uv": uv, "texture": "#pipe"}
    if rotation:
        f["rotation"] = rotation
    return {"from": [5, 5, 5], "to": [11, 11, 11], "faces": {"north": f}}


def apply(model, x, y):
    a = {"model": model}
    if x:
        a["x"] = x
    if y:
        a["y"] = y
    return a


def centre_parts(name):
    parts = []
    for f in faces:
        a, b = [ax for ax in "xyz" if f not in AXIS[ax]]
        for run, other in ((a, b), (b, a)):
            kind, x, y = STRIP_ROT[(f, run)]
            when = {f: "none", AXIS[run][0]: CONN, AXIS[run][1]: CONN, AXIS[other][0]: "none", AXIS[other][1]: "none"}
            parts.append({"when": when, "apply": apply(f"omnilogistics:block/{name}_{kind}", x, y)})
        p, q = AXIS[a]
        r, s = AXIS[b]
        cap = [{f: "none", p: CONN, q: "none"}, {f: "none", p: "none", q: CONN}, {f: "none", r: CONN, s: "none"}, {f: "none", r: "none", s: CONN},
               {f: "none", p: "none", q: "none", r: "none", s: "none"}, {f: "none", p: CONN, q: CONN, r: CONN, s: CONN}]
        x, y = CAP_ROT[f]
        parts.append({"when": {"OR": cap}, "apply": apply(f"omnilogistics:block/{name}_cap", x, y)})
    return parts


def plate_els():
    """Connector plate: face uv 4..12 square, rim uv = the 8x2 strip at the bottom of the texture (px 8..23 x 28..31 at 32px).
    The flange sits 0.4 off the block boundary so its face is not coplanar with the neighbour block's own face, and the
    45-degree twin it used to carry is gone: that twin was inscribed in this box, so all of its faces were either buried or
    exactly coplanar here - six wasted quads that only z-fought a scaled copy of the plate art at every IN / OUT end."""
    face = [4, 4, 12, 12]
    side = [4, 14, 12, 16]
    return [cube([4, 4, 0.4], [12, 12, 2], face, side)]


PARENTS = {"arm": [tube(0, 5)], "arm_end": [tube(2, 5)] + plate_els(), "cap": [knot_face(CAP)],
           "strip_v": [knot_face(STRIP)], "strip_h": [knot_face(STRIP, 90)]}
for p, els in PARENTS.items():
    w(f'{A}/models/block/pipe_{p}.json', {"render_type": TRANSLUCENT, "textures": {"pipe": "omnilogistics:block/pipe_item_basic", "particle": "#pipe"}, "elements": els})
w(f'{A}/atlases/blocks.json', {"sources": [{"type": "single", "resource": "omnilogistics:block/glow"}]})
for t, tid in TYPES.items():
    for k, tier in enumerate(TIERS):
        name = f"{tier}_{tid}"
        tex = f"omnilogistics:block/pipe_{t}_{tier}"
        # render_type is repeated on every child: it is not inherited from the parent model
        for kind in ("cap", "strip_v", "strip_h"):
            w(f'{A}/models/block/{name}_{kind}.json', {"parent": f"omnilogistics:block/pipe_{kind}", "render_type": TRANSLUCENT, "textures": {"pipe": tex, "particle": tex}})
        for suffix in ("", "_pull", "_push"):
            parent = "omnilogistics:block/pipe_arm" if suffix == "" else "omnilogistics:block/pipe_arm_end"
            w(f'{A}/models/block/{name}_arm{suffix}.json', {"parent": parent, "render_type": TRANSLUCENT, "textures": {"pipe": tex + suffix, "particle": tex}})
        parts = centre_parts(name)
        for side, r in rot.items():
            for conn, suffix in (("plain", ""), ("pull", "_pull"), ("push", "_push")):
                parts.append({"when": {side: conn}, "apply": dict({"model": f"omnilogistics:block/{name}_arm{suffix}"}, **r)})
        w(f'{A}/blockstates/{name}.json', {"multipart": parts})
        w(f'{A}/models/item/{name}.json', {"parent": "minecraft:block/block", "render_type": TRANSLUCENT, "textures": {"pipe": tex, "particle": tex},
                                           "elements": [tube(0, 5, caps=("north",)), tube(5, 11, 6), tube(11, 16, caps=("south",))]})
        if tier == "basic":
            pattern, key = {
                "item": (["ICI", "GRG", "ICI"], {"I": "#c:ingots/iron", "C": "#c:ingots/copper", "G": "#c:glass_blocks", "R": "#c:dusts/redstone"}),
                "energy": (["CRC", "RIR", "CRC"], {"C": "#c:ingots/copper", "R": "#c:dusts/redstone", "I": "#c:ingots/iron"}),
                "fluid": (["CGC", "G G", "CGC"], {"C": "#c:ingots/copper", "G": "#c:glass_blocks"}),
            }[t]
        elif tier in TIER_CORE:
            pattern, key = ["PPP", "PXP", "PPP"], {"P": f"omnilogistics:{TIERS[k - 1]}_{tid}", "X": TIER_CORE[tier]}
        else:
            pattern = None  # infinity: creative only
        if pattern:
            w(f'{D}/recipe/{name}.json', {"type": "minecraft:crafting_shaped", "pattern": pattern,
                                          "key": {kk: ing(v) for kk, v in key.items()},
                                          "result": {"id": f"omnilogistics:{name}", "count": 8}})
        w(f'{D}/loot_table/blocks/{name}.json', {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [
            {"type": "minecraft:item", "name": f"omnilogistics:{name}"}], "conditions": [{"condition": "minecraft:survives_explosion"}]}]})

# ---- machines -----------------------------------------------------------------------
MINER_TIERS = ("basic", "advanced", "elite", "ultimate")
MACHINES = [("inventory_exposer", "exposer", "exposer"), ("wireless_router", "router", "router"), ("component_extractor", "extractor", "extractor"),
            ("batch_distributor", "distributor", "distributor")] \
    + [(f"{t}_void_miner", f"miner_{t}", f"miner_{t}") for t in MINER_TIERS]
def panel_el(axis_face):
    d = 0.5
    boxes = {"north": ([3, 3, -d], [13, 13, 0]), "south": ([3, 3, 16], [13, 13, 16 + d]),
             "west": ([-d, 3, 3], [0, 13, 13]), "east": ([16, 3, 3], [16 + d, 13, 13])}
    f, t = boxes[axis_face]
    fs = {axis_face: {"uv": [0, 0, 16, 16], "texture": "#panel"}}
    return {"from": f, "to": t, "faces": fs}


for name, t, kind in MACHINES:
    w(f'{A}/blockstates/{name}.json', {"variants": {"": {"model": f"omnilogistics:block/{name}"}}})
    w(f'{A}/models/block/{name}.json', {"parent": "minecraft:block/block", "textures": {
        "top": f"omnilogistics:block/{t}_top", "side": f"omnilogistics:block/{t}_side",
        "panel": f"omnilogistics:block/{kind}_panel", "particle": f"omnilogistics:block/{t}_side"},
        "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": {
            "north": {"uv": [0, 0, 16, 16], "texture": "#side"}, "south": {"uv": [0, 0, 16, 16], "texture": "#side"},
            "east": {"uv": [0, 0, 16, 16], "texture": "#side"}, "west": {"uv": [0, 0, 16, 16], "texture": "#side"},
            "up": {"uv": [0, 0, 16, 16], "texture": "#top"}, "down": {"uv": [0, 0, 16, 16], "texture": "#top"}}}]
        + [panel_el(f) for f in ("north", "south", "west", "east")]})
    w(f'{A}/models/item/{name}.json', {"parent": f"omnilogistics:block/{name}"})
    w(f'{D}/loot_table/blocks/{name}.json', {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [
        {"type": "minecraft:item", "name": f"omnilogistics:{name}"}], "conditions": [{"condition": "minecraft:survives_explosion"}]}]})

# ---- void miner loot: what each tier pulls out of the void (weights), datapack-overridable ---------------------------------
MINER_LOOT = {
    "basic": [("cobblestone", 20), ("coal_ore", 24), ("iron_ore", 18), ("copper_ore", 18), ("redstone_ore", 8), ("lapis_ore", 5), ("gold_ore", 5), ("gravel", 2)],
    "advanced": [("cobblestone", 8), ("coal_ore", 18), ("iron_ore", 18), ("copper_ore", 14), ("redstone_ore", 10), ("lapis_ore", 7), ("gold_ore", 8),
                 ("nether_quartz_ore", 8), ("nether_gold_ore", 4), ("diamond_ore", 3), ("emerald_ore", 2)],
    "elite": [("coal_ore", 12), ("iron_ore", 16), ("copper_ore", 10), ("redstone_ore", 12), ("lapis_ore", 8), ("gold_ore", 10), ("nether_quartz_ore", 10),
              ("nether_gold_ore", 5), ("diamond_ore", 6), ("emerald_ore", 4), ("amethyst_shard", 4), ("glowstone_dust", 3), ("ancient_debris", 1)],
    "ultimate": [("iron_ore", 12), ("gold_ore", 12), ("redstone_ore", 10), ("lapis_ore", 8), ("nether_quartz_ore", 8), ("diamond_ore", 12),
                 ("deepslate_diamond_ore", 6), ("emerald_ore", 8), ("ancient_debris", 4), ("amethyst_shard", 5), ("glowstone_dust", 4), ("ender_pearl", 3)],
}
for tier, entries in MINER_LOOT.items():
    w(f'{D}/loot_table/miner/{tier}.json', {"type": "minecraft:empty", "pools": [{"rolls": 1, "entries": [
        {"type": "minecraft:item", "name": f"minecraft:{n}", "weight": wt} for n, wt in entries]}]})
# ponytail: assets copy of the same weights so JEI can show them client-side - loot tables live in the server's
# reloadable registries and are never synced. One entry per item id; if a tier ever repeats an id, switch to a list of pairs.
assert all(sum(wt for _, wt in e) > 0 for e in MINER_LOOT.values()), "MINER_LOOT tier with no weight"
w(f'{A}/miner_loot.json', {t: {f"minecraft:{n}": wt for n, wt in e} for t, e in MINER_LOOT.items()})


# ---- machine monitor: a screen on one face, so it needs its own facing blockstate ------
MON = f'{A}/models/block/machine_monitor'
w(f'{MON}.json', {"parent": "minecraft:block/orientable", "textures": {
    "front": "omnilogistics:block/monitor_front", "side": "omnilogistics:block/monitor_side",
    "top": "omnilogistics:block/monitor_top", "particle": "omnilogistics:block/monitor_side"}})
w(f'{A}/blockstates/machine_monitor.json', {"variants": {
    "facing=north": {"model": "omnilogistics:block/machine_monitor"},
    "facing=east": {"model": "omnilogistics:block/machine_monitor", "y": 90},
    "facing=south": {"model": "omnilogistics:block/machine_monitor", "y": 180},
    "facing=west": {"model": "omnilogistics:block/machine_monitor", "y": 270}}})
w(f'{A}/models/item/machine_monitor.json', {"parent": "omnilogistics:block/machine_monitor"})
w(f'{D}/loot_table/blocks/machine_monitor.json', {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [
    {"type": "minecraft:item", "name": "omnilogistics:machine_monitor"}], "conditions": [{"condition": "minecraft:survives_explosion"}]}]})

# ---- the "you have learned the card upgrade" advancement, awarded in code by the hand ritual ----------
w(f'{D}/advancement/card_upgrade.json', {
    "display": {
        "icon": {"id": "omnilogistics:advanced_logistics_card"},
        "title": {"translate": "advancement.omnilogistics.card_upgrade"},
        "description": {"translate": "advancement.omnilogistics.card_upgrade.desc"},
        "background": "minecraft:textures/block/polished_deepslate.png",
        "frame": "goal", "show_toast": True, "announce_to_chat": True, "hidden": False},
    "criteria": {"code": {"trigger": "minecraft:impossible"}},
    "requirements": [["code"]]})

# ---- items ---------------------------------------------------------------------------
def gen(t):
    return {"parent": "minecraft:item/generated", "textures": {"layer0": f"omnilogistics:item/{t}"}}


w(f'{A}/models/item/wrench.json', gen("wrench"))
for t in ("speed_upgrade", "parallel_upgrade", "range_upgrade", "gem_module", "fusion_module"):
    w(f'{A}/models/item/{t}.json', gen(t))
CARD_KINDS = {"advanced_logistics_card": ("card_x4", True), "elite_logistics_card": ("card_x16", True),
              "ultimate_logistics_card": ("card_x64", True),
              "energy_card": ("card_energy", True), "fluid_card": ("card_fluid", True), "void_card": ("card_void", False),
              "vacuum_card": ("card_vacuum", False), "activator_card": ("card_activator", False), "breaker_card": ("card_breaker", False),
              "placer_card": ("card_placer", False), "detector_card": ("card_detector", False),
              "stock_card": ("card_stock", False)}
for item, (base, has_mode) in CARD_KINDS.items():
    w(f'{A}/models/item/{base}_extract_bound.json', gen(base + "_extract_bound"))
    overrides = [{"predicate": {"omnilogistics:bound": 1}, "model": f"omnilogistics:item/{base}_extract_bound"}]
    if has_mode:
        w(f'{A}/models/item/{base}_insert.json', gen(base + "_insert"))
        w(f'{A}/models/item/{base}_insert_bound.json', gen(base + "_insert_bound"))
        overrides += [{"predicate": {"omnilogistics:mode": 1}, "model": f"omnilogistics:item/{base}_insert"},
                      {"predicate": {"omnilogistics:mode": 1, "omnilogistics:bound": 1}, "model": f"omnilogistics:item/{base}_insert_bound"}]
    w(f'{A}/models/item/{item}.json', dict(gen(base + "_extract"), overrides=overrides))
for t in ("card_extract_bound", "card_insert", "card_insert_bound"):
    w(f'{A}/models/item/{t}.json', gen(t))
w(f'{A}/models/item/logistics_card.json', dict(gen("card_extract"), overrides=[
    {"predicate": {"omnilogistics:bound": 1}, "model": "omnilogistics:item/card_extract_bound"},
    {"predicate": {"omnilogistics:mode": 1}, "model": "omnilogistics:item/card_insert"},
    {"predicate": {"omnilogistics:mode": 1, "omnilogistics:bound": 1}, "model": "omnilogistics:item/card_insert_bound"}]))

# ---- loot tables ---------------------------------------------------------------------


# ---- recipes -------------------------------------------------------------------------
def shaped(name, pattern, key, count=1, module=None):
    r = {"type": "minecraft:crafting_shaped", "pattern": pattern,
         "key": {k: ing(v) for k, v in key.items()},
         "result": {"id": f"omnilogistics:{name}", "count": count}}
    if module:                       # a module the pack can switch off takes its recipes with it
        r["neoforge:conditions"] = [{"type": "omnilogistics:module", "module": module}]
    w(f'{D}/recipe/{name}.json', r)


shaped("inventory_exposer", ["IEI", "CHC", "IRI"], {"I": "#c:ingots/iron", "E": "minecraft:ender_pearl", "C": "#c:ingots/copper", "H": "minecraft:hopper", "R": "minecraft:redstone"})
shaped("wireless_router", ["GEG", "CHC", "IRI"], {"G": "#c:ingots/gold", "E": "minecraft:ender_eye", "C": "#c:ingots/copper", "H": "minecraft:hopper", "I": "#c:ingots/iron", "R": "minecraft:redstone"})
shaped("component_extractor", ["IBI", "DGD", "IRI"], {"I": "#c:ingots/iron", "B": "minecraft:book", "D": "minecraft:diamond", "G": "minecraft:grindstone", "R": "minecraft:redstone_block"})
shaped("batch_distributor", ["IHI", "CRC", "IGI"], {"I": "#c:ingots/iron", "H": "minecraft:hopper", "C": "#c:ingots/copper",
                                                     "R": "omnilogistics:logistics_card", "G": "#c:ingots/gold"})
shaped("chunk_upgrade", ["EGE", "GDG", "EGE"], {"E": "#c:ender_pearls", "G": "#c:ingots/gold", "D": "#c:gems/diamond"})
shaped("machine_monitor", ["IGI", "GCG", "IRI"], {"I": "#c:ingots/iron", "G": "#c:glass_blocks",
                                                   "C": "omnilogistics:logistics_card", "R": "#c:dusts/redstone"})
shaped("wrench", ["I I", " I ", " I "], {"I": "#c:ingots/iron"})
shaped("speed_upgrade", ["RGR", "GSG", "RGR"], {"R": "#c:dusts/redstone", "G": "#c:ingots/gold", "S": "minecraft:sugar"})
shaped("parallel_upgrade", ["IHI", "RCR", "IHI"], {"I": "#c:ingots/iron", "H": "minecraft:hopper", "R": "#c:dusts/redstone", "C": "#c:ingots/copper"})
shaped("range_upgrade", ["GEG", "EAE", "GEG"], {"G": "#c:ingots/gold", "E": "#c:ender_pearls", "A": "minecraft:amethyst_shard"})
shaped("gem_module", ["DED", "EBE", "DED"], {"D": "#c:gems/diamond", "E": "#c:gems/emerald", "B": "minecraft:book"})
shaped("fusion_module", ["DAD", "ABA", "DAD"], {"D": "#c:gems/diamond", "A": "minecraft:anvil", "B": "minecraft:enchanted_book"})
shaped("basic_void_miner", ["IEI", "DOD", "IRI"], {"I": "#c:ingots/iron", "E": "#c:ender_pearls", "D": "#c:gems/diamond", "O": "minecraft:obsidian", "R": "#c:storage_blocks/redstone"}, module="mining")
for prev, tier in zip(MINER_TIERS, MINER_TIERS[1:]):   # previous miner in the middle, tier core in the corners, ender eyes between
    shaped(f"{tier}_void_miner", ["CEC", "EME", "CEC"], {"C": TIER_CORE[tier], "E": "minecraft:ender_eye", "M": f"omnilogistics:{prev}_void_miner"}, module="mining")
for item, extra in (("energy_card", "minecraft:redstone_block"), ("fluid_card", "minecraft:bucket"), ("void_card", "minecraft:lava_bucket"),
                    ("vacuum_card", "minecraft:hopper"), ("activator_card", "minecraft:dispenser"), ("breaker_card", "minecraft:iron_pickaxe"),
                    ("placer_card", "minecraft:piston"), ("detector_card", "minecraft:comparator"),
                    ("stock_card", "minecraft:barrel")):
    w(f'{D}/recipe/{item}.json', {"type": "minecraft:crafting_shapeless",
                                  "ingredients": [ing("omnilogistics:logistics_card"), ing(extra)],
                                  "result": {"id": f"omnilogistics:{item}", "count": 1}})
w(f'{D}/recipe/card_upgrade.json', {"type": "omnilogistics:card_upgrade"})
w(f'{D}/recipe/logistics_card.json', {"type": "minecraft:crafting_shapeless",  # cards stack to 1, so 1 per craft
                                       "ingredients": [ing("minecraft:paper"), ing("minecraft:paper"), ing("minecraft:redstone")],
                                       "result": {"id": "omnilogistics:logistics_card", "count": 1}})
w('data/c/tags/item/tools/wrench.json', {"replace": False, "values": ["omnilogistics:wrench"]})

# ---- Patchouli guide (optional dependency) --------------------------------------------
w(f'{D}/patchouli_books/guide/book.json', {
    "name": "book.omnilogistics.name", "landing_text": "book.omnilogistics.landing", "use_resource_pack": True,
    "creative_tab": "omnilogistics:main", "show_progress": False, "version": 1,
    "model": "patchouli:book_blue", "book_texture": "patchouli:textures/gui/book_blue.png"})
# Patchouli 1.21 has no book recipe serializer any more: a vanilla recipe whose result is the guide book item carrying the book id component
w(f'{D}/recipe/guide.json', {"type": "minecraft:crafting_shapeless", "ingredients": [ing("minecraft:book"), ing("#c:ingots/copper")],
                             "result": {"id": "patchouli:guide_book", "count": 1, "components": {"patchouli:book": "omnilogistics:guide"}},
                             "neoforge:conditions": [{"type": "neoforge:mod_loaded", "modid": "patchouli"}]})
B = f'{A}/patchouli_books/guide/en_us'
w(f'{B}/categories/blocks.json', {"name": "Blocks and Items", "description": "Everything OmniLogistics adds, one entry each.",
                                  "icon": "omnilogistics:basic_item_pipe", "sortnum": 0})


def text(t):
    return {"type": "patchouli:text", "text": t}


def craft(r):
    return {"type": "patchouli:crafting", "recipe": r}


def entry(name, title, icon, n, pages):
    w(f'{B}/entries/blocks/{name}.json', {"name": title, "icon": icon, "category": "omnilogistics:blocks", "sortnum": n, "pages": pages})


entry("filter", "The Component Filter", "minecraft:enchanted_book", 0, [
    text("Every block and card in this mod shares one filter: a $(bold)reference item$() plus $(bold)flags$(). Click the ghost slot with an item to set the reference (it is not consumed); shift-click an item in your inventory does the same. Click the slot empty-handed to clear it."),
    text("$(bold)Match Item$(): same item as the reference.$(br)$(bold)Match Comps$(): components must be identical.$(br)$(bold)Match Enchants$(): the item must carry every enchantment of the reference at that level or higher. Put an enchanted book in the slot and turn Match Item off to catch any gear with those enchantments."),
    text("$(bold)Has Enchants$(): any enchanted item, no reference needed.$(br)$(bold)Mod Comps$(): items carrying components from other mods, e.g. Apotheosis affixes or gems. No reference needed.$(br)$(bold)Ignore Dmg$(): durability is ignored when comparing.$(br)$(bold)Blacklist$(): let through everything that does NOT match.")])
entry("pipe", "Pipes and Cables", "omnilogistics:basic_item_pipe", 1, [
    text("Three conduit types (items, FE energy, fluids) in four tiers: plain, Gilded, Crystal and Netherforged. Every face is labelled from the $(bold)neighbour block's$() side: $(bold)NORM$() (connected: accepts input and receives deliveries), $(bold)EXTRACT$() (the conduit takes items out of that neighbour, green arm), $(bold)INSERT$() (the conduit only delivers into it, orange arm) or $(bold)OFF$() (disconnected, no arm)."),
    text("Set EXTRACT on the source side and everything flows to every other connected inventory, through as many conduits as you like. Conduits only connect to conduits of the same type. Right-click a conduit at the $(bold)end of a run$() (touching an inventory or machine) to set its faces and redstone mode; conduits in the middle are plain cables. Item pipes have a $(bold)card slot$(): drop in a Logistics Card and only items passing its filter travel, right-click the card in the slot to edit it. No card = everything passes. Tier: 8 / 32 / 128 / 512 items per move, 2k / 8k / 32k / 128k FE per tick, 1 / 4 / 16 / 64 buckets per move. Infinity tier is creative-only and effectively unlimited."),
    text("$(bold)Redstone$(): wrench the conduit centre (not an arm) to cycle Always / Only with signal / Only without signal for that conduit, or use the button in the end GUI. A gated conduit neither pulls nor delivers. Everything numeric (FE cost, ranges, rates) lives in config/omnilogistics-common.toml."),
    craft("omnilogistics:basic_item_pipe"), craft("omnilogistics:basic_energy_cable"), craft("omnilogistics:basic_fluid_pipe"), craft("omnilogistics:advanced_item_pipe")])
entry("exposer", "Inventory Exposer", "omnilogistics:inventory_exposer", 2, [
    text("Pick one face as the $(bold)target$() (GUI or wrench). The inventory on that side is shown on all other faces as a filtered view: AE2 storage buses, RS external storage, hoppers and pipes only ever see items that pass the filter."),
    text("Default filter is Mod Comps, so an exposer on a chest full of loot shows only the Apotheosis gear. Change the filter to expose anything else. Items that do not match are invisible and cannot be pulled through the exposer."),
    craft("omnilogistics:inventory_exposer")])
entry("router", "Wireless Router and Cards", "omnilogistics:wireless_router", 3, [
    text("No cables. Hold a card, sneak and right-click a block face to bind it. Cards with a filter open their GUI on right-click in the air; Energy and Fluid cards flip EXTRACT / INSERT instead. Icons change with mode and the LED turns green once bound."),
    text("$(bold)Logistics Card$(): items in / out. $(bold)Energy Card$(): FE in / out. $(bold)Fluid Card$(): fluids in / out of the router tank. $(bold)Void Card$(): destroys matching items that reach the buffer. $(bold)Vacuum Card$(): sucks matching drops around the router (or the bound block) into the buffer."),
    text("$(bold)Activator Card$(): right-clicks the bound face with the buffer item, like a player. $(bold)Breaker Card$(): breaks the bound block, drops go to the buffer. $(bold)Placer Card$(): places the buffer item at the bound spot. $(bold)Detector Card$(): the router emits redstone 15 while the bound inventory holds a matching item."),
    text("Right-click a card $(bold)inside the router$() to configure it in place: filter cards open their GUI, Energy and Fluid cards flip EXTRACT / INSERT. The $(bold)tick field$() next to the buffer sets how often the router acts: type anything from 1 to 200 ticks, no upgrade unlocks it."),
    text("Up to 8 cards act in slot order, round-robin. Two upgrade slots: each $(bold)Bandwidth Upgrade$() doubles how many card actions one cycle gets, each $(bold)Antenna Upgrade$() adds the base range again. Overclock a single card by crafting it with Bandwidth Upgrades (up to 3): every step doubles what it moves per action."),
    craft("omnilogistics:wireless_router"), craft("omnilogistics:logistics_card"), craft("omnilogistics:energy_card"), craft("omnilogistics:fluid_card"),
    craft("omnilogistics:void_card"), craft("omnilogistics:vacuum_card"), craft("omnilogistics:activator_card"), craft("omnilogistics:breaker_card"),
    craft("omnilogistics:placer_card"), craft("omnilogistics:detector_card")])
entry("extractor", "Component Extractor", "omnilogistics:component_extractor", 4, [
    text("The base machine moves $(bold)enchantments onto books$(): gear in a lane, books (or bookshelves, worth 3) in the middle slot, 4000 FE per operation, 100 ticks unless you type another number. The gear stays in its lane and is cleaned in place; finished gear and outputs auto-eject into adjacent inventories."),
    text("The $(bold)tick field$() under the lanes sets how long one operation takes: any value from 1 to 200 ticks, nothing to unlock first. Faster means more FE per second, not per operation."),
    text("Four upgrade slots. $(bold)Component Module$(): also pulls items embedded in modded components out of gear (Apotheosis gems and the like). $(bold)Fusion Module$(): unlocks FUSE mode, the button in the GUI: the donor slot merges onto the gear, enchantments with anvil rules and every modded component the gear lacks."),
    text("$(bold)Bandwidth Upgrade$() (up to 3): halves the FE per operation each, 4000 / 2000 / 1000 / 500 FE. $(bold)Lane Upgrade$() (up to 2): unlocks the second and third lane so three items process at once, each paying its own FE."),
    craft("omnilogistics:component_extractor"), craft("omnilogistics:gem_module"), craft("omnilogistics:fusion_module")])
entry("distributor", "Batch Distributor", "omnilogistics:batch_distributor", 7, [
    text("Automating a craft whose ingredients go to $(bold)several different places$() normally means one Pattern Provider per input and a sub-network. Put a Batch Distributor against a single Pattern Provider instead: six lanes, one Logistics Card per lane, and each card is bound (sneak + right-click) to the machine face it feeds."),
    text("AE2 pushes one ordinary pattern into the block and walks the lanes in order, so $(bold)each ingredient lands in the first lane whose filter matches it$() and that lane delivers it to its own machine on the next tick. Give every lane a different filter: a lane with no filter accepts nothing (that is what stops lane 1 eating the batch), and a lane with only $(bold)Blacklist$() set is the deliberate catch-all."),
    text("$(bold)You do not have to fill the filters in.$() A bound card with no filter is a $(bold)learner$(): the first ingredient that reaches it becomes its filter, and an ingredient another lane already asks for is never stolen. So the setup is: drop the cards in, bind each one to its machine, run the craft once. An $(bold)EXTRACT$() lane with no filter needs nothing at all - it takes whatever that machine holds that is not an ingredient delivered to it, which is the product."),
    text("$(bold)CLUSTER$() mode (the button in the GUI) does the other half of the problem: instead of splitting one batch, it hands the WHOLE batch to one machine and the next batch to the next machine. Bind a lane to each of five identical machines, give the block one Pattern Provider, and the craft runs five times faster - no sub-network, no five providers. A machine that is still busy simply refuses the batch, so it goes to a free one. $(bold)Lane Upgrades$() add 3 lanes each, up to 12."),
    text("Set the provider's push direction to the Distributor, or a chest touching it will steal batches. Switch a card to $(bold)EXTRACT$() to turn its lane around: it pulls the finished product out of that machine and pushes it into the return face (wrench a face to pin it, otherwise the first neighbour no lane targets), i.e. straight back into ME. The amber pip means two lanes share a filter, the only case where items go to the wrong machine."),
    craft("omnilogistics:batch_distributor")])
entry("multi_card", "Bigger Filter Cards", "omnilogistics:elite_logistics_card", 8, [
    text("The plain Logistics Card whitelists $(bold)one$() item. The Gilded, Crystal and Netherforged cards are the same card with $(bold)4, 16 and 64$() reference slots: a stack passes if it matches $(bold)any$() of them, and the flags (Match Comps, Has Enchants, Blacklist...) still apply on top."),
    text("Upgrade in the crafting grid: card + $(bold)gold ingot$() -> Gilded, + $(bold)diamond$() -> Crystal, + $(bold)netherite ingot$() -> Netherforged. The card keeps its filter, its binding, its mode and its speed, so you can grow a card you already set up. They work everywhere a Logistics Card works: pipes, the router, the Batch Distributor.")])
entry("upgrades", "Upgrades", "omnilogistics:speed_upgrade", 9, [
    text("Bandwidth, Lane and Antenna upgrades stack in their slot (3 / 2 / 3). Modules are single. Extractor: Bandwidth, Lane, Component Module, Fusion Module. Router: Bandwidth, Antenna. $(bold)Bandwidth Upgrades no longer change timing$() - every machine runs at the tick rate you type into its GUI. They raise throughput instead: x2 card actions per cycle in the router, x2 items and FE per cycle in the miner, half the FE per operation in the extractor."),
    text("$(bold)Chunk Loader Upgrade$() (router, 1): keeps the router's own chunk and the chunks its cards point at ticking while you are away - up to 9 chunks, config $(italic)router.loadedChunks$()."),
    craft("omnilogistics:speed_upgrade"), craft("omnilogistics:parallel_upgrade"), craft("omnilogistics:range_upgrade"),
    craft("omnilogistics:chunk_upgrade")])
entry("stock_card", "Stock Keeper", "omnilogistics:stock_card", 11, [
    text("$(bold)Stock Keeper Card$(): keeps the bound inventory topped up to one stack of whatever the router is carrying, and stops when it is full. Craft it with Bandwidth Upgrades to keep 2 / 4 / 8 stacks."),
    text("It never fetches anything by itself - pair it with an item card in $(bold)EXTRACT$() mode on a chest. That one fills the router buffer, this one keeps the machine stocked."),
    craft("omnilogistics:stock_card")])
entry("fluid_filter", "Fluid filters", "omnilogistics:fluid_card", 12, [
    text("A fluid filter names its fluid with a $(bold)container$(): drop a water bucket into a reference slot and only water passes. The Fluid Card holds four references, and a tag entry can name a whole fluid tag."),
    text("Every fluid conduit has the same card slot, so a pipe can be told to carry one fluid only - right-click the conduit at the end of a run, drop the card in, right-click the card to edit it. The router's Fluid Card obeys the same filter."),
    text("Cards copy: hold one card and $(bold)right-click it onto another$() in your inventory, and the whole filter - references, flags, tags, components, mode - is copied over. The binding is not.")])
entry("wrench", "Logistics Wrench", "omnilogistics:wrench", 10, [
    text("Right-click a conduit arm: cycle NORM / EXTRACT / INSERT / OFF. Right-click the conduit centre: cycle the redstone mode (or turn an OFF face facing you back on). Right-click an exposer face: set (or clear) the target. $(bold)Sneak + right-click$() any OmniLogistics block: pick it up, block and contents go straight into your inventory. It is tagged c:tools/wrench, so other mods that accept wrenches accept it too."),
    craft("omnilogistics:wrench")])
entry("miner", "Void Miner", "omnilogistics:basic_void_miner", 7, [
    text("A one-block miner: feed it FE and it pulls ore blocks out of nothing. Every cycle rolls its tier's loot table ($(o)data/omnilogistics/loot_table/miner/<tier>.json$(), datapack-editable) and the item arrives in the four output slots, which auto-eject into every neighbour. Watch the beam: what you see coming down is what you get."),
    text("$(bold)Card slot$(): a Logistics Card keeps only items passing its filter (right-click the card in the slot to edit it); a miss still spends the cycle. $(bold)Bandwidth Upgrades$() (up to 3) double the items - and the FE - per cycle each. A redstone signal pauses the miner. The $(bold)tick field$() sets the cycle length (1..200); the tiers start at 200 / 100 / 50 / 25 ticks and cost 2k / 8k / 32k / 128k FE per cycle, richer tables as you go up; every number is in config/omnilogistics-common.toml."),
    craft("omnilogistics:basic_void_miner"), craft("omnilogistics:advanced_void_miner"), craft("omnilogistics:elite_void_miner"), craft("omnilogistics:ultimate_void_miner")])
print("resources ok")

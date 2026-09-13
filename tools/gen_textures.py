"""Generates every PNG the mod uses (blocks, items, GUI sheets). Pure stdlib, fully deterministic (no randomness).
Run: python tools/gen_textures.py   ->  src/main/resources/assets/omnilogistics/textures/{block,item,gui}/
Preview without launching the game: python tools/preview_textures.py (needs Pillow); in-game: gradlew runClientSelfTest.

Visual language
  * Hue-shifted ramps per material (7 tones, 9 for tubes), cool shadows / warm highlights, no per-pixel noise.
  * One light source, top-left: raised things (frames, plates, flanges, bolts) are light top+left / dark bottom+right,
    recessed things (grooves, screens, slots) are dark top+left with a lit lip bottom+right.
  * Machines: riveted 6px steel frame + recessed bay; top = louvre block + a big per-kind port + 2 LEDs; side = plain
    louvred bay + accent lamp; panel = thin steel bezel around a glass screen (~80% of the face) with one big
    per-kind element (router signal bars / extractor gem hatch / exposer lens), breathing over 4 frames.
  * Conduits: gunmetal tube with a 9-tone barrel (highlight left of centre, darkest column at the right edge),
    2px tier-coloured coupling rings at both ends, 2px type line in a 1px groove; cap = dark-rimmed tier flange with
    four 2x2 bolts and a recessed type core. IN / OUT arms use a bright mode ramp with an outlined pale arrow.
  * Items: smart cards (band, gold chip, outlined kind-coloured chevron, 9x9 glyph window, LED), PCB modules with a
    14x14 IC / socket and 2px glyphs, adjustable wrench with a cyan grip.
"""
import json
import os
import struct
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'omnilogistics', 'textures')
N = 32

# ---------------------------------------------------------------- colour helpers

def cl(v):
    return max(0, min(255, int(round(v))))


def rgb(h, a=255):
    return (h >> 16 & 255, h >> 8 & 255, h & 255, a)


def mix(a, b, t):
    return tuple(cl(a[k] + (b[k] - a[k]) * t) for k in range(4))


WARM = (255, 248, 236, 255)
COOL = (6, 9, 16, 255)


def lt(c, t):
    return mix(c, WARM, t)


def dk(c, t):
    return mix(c, COOL, t)


def ramp7(base):
    """7 tones: 0 deepest shadow .. 3 base .. 6 highlight. Shadows drift cool, highlights drift warm."""
    b = rgb(base) if isinstance(base, int) else base
    return [dk(b, .74), dk(b, .56), dk(b, .34), b, lt(b, .16), lt(b, .34), lt(b, .56)]


def ramp9(base):
    """9 tones for cylinders: 0 deepest .. 4 base .. 8 highlight (wider spread than ramp7)."""
    b = rgb(base) if isinstance(base, int) else base
    return [dk(b, .80), dk(b, .64), dk(b, .46), dk(b, .25), b, lt(b, .14), lt(b, .30), lt(b, .48), lt(b, .66)]


GM = ramp7(0x4d5663)          # gunmetal (frames, card bodies)
GM_DARK = ramp7(0x2c323b)     # dark gunmetal (recessed bays, PCB substrate)
STEEL = ramp7(0x8b95a1)       # bright steel (wrench, chip lids)
GOLD = ramp7(0xd0a63a)
CYAN = ramp7(0x3fd3ff)
TUBE = ramp9(0x4d5663)        # conduit barrel

TYPE_COLOR = {"item": 0x3fd3ff, "energy": 0xffd23f, "fluid": 0x3f8cff}
TIER_BASE = {"basic": 0x778290, "advanced": 0x4a80b8, "elite": 0x8a5fd0, "ultimate": 0xd9a832, "infinity": 0xe25bc2}
MODE_BASE = {"_pull": 0x4ad866, "_push": 0xf29a22}
MACHINE_ACCENT = {"router": 0xff9f1c, "extractor": 0x5cff7a, "exposer": 0x8a3fff, "distributor": 0x2ec4a8, "monitor": 0x3fd3ff,
                  **{"miner_" + k: v for k, v in TIER_BASE.items() if k != "infinity"}}   # void miners: one per tier, tier colour
VOID = 0xa060ff
RED_LED, GREEN_LED = 0xff4b4b, 0x5cff7a
GLASS = rgb(0x070b10)
OUTLINE = rgb(0x0b0e12)
# translucent glass of the conduit windows (alpha < 255: the world and the contents show through)
PANE = (160, 200, 222, 48)
PANE_HI = (232, 246, 255, 110)
PANE_LO = (84, 112, 132, 90)


# ---------------------------------------------------------------- image

class Img:
    def __init__(self, w, h, fill=(0, 0, 0, 0)):
        self.w, self.h = w, h
        self.p = [[fill] * w for _ in range(h)]

    def get(self, x, y):
        return self.p[y][x] if 0 <= x < self.w and 0 <= y < self.h else (0, 0, 0, 0)

    def px(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.p[y][x] = c

    def blend(self, x, y, c, t):
        cur = self.get(x, y)
        if cur[3] == 0:
            return
        self.px(x, y, mix(cur, (c[0], c[1], c[2], cur[3]), t))

    def rect(self, x, y, w, h, c):
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                self.px(xx, yy, c)

    def hl(self, x, y, w, c):
        self.rect(x, y, w, 1, c)

    def vl(self, x, y, h, c):
        self.rect(x, y, 1, h, c)

    def outline(self, x, y, w, h, c):
        self.hl(x, y, w, c); self.hl(x, y + h - 1, w, c)
        self.vl(x, y, h, c); self.vl(x + w - 1, y, h, c)

    def bevel(self, x, y, w, h, light, dark):
        """Raised edge: light top+left, dark bottom+right."""
        self.hl(x, y, w, light); self.vl(x, y, h, light)
        self.hl(x, y + h - 1, w, dark); self.vl(x + w - 1, y, h, dark)

    def recess(self, x, y, w, h, dark, light):
        """Recessed edge: dark (shadow) top+left, lit lip bottom+right."""
        self.hl(x, y, w, dark); self.vl(x, y, h, dark)
        self.hl(x, y + h - 1, w, light); self.vl(x + w - 1, y, h, light)

    def round1(self, x, y, w, h):
        for xx, yy in ((x, y), (x + w - 1, y), (x, y + h - 1), (x + w - 1, y + h - 1)):
            self.px(xx, yy, (0, 0, 0, 0))

    def glyph(self, x, y, rows, c, ch='#'):
        for yy, row in enumerate(rows):
            for xx, s in enumerate(row):
                if s == ch:
                    self.px(x + xx, y + yy, c)

    def glyph2(self, x, y, rows, fill, edge):
        """Two-tone glyph: 'X' = fill, 'o' = outline."""
        self.glyph(x, y, rows, edge, 'o')
        self.glyph(x, y, rows, fill, 'X')

    def paste(self, other, x, y):
        for yy in range(other.h):
            for xx in range(other.w):
                self.px(x + xx, y + yy, other.p[yy][xx])

    def save(self, rel, animation=None):
        raw = b''.join(b'\x00' + b''.join(struct.pack('BBBB', *p) for p in row) for row in self.p)

        def chunk(t, d):
            return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)

        data = (b'\x89PNG\r\n\x1a\n'
                + chunk(b'IHDR', struct.pack('>IIBBBBB', self.w, self.h, 8, 6, 0, 0, 0))
                + chunk(b'IDAT', zlib.compress(raw, 9))
                + chunk(b'IEND', b''))
        path = os.path.join(ROOT, rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, 'wb') as f:
            f.write(data)
        meta = path + '.mcmeta'
        if animation:
            with open(meta, 'w', encoding='utf-8') as f:
                json.dump({"animation": animation}, f)
        elif os.path.exists(meta):
            os.remove(meta)


def stack(frames):
    out = Img(frames[0].w, frames[0].h * len(frames))
    for k, f in enumerate(frames):
        out.paste(f, 0, k * f.h)
    return out


# ---------------------------------------------------------------- shared industrial details

def bolt(i, x, y, r=GM):
    """3x3 hex bolt head, lit top-left."""
    i.rect(x, y, 3, 3, r[2])
    i.px(x, y, r[6]); i.px(x + 1, y, r[5]); i.px(x, y + 1, r[5])
    i.px(x + 1, y + 1, r[4])
    i.px(x + 2, y + 2, r[0]); i.px(x + 2, y + 1, r[1]); i.px(x + 1, y + 2, r[1])


def led(i, x, y, color, on=True):
    c = rgb(color)
    i.rect(x - 1, y - 1, 4, 4, GM[0])
    i.rect(x, y, 2, 2, c if on else dk(c, .6))
    if on:
        i.px(x, y, lt(c, .5))


def louvres(i, x, y, w, count, step=3, r=GM_DARK):
    """Vent slats: dark slot + lit lip below it."""
    for k in range(count):
        yy = y + k * step
        i.hl(x, yy, w, r[0])
        i.hl(x, yy + 1, w, r[5])


def steel_frame(i, r=GM, t=6, bolts=True):
    """32x32 riveted frame, t px thick, bay recessed inside."""
    n = N
    i.rect(0, 0, n, n, r[3])
    i.bevel(0, 0, n, n, r[6], r[0])
    i.bevel(1, 1, n - 2, n - 2, r[5], r[1])
    i.recess(t - 1, t - 1, n - 2 * t + 2, n - 2 * t + 2, r[0], r[5])
    i.rect(t, t, n - 2 * t, n - 2 * t, GM_DARK[2])
    if bolts:
        for bx, by in ((2, 2), (n - 5, 2), (2, n - 5), (n - 5, n - 5)):
            bolt(i, bx, by, r)


def octagon(i, x, y, s, cut, fn):
    """Call fn(xx, yy, u, v) for every pixel of an s x s octagon with corner cut `cut`."""
    for v in range(s):
        for u in range(s):
            du = min(u, s - 1 - u)
            dv = min(v, s - 1 - v)
            if du + dv >= cut:
                fn(x + u, y + v, u, v)


def fill_octagon(i, x, y, s, cut, c):
    octagon(i, x, y, s, cut, lambda xx, yy, u, v: i.px(xx, yy, c))


def lit_octagon(i, x, y, s, cut, r, offs=0):
    """Raised octagonal boss: tone from top-left (light) to bottom-right (dark)."""
    def f(xx, yy, u, v):
        d = (u + v) / (2 * (s - 1))
        idx = cl(5 - d * 4 + offs)
        i.px(xx, yy, r[max(0, min(6, idx))])
    octagon(i, x, y, s, cut, f)


def accent_core(i, x, y, s, c):
    """s x s glowing accent block: lit top-left pixel, darker right/bottom edge."""
    i.rect(x, y, s, s, dk(c, .3))
    i.rect(x, y, s - 1, s - 1, c)
    i.px(x, y, lt(c, .55))
    i.hl(x + 1, y, s - 2, lt(c, .25))


# ---------------------------------------------------------------- conduits

# 12 columns across the tube (9-tone ramp): highlight left of centre, darkest at the right edge
BARREL = [3, 5, 7, 8, 7, 6, 5, 4, 3, 2, 1, 0]

# 12 x 8 flow arrow with a 1px outline ('o'), 2px shaft, 8px head. Tip at the bottom = points along +y.
ARROW = ["....oooo....",
         "....oXXo....",
         "....oXXo....",
         ".ooooXXoooo.",
         ".oXXXXXXXXo.",
         "..oXXXXXXo..",
         "...oXXXXo...",
         "....oXXo...."]


def arrow_rows(down):
    return ARROW if down else ARROW[::-1]


def barrel_col(i, x, y0, h, ramp, k, offs=0):
    idx = max(0, min(8, BARREL[k] + offs))
    i.vl(x, y0, h, ramp[idx])


def tube_strip(i, tier9, type_color=None, mode9=None):
    """ARM STRIP x10..21: plain = y0..11, one continuous glass run (no end rings, so blocks join seamlessly);
    the strip is mirror-symmetric across its width because the blockstate rotations flip it. Mode arms = y0..9."""
    if mode9 is None:
        tc = rgb(type_color)
        pane = mix(PANE[:3] + (255,), tc, .65)[:3] + (PANE[3] + 28,)   # glass tinted by the carried type: item cyan, energy yellow, fluid blue
        i.rect(12, 0, 8, 12, pane)                              # 4px window
        edge = lt(tc, .35)[:3] + (130,)
        i.vl(12, 0, 12, edge); i.vl(19, 0, 12, edge)
        i.vl(11, 0, 12, tier9[6]); i.vl(20, 0, 12, tier9[6])   # 2px tier rails: light inner, dark outer edge
        i.vl(10, 0, 12, tier9[3]); i.vl(21, 0, 12, tier9[3])
    else:
        for k in range(12):
            barrel_col(i, 10 + k, 0, 10, mode9, k)
            barrel_col(i, 10 + k, 0, 1, mode9, k, +1)     # end rings in the mode colour
            barrel_col(i, 10 + k, 9, 1, mode9, k, -1)
        pale = lt(mode9[4], .62)
        i.glyph2(10, 0, arrow_rows(mode9 is MODE9["_pull"]), pale, mode9[0])   # rows 0..7: plate() eats row 8


def tube_cap(i, tier, type_color, y=12):
    """CAP x10..21, y12..23: the knot face shown where a conduit bends, branches or ends (straight runs show the strip)."""
    # tube end behind the flange (visible at the corners), lit lip top-left
    i.rect(10, y, 12, 12, TUBE[3])
    i.bevel(10, y, 12, 12, TUBE[7], TUBE[0])
    # 1px dark rim then the 10x10 lit tier flange
    fill_octagon(i, 10, y, 12, 4, tier[0])
    lit_octagon(i, 11, y + 1, 10, 3, tier)
    # glass port in the flange: a 1px type-coloured ring around a 6x6 window into the knot
    tc = rgb(type_color)
    i.outline(12, y + 2, 8, 8, dk(tc, .25))
    i.px(12, y + 2, lt(tc, .3))
    i.rect(13, y + 3, 6, 6, PANE)
    i.hl(13, y + 3, 6, PANE_HI); i.vl(13, y + 3, 6, PANE_HI)
    i.hl(13, y + 8, 6, PANE_LO); i.vl(18, y + 3, 6, PANE_LO)


def plate(i, m):
    """Connector plate face (8..23, 8..23) for _pull / _push."""
    i.rect(8, 8, 16, 16, m[3])
    i.bevel(8, 8, 16, 16, m[5], m[0])
    i.bevel(9, 9, 14, 14, m[4], m[1])
    for bx, by in ((10, 10), (20, 10), (10, 20), (20, 20)):
        i.rect(bx, by, 2, 2, m[1]); i.px(bx, by, m[6])


def plate_rim(i, m):
    i.rect(8, 28, 16, 4, m[3])
    i.hl(8, 28, 16, m[6]); i.hl(8, 29, 16, m[4]); i.hl(8, 31, 16, m[0])
    i.vl(8, 28, 4, m[5]); i.vl(23, 28, 4, m[0])


MODE9 = {k: ramp9(v) for k, v in MODE_BASE.items()}


def pipes():
    for t, tc in TYPE_COLOR.items():
        for tier_name, tier_base in TIER_BASE.items():
            tier = ramp7(tier_base)
            tier9 = ramp9(tier_base)
            for suffix in ("", "_pull", "_push"):
                i = Img(N, N)
                if suffix == "":
                    i.rect(0, 0, N, N, GM[2])                 # body colour for particles
                    tube_strip(i, tier9, type_color=tc)
                    tube_cap(i, tier, tc)
                else:
                    m = ramp7(MODE_BASE[suffix])
                    m9 = MODE9[suffix]
                    i.rect(0, 0, N, N, m[1])
                    tube_strip(i, tier9, mode9=m9)
                    plate(i, m)
                    plate_rim(i, m)
                i.save('block/pipe_%s_%s%s.png' % (t, tier_name, suffix))


# ---------------------------------------------------------------- machines

def machine_side(kind):
    i = Img(N, N)
    steel_frame(i)
    # plain dark bay with 2px louvres (covered by the panel model in game)
    i.rect(6, 6, 20, 20, GM_DARK[2])
    louvres(i, 7, 9, 18, 4, 4, GM_DARK)
    # accent status lamp in the bottom frame rail (centre)
    c = MACHINE_ACCENT[kind]
    i.rect(12, 28, 8, 2, GM[0])
    i.rect(13, 28, 6, 1, rgb(c))
    i.px(13, 28, lt(rgb(c), .4))
    i.save('block/%s_side.png' % kind)


def machine_top(kind):
    i = Img(N, N)
    steel_frame(i)
    r = GM_DARK
    c = rgb(MACHINE_ACCENT[kind])
    i.rect(6, 6, 20, 20, r[2])
    if kind.startswith("miner"):
        # void port dead centre (the beam rises out of it): octagonal bezel, black glass, tier ring, purple core, LEDs below
        lit_octagon(i, 8, 6, 16, 5, GM)
        fill_octagon(i, 9, 7, 14, 4, rgb(0x0b0912))
        i.outline(12, 10, 8, 8, c)
        i.rect(13, 11, 6, 6, rgb(0x2a1450))
        i.rect(15, 13, 2, 2, rgb(VOID))
        i.px(15, 13, rgb(0xe8d8ff))
        led(i, 13, 24, MACHINE_ACCENT[kind])
        led(i, 18, 24, GREEN_LED)
        i.save('block/%s_top.png' % kind)
        return
    # louvred vent block, left (x7..13)
    i.rect(7, 7, 7, 18, r[1])
    i.recess(7, 7, 7, 18, r[0], r[4])
    louvres(i, 8, 9, 5, 5, 3)
    # functional port, right: 10x10 at (15,7)
    px, py = 15, 7
    if kind == "router":        # antenna dish: octagonal boss, dark ring, 6px emitter
        lit_octagon(i, px, py, 10, 3, GM)
        fill_octagon(i, px + 1, py + 1, 8, 2, GM[0])
        accent_core(i, px + 2, py + 2, 6, c)
    elif kind == "extractor":   # square hatch: steel bevel, recessed chamber, 6px core
        i.rect(px, py, 10, 10, GM[4]); i.bevel(px, py, 10, 10, GM[6], GM[0])
        i.rect(px + 1, py + 1, 8, 8, GM[0]); i.recess(px + 1, py + 1, 8, 8, GM[0], GM[3])
        accent_core(i, px + 2, py + 2, 6, c)
    else:                       # exposer lens: octagonal bezel, dark glass, 6px iris with pupil + specular
        lit_octagon(i, px, py, 10, 3, GM)
        fill_octagon(i, px + 1, py + 1, 8, 2, rgb(0x0c0a14))
        i.rect(px + 2, py + 2, 6, 6, dk(c, .3))
        i.rect(px + 3, py + 3, 4, 4, c)
        i.rect(px + 4, py + 4, 2, 2, rgb(0x0c0a14))
        i.px(px + 2, py + 2, lt(c, .7)); i.px(px + 3, py + 2, lt(c, .35)); i.px(px + 2, py + 3, lt(c, .35))
    # two status LEDs under the port
    led(i, 17, 20, MACHINE_ACCENT[kind])
    led(i, 22, 20, GREEN_LED)
    i.save('block/%s_top.png' % kind)


ARROW8 = ["oooooooo",
          ".oXXXXo.",
          "..oXXo..",
          "...oo..."]

PULSE = [0.55, 0.78, 1.0, 0.78]
ANIM = {"frametime": 6, "interpolate": True}


def panel_base(i):
    """Thin raised steel bezel (3px) around the screen area 3..28."""
    i.rect(0, 0, N, N, GM[3])
    i.bevel(0, 0, N, N, GM[6], GM[0])
    i.bevel(1, 1, N - 2, N - 2, GM[5], GM[1])


def screen(i, x, y, w, h, c, p):
    """Recessed glass screen: dark shadow top-left, lit lip bottom-right, tinted glass, top reflection row."""
    i.rect(x, y, w, h, GM[0])
    i.recess(x, y, w, h, GM[0], GM[4])
    inner = mix(GLASS, c, 0.14 + 0.12 * p)
    i.rect(x + 1, y + 1, w - 2, h - 2, inner)
    i.hl(x + 1, y + 1, w - 2, lt(inner, .10))
    return inner


DIAMOND12 = [".....##.....", "....####....", "...######...", "..########..", ".##########.", "############",
             "############", ".##########.", "..########..", "...######...", "....####....", ".....##....."]


def ring(i, cx, cy, rad, col, lo=4, hi=27):
    """1px circle outline of radius rad around (cx, cy), clipped to the screen area."""
    for y in range(cy - rad - 1, cy + rad + 2):
        for x in range(cx - rad - 1, cx + rad + 2):
            d = ((x - cx + 0.5) ** 2 + (y - cy + 0.5) ** 2) ** 0.5
            if abs(d - rad) < 0.6 and lo <= x <= hi and lo <= y <= hi:
                i.px(x, y, col)


def machine_panel(kind):
    frames = []
    accent = rgb(MACHINE_ACCENT[kind])
    for step, p in enumerate(PULSE):
        i = Img(N, N)
        panel_base(i)
        g = mix(dk(accent, .3), lt(accent, .25), p)      # breathing accent tone
        m0 = GM[0]
        if kind == "router":
            screen(i, 3, 3, 26, 26, accent, p)
            # four 4px signal bars growing to the right, brightest on the right
            for k, hgt in enumerate((6, 10, 14, 18)):
                x = 6 + k * 5
                col = mix(dk(g, .3), g, k / 3)
                i.rect(x, 27 - hgt, 4, hgt, col)
                i.hl(x, 27 - hgt, 4, lt(col, .3))
                i.vl(x + 3, 27 - hgt, hgt, dk(col, .3))
            # carrier dot (blinks with the pulse) top-left
            i.rect(6, 6, 3, 3, g if p > 0.6 else dk(g, .45))
            i.px(6, 6, lt(g, .5))
        elif kind == "extractor":
            screen(i, 3, 3, 26, 26, accent, p)
            # steel tray / hatch at the bottom of the chamber
            i.rect(7, 20, 18, 5, GM[3]); i.bevel(7, 20, 18, 5, GM[6], GM[0])
            i.rect(9, 21, 14, 2, GM[0]); i.hl(9, 23, 14, GM[4])
            # 12x12 faceted gem being pulled toward the tray
            for v, row in enumerate(DIAMOND12):
                for u, s in enumerate(row):
                    if s == '#':
                        if u < 6 and v < 6:
                            col = lt(g, .3)
                        elif u >= 6 and v >= 6:
                            col = dk(g, .3)
                        else:
                            col = g
                        i.px(10 + u, 6 + v, col)
            i.rect(12, 8, 2, 2, lt(g, .6))
            # inward chevrons (2px stroke) on both sides
            chev = ["##..", ".##.", "..##", "..##", ".##.", "##.."]
            i.glyph(5, 9, chev, g)
            i.glyph(23, 9, [r[::-1] for r in chev], g)
            i.rect(15, 18, 2, 2, dk(g, .2))
        elif kind == "distributor":
            screen(i, 3, 3, 26, 26, accent, p)
            i.rect(15, 5, 2, 8, g)                                  # one input coming down
            i.rect(15, 5, 2, 2, lt(g, .5))
            i.hl(8, 13, 16, g); i.hl(8, 14, 16, dk(g, .35))         # the split bar
            for x in (8, 15, 22):                                   # three lanes fanning out to their own machine
                col = mix(dk(g, .25), g, (x - 8) / 14)
                i.rect(x, 14, 2, 8, col)
                i.glyph2(x - 3, 21, ARROW8, lt(col, .35), m0)
            i.rect(6, 6, 2, 2, g if p > 0.6 else dk(g, .45))        # batch lamp
        elif kind.startswith("miner"):
            # void sonar: rings expanding from the core one step per frame (tier colour), steady purple beam, white core
            screen(i, 3, 3, 26, 26, accent, p)
            for k in range(3):
                rad = 2 + (k * 3 + step) % 9
                ring(i, 16, 16, rad, mix(dk(accent, .45), lt(accent, .25), 1 - rad / 11))
            i.vl(15, 5, 22, rgb(0x6a3fbf)); i.vl(16, 5, 22, lt(rgb(VOID), .15))
            i.rect(14, 14, 4, 4, rgb(0xe8d8ff)); i.px(14, 14, WARM)
        else:
            # exposer lens: steel octagonal bezel, dark glass, big purple iris, pupil, specular
            i.rect(2, 2, N - 4, N - 4, GM[3])
            lit_octagon(i, 3, 3, 26, 7, GM)
            fill_octagon(i, 4, 4, 24, 6, GM[0])
            fill_octagon(i, 5, 5, 22, 6, mix(rgb(0x0b0912), accent, .08 * p))

            def iris(xx, yy, u, v):
                d = max(abs(u - 6.5), abs(v - 6.5))
                if d > 5.6:
                    i.px(xx, yy, dk(g, .35))
                elif d > 1.6:
                    i.px(xx, yy, g)
                else:
                    i.px(xx, yy, rgb(0x0b0912))
            octagon(i, 9, 9, 14, 4, iris)
            i.rect(11, 11, 3, 3, lt(accent, .7))
            i.rect(11, 11, 2, 2, WARM)
            for k in range(26):
                i.blend(3 + k, 2, accent, .10 * p); i.blend(3 + k, 29, accent, .10 * p)
                i.blend(2, 3 + k, accent, .10 * p); i.blend(29, 3 + k, accent, .10 * p)
        frames.append(i)
    stack(frames).save('block/%s_panel.png' % kind, ANIM)


def monitor_front():
    """The screen face: thin steel bezel around dark glass. Everything on it is drawn by MonitorRenderer at runtime."""
    i = Img(N, N)
    steel_frame(i, t=3)
    i.rect(3, 3, 26, 26, GM[0])
    i.recess(3, 3, 26, 26, rgb(0x05070a), GM[3])
    i.rect(4, 4, 24, 24, rgb(0x0a0f14))
    for k in range(24):                                  # faint scanlines so it reads as a screen even when empty
        if k % 3 == 0:
            i.hl(4, 4 + k, 24, rgb(0x0d141b))
    i.px(5, 5, rgb(0x1b2b36))
    led(i, 25, 26, MACHINE_ACCENT["monitor"])
    i.save('block/monitor_front.png')


def machines():
    for kind in MACHINE_ACCENT:
        machine_side(kind)
        machine_top(kind)
        if kind != "monitor":
            machine_panel(kind)
    monitor_front()


# ---------------------------------------------------------------- items: cards

KIND_GLYPH = {   # 7x7, 2px strokes so they survive the 32 -> 16 inventory scale
    'card': ["###.###", "###.###", "###.###", ".......", "###.###", "###.###", "###.###"],          # parcels
    'card_energy': ["....##.", "...##..", "..##...", ".#####.", "...##..", "..##...", ".##...."],   # bolt
    'card_fluid': ["...#...", "..###..", ".#####.", "#######", "#######", ".#####.", "..###.."],    # drop
    'card_void': ["##...##", "###.###", ".#####.", "..###..", ".#####.", "###.###", "##...##"],     # X
    'card_vacuum': ["#######", "#######", ".#####.", "..###..", "...##..", "...##..", "...##.."],   # funnel
    'card_activator': ["##.....", "###....", "####...", "#####..", "######.", "##.###.", "#...##."],  # cursor
    'card_breaker': [".#####.", "##.#.##", "...##..", "...##..", "..##...", ".##....", "##....."],  # pickaxe
    'card_placer': ["..##...", "######.", "..##...", ".......", "#######", "#######", "#######"],   # plus + slab
    'card_detector': ["..###..", ".##.##.", "##...##", "##...##", "##...##", ".##.##.", "..###.."],  # ring
    'card_stock': ["#######", "#.....#", "#.###.#", "#.###.#", "#.###.#", "#######", "#######"],      # a crate kept full
}
CARD_KINDS = [('card', 0x5cff7a, 0xff9f1c), ('card_energy', 0xffd23f, 0xffd23f), ('card_fluid', 0x3f8cff, 0x3f8cff),
              ('card_void', 0x8a3fff, None), ('card_vacuum', 0x2ec4a8, None), ('card_activator', 0xffb385, None),
              ('card_breaker', 0xff4b4b, None), ('card_placer', 0xb98cff, None), ('card_detector', 0xffd23f, None),
              ('card_stock', 0x5cff7a, None)]

CHEVRON = ["...oo...",    # 8x8 arrow: 2px shaft, 6px head, 1px outline; tip down
           "..oXXo..",
           "..oXXo..",
           "oooXXooo",
           "oXXXXXXo",
           ".oXXXXo.",
           "..oXXo..",
           "...oo..."]


def card(name, kind, accent, insert, bound, pips=0):
    i = Img(N, N)
    a = rgb(accent)
    x0, y0, w, h = 7, 3, 18, 26
    # body: dark outline, gunmetal plate lit top-left
    i.rect(x0 - 1, y0 - 1, w + 2, h + 2, OUTLINE)
    i.round1(x0 - 1, y0 - 1, w + 2, h + 2)
    i.rect(x0, y0, w, h, GM[3])
    i.bevel(x0, y0, w, h, GM[6], GM[0])
    i.rect(x0 + 1, y0 + 1, w - 2, h - 2, GM[3])
    i.hl(x0 + 1, y0 + 1, w - 2, GM[5]); i.vl(x0 + 1, y0 + 1, h - 2, GM[4])
    i.hl(x0 + 1, y0 + h - 2, w - 2, GM[1]); i.vl(x0 + w - 2, y0 + 1, h - 2, GM[2])
    # colour band
    i.rect(x0 + 1, 5, w - 2, 5, a)
    i.hl(x0 + 1, 5, w - 2, lt(a, .45))
    i.hl(x0 + 1, 9, w - 2, dk(a, .35))
    i.hl(x0 + 1, 10, w - 2, GM[0])
    for k in range(pips):                                   # capacity pips: 1 = x4, 2 = x16, 3 = x64
        i.rect(9 + k * 3, 6, 2, 3, WARM)
        i.px(9 + k * 3, 8, dk(a, .45))
    # gold chip with contact grooves
    i.rect(9, 12, 6, 5, GOLD[3])
    i.bevel(9, 12, 6, 5, GOLD[6], GOLD[0])
    i.hl(10, 14, 4, GOLD[1]); i.vl(11, 13, 3, GOLD[1])
    # direction chevron in the kind colour (extract = down / into the card, insert = up / out)
    rows = CHEVRON[::-1] if insert else CHEVRON
    i.glyph2(16, 10, rows, a, GM[0])
    # kind glyph in the accent colour, on a 9x9 dark window
    i.rect(9, 18, 9, 9, GM[1])
    i.recess(9, 18, 9, 9, GM[0], GM[4])
    i.glyph(10, 19, KIND_GLYPH[kind], a)
    # label lines + LED
    i.hl(19, 20, 4, GM[5]); i.hl(19, 22, 3, GM[5])
    led(i, 21, 25, GREEN_LED if bound else RED_LED)
    i.save('item/%s.png' % name)


MULTI_CARDS = [("card_x4", 1, TIER_BASE["advanced"]), ("card_x16", 2, TIER_BASE["elite"]), ("card_x64", 3, TIER_BASE["ultimate"])]


def cards():
    for base, pips, tier in MULTI_CARDS:                        # same card, tier band + pips for 4 / 16 / 64 references
        KIND_GLYPH[base] = KIND_GLYPH["card"]
        for name, insert in ((base + "_extract", False), (base + "_insert", True)):
            for bound in (False, True):
                card(name + ("_bound" if bound else ""), base, tier, insert, bound, pips)
    for base, c_ex, c_in in CARD_KINDS:
        variants = [(base + '_extract', c_ex, False)]
        if c_in is not None:
            variants.append((base + '_insert', c_in, True))
        for name, accent, insert in variants:
            for bound in (False, True):
                card(name + ('_bound' if bound else ''), base, accent, insert, bound)


# ---------------------------------------------------------------- items: upgrade modules

UPGRADE_GLYPH = {   # 10 wide, 2px strokes
    'speed_upgrade': (0xff4b4b, [".....###..", "....###...", "...###....", "..#######.", ".....###..", "....###...",
                                 "...###....", "..###....."]),
    'parallel_upgrade': (0x3f8cff, ["##..##..##"] * 10),
    'chunk_upgrade': (0x2ec4a8, ["##########", "#........#", "#..####..#", "#..#..#..#", "#..#..#..#", "#..####..#",
                                 "#........#", "##########"]),
    'range_upgrade': (0xb98cff, ["..######..", ".########.", "##......##", "##......##", "##..##..##", "##..##..##",
                                 "##......##", "##......##", ".########.", "..######.."]),
    'gem_module': (0x5cff7a, ["....##....", "...####...", "..######..", ".########.", "##########", "##########",
                              ".########.", "..######..", "...####...", "....##...."]),
    'fusion_module': (0xff9f1c, ["....##....", "....##....", "....##....", "....##....", "##########", "##########",
                                 "....##....", "....##....", "....##....", "....##...."]),
}
PCB = ramp7(0x24323d)
COPPER = ramp7(0xa8783c)


def module(name, accent, glyph, is_module):
    i = Img(N, N)
    a = rgb(accent)
    # board
    bx, by, bw, bh = 4, 5, 24, 22
    i.rect(bx - 1, by - 1, bw + 2, bh + 2, OUTLINE)
    i.round1(bx - 1, by - 1, bw + 2, bh + 2)
    i.rect(bx, by, bw, bh, PCB[3])
    i.bevel(bx, by, bw, bh, PCB[6], PCB[0])
    # four copper traces from the component down to the edge connector
    for x in (11, 14, 17, 20):
        i.vl(x, 21, 5, COPPER[2]); i.px(x, 21, COPPER[4])
    # small parts either side of the traces: capacitor (left), resistor (right)
    i.rect(6, 22, 3, 3, GM[1]); i.rect(6, 22, 2, 2, GM[4]); i.px(6, 22, GM[6])
    i.rect(23, 22, 4, 2, rgb(0x2b2b2f)); i.vl(24, 22, 2, a); i.px(26, 22, rgb(0x5a5a60))
    gx, gy = 11, 9      # glyph origin (10 wide, centred in the 14x14 part at 9..22, 7..20)
    gh = len(glyph)
    gy = 7 + (14 - gh) // 2
    if is_module:
        # socketed component: steel collar with a dark faceted core carrying the glyph
        lit_octagon(i, 9, 7, 14, 3, STEEL)
        i.rect(10, 8, 12, 12, rgb(0x0a0c10))
        i.recess(10, 8, 12, 12, rgb(0x05070a), STEEL[2])
        i.glyph(gx, gy, glyph, a)
        i.glyph(gx, gy, glyph[:3], lt(a, .35))
        i.glyph(gx, gy + gh - 3, glyph[gh - 3:], dk(a, .3))
    else:
        # 14x14 black IC with a lit edge, 2px pins on both sides, accent glyph printed on the lid
        for y in range(8, 20, 3):
            i.hl(7, y, 2, STEEL[5]); i.hl(7, y + 1, 2, STEEL[2])
            i.hl(23, y, 2, STEEL[4]); i.hl(23, y + 1, 2, STEEL[1])
        i.rect(9, 7, 14, 14, rgb(0x0f1216))
        i.bevel(9, 7, 14, 14, rgb(0x3a414b), rgb(0x05070a))
        i.px(10, 8, GM[4])                                   # pin-1 dot
        i.glyph(gx, gy, glyph, a)
        i.glyph(gx, gy, glyph[:2], lt(a, .3))
    # gold edge connector fingers (below the board)
    i.rect(6, 27, 20, 5, OUTLINE)
    for k in range(6):
        x = 7 + k * 3
        i.rect(x, 27, 2, 4, GOLD[3])
        i.vl(x, 27, 4, GOLD[5]); i.px(x + 1, 30, GOLD[1])
    i.hl(6, 26, 20, PCB[0])
    i.save('item/%s.png' % name)


def upgrades():
    for name, (accent, glyph) in UPGRADE_GLYPH.items():
        module(name, accent, glyph, name.endswith('_module'))


# ---------------------------------------------------------------- items: wrench

def wrench():
    i = Img(N, N)
    ax, ay = 6.0, 26.0
    bx, by = 20.5, 11.5
    hx, hy = 23.0, 8.5
    mask = [[False] * N for _ in range(N)]
    ridge = [[False] * N for _ in range(N)]
    grip = [[False] * N for _ in range(N)]

    def seg_dist(px, py):
        vx, vy = bx - ax, by - ay
        t = ((px - ax) * vx + (py - ay) * vy) / (vx * vx + vy * vy)
        t = max(0.0, min(1.0, t))
        cx, cy = ax + vx * t, ay + vy * t
        return ((px - cx) ** 2 + (py - cy) ** 2) ** .5, t

    for y in range(N):
        for x in range(N):
            px, py = x + .5, y + .5
            d, t = seg_dist(px, py)
            in_handle = d <= 2.6
            dx, dy = px - hx, py - hy
            rr = (dx * dx + dy * dy) ** .5
            u = (dx - dy) * 0.7071
            v = (dx + dy) * 0.7071
            in_head = rr <= 6.3 and not (u > 0.3 and abs(v) < 2.0)
            in_head = in_head and not (u > 4.8 and abs(v) < 3.6)
            mask[y][x] = in_handle or in_head
            ridge[y][x] = in_handle and d <= 0.9
            grip[y][x] = in_handle and 0.08 <= t <= 0.42

    def inside(x, y):
        return 0 <= x < N and 0 <= y < N and mask[y][x]

    for y in range(N):
        for x in range(N):
            if not mask[y][x]:
                if any(inside(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    i.px(x, y, OUTLINE)
                continue
            r = CYAN if grip[y][x] else STEEL
            top_free = not inside(x, y - 1) or not inside(x - 1, y)
            bot_free = not inside(x, y + 1) or not inside(x + 1, y)
            if top_free and not bot_free:
                c = r[6]
            elif bot_free and not top_free:
                c = r[1]
            elif top_free and bot_free:
                c = r[4]
            else:
                c = r[5] if ridge[y][x] else r[3]
            i.px(x, y, c)
    i.rect(17, 12, 4, 3, STEEL[1]); i.hl(17, 12, 4, STEEL[5]); i.px(18, 13, STEEL[3])
    i.save('item/wrench.png')


# ---------------------------------------------------------------- gui

G_BG = rgb(0x1a1e25)
G_INV = rgb(0x14171d)
G_HEAD = rgb(0x353d49)
G_FRAME = ramp7(0x46505c)


def panel(w, h):
    i = Img(256, 256) if max(w, h) <= 256 else Img(512, 512)   # DarkScreen picks the sheet size the same way
    i.rect(0, 0, w, h, G_FRAME[3])
    i.outline(0, 0, w, h, rgb(0x06080b))
    i.bevel(1, 1, w - 2, h - 2, G_FRAME[6], G_FRAME[0])
    i.bevel(2, 2, w - 4, h - 4, G_FRAME[5], G_FRAME[1])
    # recessed inner ground
    i.recess(4, 4, w - 8, h - 8, rgb(0x0a0d11), G_FRAME[4])
    i.rect(5, 5, w - 10, h - 10, G_BG)
    # header band (rows 5..16): lighter metal strip, cyan accent line fading right, dark seam + lit lip under it
    i.rect(5, 5, w - 10, 12, G_HEAD)
    i.hl(5, 5, w - 10, lt(G_HEAD, .12))
    for x in range(5, w - 5):
        t = (x - 5) / max(1, w - 11)
        i.px(x, 17, mix(rgb(0x3fd3ff), rgb(0x1e3f4c), t))
    i.hl(5, 18, w - 10, rgb(0x080b0e))
    i.hl(5, 19, w - 10, rgb(0x262d37))
    i.round1(0, 0, w, h)
    # 3x3 corner bolts in a dark seat, like the machine frames
    for bx, by in ((2, 2), (w - 5, 2), (2, h - 5), (w - 5, h - 5)):
        i.rect(bx - 1, by - 1, 5, 5, rgb(0x0a0d11))
        bolt(i, bx, by, GM)
    return i


def slot(i, x, y):
    i.rect(x - 1, y - 1, 18, 18, rgb(0x0a0d11))
    i.rect(x, y, 16, 16, rgb(0x12161b))
    i.recess(x, y, 16, 16, rgb(0x05070a), rgb(0x3a424c))
    i.hl(x + 1, y + 1, 14, rgb(0x0d1014))
    i.vl(x + 1, y + 1, 14, rgb(0x0d1014))


def inventory(i, x, y):
    for r in range(3):
        for c in range(9):
            slot(i, x + c * 18, y + r * 18)
    for c in range(9):
        slot(i, x + c * 18, y + 58)


def divider(i, y, w, h):
    """Groove divider; the player-inventory zone below it is one tone darker."""
    i.rect(5, y + 2, w - 10, h - 5 - (y + 2), G_INV)
    i.hl(5, y, w - 10, rgb(0x0a0d11))
    i.hl(5, y + 1, w - 10, rgb(0x2b323c))


def box(i, x, y, w, h):
    i.rect(x, y, w, h, rgb(0x0a0d11))
    i.recess(x, y, w, h, rgb(0x05070a), rgb(0x3a424c))


def gui_filter():
    """One panel per grid height; the reference slots themselves are drawn by FilterScreen, so one panel fits 1 and 4."""
    for name, rows in (("filter", 1), ("filter_tall", 2), ("filter_huge", 8)):
        shift = (rows - 1) * 18
        h = 242 + shift
        i = panel(256, h)
        divider(i, 150 + shift, 256, h)
        inventory(i, 47, 162 + shift)
        i.save('gui/%s.png' % name)


def gui_pipe():
    i = panel(176, 246)
    divider(i, 152, 176, 246)
    inventory(i, 8, 164)
    i.save('gui/pipe.png')


def glow():
    """16x16 white sprite for the energy core (ConduitRenderer); stitched via atlases/blocks.json."""
    Img(16, 16, (255, 255, 255, 255)).save('block/glow.png')


def gui_router():
    i = panel(176, 180)
    for k in range(8):
        slot(i, 8 + k * 18, 24)
    slot(i, 8, 62)
    slot(i, 44, 62)
    slot(i, 62, 62)
    box(i, 95, 67, 74, 10)
    box(i, 96, 51, 31, 14)               # tick field
    divider(i, 86, 176, 180)
    inventory(i, 8, 98)
    i.save('gui/router.png')


def gui_extractor():
    i = panel(176, 208)
    for x, y in ((26, 20), (26, 42), (26, 64), (62, 42), (98, 20), (116, 20), (98, 42), (116, 42),
                 (62, 96), (80, 96), (98, 96), (116, 96)):
        slot(i, x, y)
    for lane in range(3):
        box(i, 44, 19 + lane * 22, 5, 18)
    box(i, 7, 19, 10, 88)
    box(i, 61, 65, 28, 14)               # tick field
    divider(i, 115, 176, 208)
    inventory(i, 8, 126)
    i.save('gui/extractor.png')


def gui_distributor():
    i = panel(176, 246)
    for col in range(2):             # two columns of six lanes: card, buffer, destination icon, status pip
        for k in range(6):
            x, y = 8 + col * 80, 20 + k * 18
            slot(i, x, y)
            slot(i, x + 18, y)
            box(i, x + 37, y, 16, 16)
            box(i, x + 55, y + 5, 6, 6)
    slot(i, 152, 129)                # parallel upgrade
    divider(i, 152, 176, 246)
    inventory(i, 8, 164)
    i.save('gui/distributor.png')


def gui_miner():
    i = panel(176, 166)
    slot(i, 30, 20); slot(i, 30, 46)                     # card, speed upgrade
    for x, y in ((116, 20), (134, 20), (116, 38), (134, 38)):
        slot(i, x, y)
    box(i, 7, 19, 10, 46)                                # energy bar
    box(i, 64, 22, 20, 20)                               # void window: the item on its way out
    box(i, 63, 45, 22, 7)                                # cycle progress
    box(i, 115, 55, 32, 14)                              # tick field
    divider(i, 73, 176, 166)
    inventory(i, 8, 84)
    i.save('gui/miner.png')


if __name__ == '__main__':
    pipes(); machines(); cards(); upgrades(); wrench(); glow()
    gui_filter(); gui_router(); gui_extractor(); gui_pipe(); gui_miner(); gui_distributor()
    print('written to', ROOT)

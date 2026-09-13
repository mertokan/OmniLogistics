# -*- coding: utf-8 -*-
"""The mod logo, built out of the mod's own in-game textures (tools/gen_textures.py) so the icon and the block a
player picks up are the same object.

The Wireless Router as a block, three-quarter view, with a conduit plugged into each of its open faces.

Run: python tools/gen_logo.py   ->  src/main/resources/logo.png (the jar icon and the CurseForge avatar)
"""
import os, struct, sys, zlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_textures as G
from gen_textures import Img, rgb, mix, lt, dk, ramp9, TYPE_COLOR, MACHINE_ACCENT, GM, GM_DARK

HERE = os.path.dirname(os.path.abspath(__file__))
PRESS = os.path.join(HERE, '..', 'docs', 'press')
JAR = os.path.join(HERE, '..', 'src', 'main', 'resources', 'logo.png')
S, SCALE = 320, 2        # 640 square: the block at about 40% of it, sitting in its own space


# ---------------------------------------------------------------- output

def write(img, paths):
    raw = b''.join(b'\x00' + b''.join(struct.pack('BBBB', *p) for p in row) for row in img.p)

    def chunk(t, d):
        return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)

    data = (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', img.w, img.h, 8, 6, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))
    for p in paths:
        os.makedirs(os.path.dirname(p), exist_ok=True)
        open(p, 'wb').write(data)
        print('wrote', os.path.normpath(p))


def upscale(img, k):
    big = Img(img.w * k, img.h * k)
    for y in range(img.h):
        for x in range(img.w):
            big.rect(x * k, y * k, k, k, img.p[y][x])
    return big


# ---------------------------------------------------------------- the mod's own textures, in memory

def textures():
    """Run the texture generator with save() intercepted, so every face it draws is available as an Img."""
    got = {}
    keep = Img.save
    Img.save = lambda self, rel, animation=None: got.setdefault(rel, self)
    try:
        G.machines()
        G.pipes()
        G.cards()
    finally:
        Img.save = keep
    return got


def frame0(img):
    """Animated sheets are saved as a vertical strip; the logo only wants the first frame."""
    if img.h <= G.N:
        return img
    out = Img(G.N, G.N)
    for y in range(G.N):
        out.p[y] = list(img.p[y])
    return out


# ---------------------------------------------------------------- variant A: the router as a block

def shade(c, f):
    if c[3] == 0:
        return c
    return (G.cl(c[0] * f), G.cl(c[1] * f), G.cl(c[2] * f), c[3])


def iso_block(dst, top, left, right, cx, cy, k=2, tall=1.3, n=G.N):
    """A block in two-to-one isometric, one texel every k pixels: U = (k, k/2) right, V = (-k, k/2) left, down = k*tall.

    Straight 2:1 gives a cube exactly as wide as it is high, which reads as a squashed box - true isometric is about
    15% taller than it is wide, so the vertical axis gets stretched by `tall` and the side textures with it."""
    def put(x, y, c, h):
        if c[3] == 0:
            return
        dst.rect(int(x), int(y), k, h, c)

    half = n * k // 2
    for v in range(n):                                   # top face, back to front so nearer texels win
        for u in range(n):
            put(cx + (u - v) * k, cy + (u + v) * k * 0.5 - half, shade(top.p[v][u], 1.0), k)
    base = cy + half                                     # y of the top face's front corner
    step = k * tall
    for h in range(n):                                   # left face: the panel, the side you read
        for t in range(n):
            put(cx - n * k + t * k, base - (n - t) * k * 0.5 + h * step, shade(left.p[h][t], 0.80), int(step) + 1)
    for h in range(n):                                   # right face
        for t in range(n):
            put(cx + t * k, base - t * k * 0.5 + h * step, shade(right.p[h][n - 1 - t], 0.62), int(step) + 1)


TONES = [0, 1, 3, 5, 7, 8, 8, 8, 7, 6, 5, 4, 3, 2, 1]    # a barrel across the band: lit side, shadow side


def band(dst, cx, top, color, across):
    """One slice of conduit: the metal barrel plus the type-coloured line down its middle."""
    r = ramp9(0x4d5663)
    c = rgb(color)
    n = len(TONES)
    mid = n // 2
    for k in range(n + 1):                               # one pixel of overlap closes the diagonal stair
        t = r[TONES[min(k, n - 1)]]
        dst.px(cx + k, top, t) if across else dst.px(cx, top + k, t)
    for k, col in ((mid - 1, lt(c, .45)), (mid, mix(c, (0, 0, 0, 255), .18)), (mid + 1, mix(c, (0, 0, 0, 255), .40))):
        dst.px(cx + k, top, col) if across else dst.px(cx, top + k, col)


def iso_pipe(dst, x, y, dx, dy, steps, color):
    """A conduit along an isometric axis: one shaded slice per step, so the band slopes with the block.
    A run along X is sliced vertically; a run straight down the screen is sliced across."""
    for s in range(steps):
        if dx:
            band(dst, int(x + dx * s), int(y + dy * s), color, False)
        else:
            band(dst, int(x), int(y + s), color, True)


def flange(dst, x, y, w, h):
    """The coupling where a conduit meets a face."""
    for j in range(h):
        for k in range(w):
            dst.px(x + k, y + j, GM[5 if j < 1 or k < 1 else (0 if j > h - 2 else 3)])


def variant_a(tex):
    i = Img(S, S)
    for y in range(S):
        i.rect(0, y, S, 1, mix(rgb(0x232b38), rgb(0x0b0e14), (y / (S - 1.0)) ** 0.85))
    acc = rgb(MACHINE_ACCENT["router"])
    for k in range(60, 0, -1):                           # the router's own glow, behind everything
        t = k / 60.0
        i.rect(160 - k * 2, 160 - k, k * 4, k * 2, mix(i.p[160 - k][160], acc, 0.05 * (1 - t)))
    for y in range(-18, 19):                             # the block sits on something, so it casts on something
        for x in range(-78, 79):
            d = (x / 76.0) ** 2 + (y / 17.0) ** 2
            if d < 1:
                i.blend(160 + x, 240 + y, (0, 0, 0, 255), 0.5 * (1 - d))
    for y in range(S):                                   # corners fall away, the middle stays lit
        for x in range(S):
            d = ((x - 160) / 186.0) ** 2 + ((y - 158) / 186.0) ** 2
            if d > 0.45:
                i.blend(x, y, (0, 0, 0, 255), min(0.5, (d - 0.45) * 0.7))

    iso_pipe(i, 132, 152, -1, 0.5, 132, TYPE_COLOR["item"])   # items out to the lower left
    iso_pipe(i, 188, 152, 1, 0.5, 132, TYPE_COLOR["fluid"])   # fluid out to the lower right
    iso_block(i, frame0(tex['block/router_top.png']), frame0(tex['block/router_panel.png']),
              frame0(tex['block/router_side.png']), 160, 118)
    iso_pipe(i, 153, 0, 0, 1, 98, TYPE_COLOR["energy"])       # FE down onto the back of the top face
    flange(i, 151, 94, 20, 5)
    i.outline(0, 0, S, S, rgb(0x05070b))
    return i


tex = textures()
write(upscale(variant_a(tex), SCALE), [JAR, os.path.join(PRESS, 'logo.png')])

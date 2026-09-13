"""Renders the mod's textures as contact sheets so they can be judged without launching the game.
Machines and conduits are drawn as isometric blocks (Minecraft face shading: top 1.0, left 0.8, right 0.6) using the same
UV rules as the block models; items and GUI sheets are shown flat. Needs Pillow.

Run: python tools/preview_textures.py [--textures <assets/omnilogistics/textures dir>] [--out <dir>]
Writes <out>/blocks.png, <out>/items.png, <out>/items_small.png (inventory scale), <out>/gui.png, <out>/flat_blocks.png
"""
import argparse
import os

from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_TEX = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'omnilogistics', 'textures')
BG = (28, 30, 34, 255)
FONT = ImageFont.load_default()

TYPES = ["item", "energy", "fluid"]
TIERS = ["basic", "advanced", "elite", "ultimate", "infinity"]
MACHINES = ["router", "extractor", "exposer", "miner_elite"]


def load(root, rel):
    return Image.open(os.path.join(root, rel)).convert('RGBA')


def shade(img, f):
    r, g, b, a = img.split()
    return Image.merge('RGBA', (r.point(lambda v: int(v * f)), g.point(lambda v: int(v * f)), b.point(lambda v: int(v * f)), a))


def first_frame(img):
    """Animated textures are vertical strips of square frames; show frame 0."""
    return img.crop((0, 0, img.width, img.width)) if img.height > img.width else img


def uv(img, u0, v0, u1, v1):
    """Crop by model UV (0..16 units) regardless of the texture's pixel size."""
    s = img.width / 16
    return img.crop((int(u0 * s), int(v0 * s), int(u1 * s), int(v1 * s)))


def iso_box(top, left, right, k):
    """
    Isometric box from three face images (top: w x d texels, left: w x h, right: d x h). k = pixels per texel.
    Output: width (w + d) * k, height (w + d) * k / 2 + h * k.
    """
    w, d = top.size
    h = left.height
    W = int((w + d) * k)
    H = int((w + d) * k / 2 + h * k)
    out = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    # top rhombus: (u,v) -> x = (u + (d - v)) * k ... use a right-handed layout: left edge = v axis, right edge = u axis
    # forward: x = (u + v) * k, y = (d - v + u) * k / 2  -> inverse for Image.transform (output -> input)
    # u = (x/k + 2y/k - d) / 2 ; v = (x/k - 2y/k + d) / 2
    a = 1 / (2 * k); b = 1 / k; c = -d / 2
    dd = 1 / (2 * k); e = -1 / k; f = d / 2
    top_t = top.transform((W, H), Image.AFFINE, (a, b, c, dd, e, f), resample=Image.NEAREST)
    # left face: x = v * k, y = (d - v) * k / 2 + t * k  (t = 0..h down)  -> v = x / k, t = (y - (d - x/k) * k / 2) / k
    left_t = left.transform((W, H), Image.AFFINE, (1 / k, 0, 0, 0.5 / k, 1 / k, -d / 2), resample=Image.NEAREST)
    # right face: x = (d + u) * k, y = u * k / 2 + t * k -> u = x / k - d, t = (y - (x/k - d) * k / 2) / k
    right_t = right.transform((W, H), Image.AFFINE, (1 / k, 0, -d, -0.5 / k, 1 / k, d / 2), resample=Image.NEAREST)
    for face, fac in ((left_t, 0.8), (right_t, 0.6), (top_t, 1.0)):
        face = shade(face, fac)
        out.alpha_composite(face)
    return out


def machine_iso(root, kind, k=6):
    top = load(root, f'block/{kind}_top.png')
    side = load(root, f'block/{kind}_side.png')
    panel = first_frame(load(root, f'block/{kind}_panel.png'))
    # the model puts a 10x10 panel (uv 0..16 of the panel texture) at 3..13 on every side face
    side = side.copy()
    p = panel.resize((int(side.width * 10 / 16), int(side.height * 10 / 16)), Image.NEAREST)
    side.alpha_composite(p, (int(side.width * 3 / 16), int(side.height * 3 / 16)))
    return iso_box(top, side, side, k / (top.width / 16))


def pipe_strip(tex, k):
    """Top/left face of a straight pipe (16 long x 6 wide): arm | centre | arm, following pipe_arm / pipe_center UVs."""
    arm = uv(tex, 5, 0, 11, 5)            # 6 wide (across), 5 long
    arm_h = arm.rotate(90, expand=True)   # 5 long x 6 wide -> length along x
    center = uv(tex, 5, 0, 11, 6).rotate(90, expand=True)   # a straight run shows the strip on the centre too
    s = tex.width / 16
    strip = Image.new('RGBA', (int(16 * s), int(6 * s)))
    strip.paste(arm_h, (0, 0))
    strip.paste(center, (int(5 * s), 0))
    strip.paste(arm_h.transpose(Image.FLIP_LEFT_RIGHT), (int(11 * s), 0))
    return strip


def pipe_iso(root, t, tier, k=6):
    tex = load(root, f'block/pipe_{t}_{tier}.png')
    strip = pipe_strip(tex, k)
    cap = uv(tex, 5, 6, 11, 12)
    return iso_box(strip, strip, cap, k / (tex.width / 16))


def plate_iso(root, t, tier, suffix, k=6):
    """Connector plate (8x8x2) plus a short arm behind it, as on an IN / OUT face."""
    tex = load(root, f'block/pipe_{t}_{tier}{suffix}.png')
    s = tex.width / 16
    face = uv(tex, 4, 4, 12, 12)
    edge = uv(tex, 4, 14, 12, 16).rotate(90, expand=True)   # the plate rim strip, matching plate_els() side uv
    top = Image.new('RGBA', (int(2 * s), int(8 * s)))
    top.paste(edge, (0, 0))
    return iso_box(top.transpose(Image.TRANSPOSE), edge.transpose(Image.TRANSPOSE), face, k / s)


def label(draw, x, y, text):
    draw.text((x, y), text, fill=(210, 215, 222, 255), font=FONT)


def sheet_blocks(root, out):
    cell = 6 * 32 + 40
    cols = 6
    tiles = []
    for m in MACHINES:
        tiles.append((m, machine_iso(root, m)))
    for t in TYPES:
        for tier in TIERS:
            tiles.append((f'{t} {tier}', pipe_iso(root, t, tier)))
    for t in TYPES:
        tiles.append((f'{t} IN plate', plate_iso(root, t, 'basic', '_pull')))
        tiles.append((f'{t} OUT plate', plate_iso(root, t, 'basic', '_push')))
    rows = (len(tiles) + cols - 1) // cols
    img = Image.new('RGBA', (cols * cell, rows * cell), BG)
    d = ImageDraw.Draw(img)
    for i, (name, tile) in enumerate(tiles):
        x, y = (i % cols) * cell, (i // cols) * cell
        tile.thumbnail((cell - 20, cell - 30), Image.NEAREST)
        img.alpha_composite(tile, (x + 10, y + 8))
        label(d, x + 10, y + cell - 18, name)
    img.save(os.path.join(out, 'blocks.png'))


def sheet_flat(root, sub, names, out_name, out, scale=6, cols=8):
    cell_w = 32 * scale + 12
    cell_h = 32 * scale + 26
    rows = (len(names) + cols - 1) // cols
    img = Image.new('RGBA', (cols * cell_w, rows * cell_h), BG)
    d = ImageDraw.Draw(img)
    for i, n in enumerate(names):
        x, y = (i % cols) * cell_w, (i // cols) * cell_h
        t = first_frame(load(root, f'{sub}/{n}.png'))
        t = t.resize((32 * scale, 32 * scale), Image.NEAREST)
        img.alpha_composite(t, (x + 6, y + 4))
        label(d, x + 6, y + 32 * scale + 8, n)
    img.save(os.path.join(out, out_name))


def sheet_gui(root, out, scale=2):
    names = [n[:-4] for n in sorted(os.listdir(os.path.join(root, 'gui'))) if n.endswith('.png')]
    imgs = [load(root, f'gui/{n}.png') for n in names]
    W = sum(i.width * scale + 20 for i in imgs)
    H = max(i.height * scale for i in imgs) + 30
    img = Image.new('RGBA', (W, H), BG)
    d = ImageDraw.Draw(img)
    x = 10
    for n, i in zip(names, imgs):
        img.alpha_composite(i.resize((i.width * scale, i.height * scale), Image.NEAREST), (x, 20))
        label(d, x, 4, n)
        x += i.width * scale + 20
    img.save(os.path.join(out, 'gui.png'))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--textures', default=DEFAULT_TEX)
    ap.add_argument('--out', default=os.path.join(HERE, '..', 'build', 'texture_preview'))
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    root = a.textures
    sheet_blocks(root, a.out)
    blocks = sorted(n[:-4] for n in os.listdir(os.path.join(root, 'block')) if n.endswith('.png'))
    items = sorted(n[:-4] for n in os.listdir(os.path.join(root, 'item')) if n.endswith('.png'))
    sheet_flat(root, 'block', blocks, 'flat_blocks.png', a.out)
    sheet_flat(root, 'item', items, 'items.png', a.out)
    sheet_flat(root, 'item', items, 'items_small.png', a.out, scale=2, cols=10)   # roughly inventory-slot size
    sheet_gui(root, a.out)
    print('previews written to', a.out)


if __name__ == '__main__':
    main()

"""Renders GUI layout previews (texture + slots + buttons + label boxes) so layouts can be checked without launching the game.
Run: python tools/preview_gui.py <outdir>
"""
import os
import struct
import sys
import zlib

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
TEX = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'omnilogistics', 'textures', 'gui')


def read_png(path):
    with open(path, 'rb') as f:
        data = f.read()
    assert data[:8] == b'\x89PNG\r\n\x1a\n'
    pos, w, h, idat = 8, 0, 0, b''
    while pos < len(data):
        ln = struct.unpack('>I', data[pos:pos + 4])[0]
        typ = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + ln]
        if typ == b'IHDR':
            w, h = struct.unpack('>II', body[:8])
        elif typ == b'IDAT':
            idat += body
        pos += 12 + ln
    raw = zlib.decompress(idat)
    rows, stride = [], w * 4
    prev = bytearray(stride)
    p = 0
    for _ in range(h):
        ft = raw[p]
        line = bytearray(raw[p + 1:p + 1 + stride])
        p += 1 + stride
        for i in range(stride):
            a = line[i - 4] if i >= 4 else 0
            b = prev[i]
            c = prev[i - 4] if i >= 4 else 0
            if ft == 1: line[i] = (line[i] + a) & 255
            elif ft == 2: line[i] = (line[i] + b) & 255
            elif ft == 3: line[i] = (line[i] + (a + b) // 2) & 255
            elif ft == 4:
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                pr = a if pa <= pb and pa <= pc else b if pb <= pc else c
                line[i] = (line[i] + pr) & 255
        rows.append([tuple(line[i:i + 4]) for i in range(0, stride, 4)])
        prev = line
    return w, h, rows


class Canvas:
    def __init__(self, w, h, rows, scale=3):
        self.s = scale
        self.w, self.h = w * scale, h * scale
        self.p = [[rows[y // scale][x // scale] for x in range(self.w)] for y in range(self.h)]

    def rect(self, x, y, w, h, c, fill=False):
        s = self.s
        for yy in range(y * s, (y + h) * s):
            for xx in range(x * s, (x + w) * s):
                edge = xx < x * s + s or xx >= (x + w) * s - s or yy < y * s + s or yy >= (y + h) * s - s
                if fill or edge:
                    if 0 <= xx < self.w and 0 <= yy < self.h:
                        self.p[yy][xx] = c

    def label(self, x, y, text, c=(230, 237, 243, 255)):
        self.rect(x, y, 6 * len(text), 8, c, fill=False)

    def save(self, path):
        raw = b''.join(b'\x00' + b''.join(struct.pack('BBBB', *px) for px in row) for row in self.p)

        def chunk(t, d):
            return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)

        with open(path, 'wb') as f:
            f.write(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', self.w, self.h, 8, 6, 0, 0, 0))
                    + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))


SLOT = (255, 255, 255, 255)
BTN = (255, 160, 40, 255)
LBL = (60, 255, 120, 255)
BAR = (63, 211, 255, 255)


def crop(name, w, h):
    tw, th, rows = read_png(os.path.join(TEX, name + '.png'))
    return Canvas(w, h, [r[:w] for r in rows[:h]])


def filter_preview(out):
    c = crop('filter', 256, 242)
    c.rect(119, 21, 18, 18, SLOT)
    for i in range(6): c.rect(8 + i * 40, 42, 38, 18, BTN)
    c.rect(176, 21, 72, 18, BTN)
    for i in range(9): c.rect(8 + (i % 3) * 82, 64 + (i // 3) * 22, 78, 18, BTN)
    c.rect(8, 130, 120, 18, BTN); c.rect(130, 130, 118, 18, BTN)
    c.label(8, 6, "Title"); c.label(8, 26, "Filter"); c.label(47, 152, "Inventory")
    for r in range(3):
        for col in range(9): c.rect(46 + col * 18, 161 + r * 18, 18, 18, SLOT)
    for col in range(9): c.rect(46 + col * 18, 219, 18, 18, SLOT)
    c.save(os.path.join(out, 'preview_filter.png'))


def pipe_preview(out):
    c = crop('pipe', 176, 246)
    for i in range(6):
        c.rect(7, 19 + i * 18, 18, 18, SLOT); c.label(28, 20 + i * 18, "North"); c.label(28, 29 + i * 18, "Chest"); c.rect(100, 19 + i * 18, 68, 16, BTN)
    c.rect(7, 131, 18, 18, SLOT); c.rect(27, 131, 18, 18, SLOT); c.rect(96, 131, 72, 16, BTN)
    c.label(8, 6, "Basic Item Pipe"); c.label(8, 154, "Inventory")
    for r in range(3):
        for col in range(9): c.rect(7 + col * 18, 163 + r * 18, 18, 18, SLOT)
    for col in range(9): c.rect(7 + col * 18, 221, 18, 18, SLOT)
    c.save(os.path.join(out, 'preview_pipe.png'))


def router_preview(out):
    c = crop('router', 176, 180)
    for i in range(8): c.rect(7 + i * 18, 23, 18, 18, SLOT)
    c.rect(7, 61, 18, 18, SLOT); c.rect(43, 61, 18, 18, SLOT); c.rect(61, 61, 18, 18, SLOT)
    c.rect(96, 51, 31, 14, BAR); c.label(128, 54, "t"); c.label(136, 54, "R 64")
    c.rect(90, 68, 78, 8, BAR)
    c.label(8, 6, "Wireless Router"); c.label(8, 42, "right-click a card: configure"); c.label(8, 52, "Buffer"); c.label(44, 52, "Upgrades")
    c.label(90, 78, "Tank: 0 mB"); c.label(8, 88, "Inventory")
    for r in range(3):
        for col in range(9): c.rect(7 + col * 18, 97 + r * 18, 18, 18, SLOT)
    for col in range(9): c.rect(7 + col * 18, 155, 18, 18, SLOT)
    c.save(os.path.join(out, 'preview_router.png'))


def extractor_preview(out):
    c = crop('extractor', 176, 208)
    for x, y in ((26, 20), (26, 42), (26, 64), (62, 42), (98, 20), (116, 20), (98, 42), (116, 42), (62, 96), (80, 96), (98, 96), (116, 96)):
        c.rect(x - 1, y - 1, 18, 18, SLOT)
    c.rect(8, 20, 8, 86, BAR)
    for lane in range(3): c.rect(45, 20 + lane * 22, 3, 16, BAR)
    c.rect(98, 66, 52, 16, BTN); c.rect(61, 65, 28, 14, BAR); c.label(89, 68, "t")
    c.label(8, 6, "Component Extractor"); c.label(62, 86, "Upgrades"); c.label(56, 31, "Books"); c.label(8, 116, "Inventory")
    for r in range(3):
        for col in range(9): c.rect(7 + col * 18, 125 + r * 18, 18, 18, SLOT)
    for col in range(9): c.rect(7 + col * 18, 183, 18, 18, SLOT)
    c.save(os.path.join(out, 'preview_extractor.png'))


if __name__ == '__main__':
    out = sys.argv[1] if len(sys.argv) > 1 else '.'
    os.makedirs(out, exist_ok=True)
    filter_preview(out); router_preview(out); extractor_preview(out); pipe_preview(out)
    print('previews written to', out)

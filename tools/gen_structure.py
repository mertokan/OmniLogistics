"""Writes the empty 3x3x3 structure template used by the GameTests (data/omnilogistics/structure/empty.nbt)."""
import gzip, os, struct

def s(v): b = v.encode(); return struct.pack('>H', len(b)) + b
def tag(t, name, payload): return bytes([t]) + s(name) + payload
def tint(v): return struct.pack('>i', v)
def tlist(elem_type, items): return bytes([elem_type]) + struct.pack('>i', len(items)) + b''.join(items)
def compound(*tags): return b''.join(tags) + b'\x00'

root = tag(10, '', compound(
    tag(9, 'size', tlist(3, [tint(3), tint(3), tint(3)])),
    tag(9, 'palette', tlist(10, [compound(tag(8, 'Name', s('minecraft:air')))])),
    tag(9, 'blocks', tlist(10, [compound(tag(9, 'pos', tlist(3, [tint(x), tint(y), tint(z)])), tag(3, 'state', tint(0)))
                                 for x in range(3) for y in range(3) for z in range(3)])),
    tag(9, 'entities', tlist(0, [])),
    tag(3, 'DataVersion', tint(3955)),
))
p = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'data', 'omnilogistics', 'structure', 'empty.nbt')
os.makedirs(os.path.dirname(p), exist_ok=True)
with gzip.open(p, 'wb') as f: f.write(root)
print('wrote', p)

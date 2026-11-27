#!/usr/bin/env python3
"""Writes an all-air structure template (gzipped NBT) for GameTests."""
import gzip, struct, sys

def tag_string(s):
    b = s.encode('utf-8'); return struct.pack('>H', len(b)) + b

def named(tag_id, name, payload):
    return bytes([tag_id]) + tag_string(name) + payload

def tag_int(name, v): return named(3, name, struct.pack('>i', v))

def tag_list_int(name, values):
    return named(9, name, bytes([3]) + struct.pack('>i', len(values)) + b''.join(struct.pack('>i', v) for v in values))

def tag_list_empty(name, elem_type=10):
    return named(9, name, bytes([elem_type]) + struct.pack('>i', 0))

out, sx, sy, sz = sys.argv[1], int(sys.argv[2]), int(sys.argv[3]), int(sys.argv[4])
root = (tag_int('DataVersion', 3955) + tag_list_int('size', [sx, sy, sz])
        + tag_list_empty('palette') + tag_list_empty('blocks') + tag_list_empty('entities') + b'\x00')
data = bytes([10]) + tag_string('') + root
with gzip.open(out, 'wb') as fh:
    fh.write(data)
print('wrote', out)

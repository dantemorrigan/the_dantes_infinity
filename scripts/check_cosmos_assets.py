"""Fail the build on truncated PNGs or broken particle references (stdlib only)."""
import json
import struct
import zlib
from pathlib import Path

assets = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/infinity_gauntlet'
images = list((assets / 'textures/environment/cosmos').rglob('*.png'))
images += list((assets / 'textures/particle').glob('cosmos_*.png'))
assert len(images) == 30
for path in images:
    data = path.read_bytes()
    assert data[:8] == b'\x89PNG\r\n\x1a\n', path
    offset, ended, compressed = 8, False, bytearray()
    while offset < len(data):
        assert offset + 12 <= len(data), (path, 'truncated header')
        length = struct.unpack_from('>I', data, offset)[0]
        assert offset + length + 12 <= len(data), (path, 'truncated chunk')
        tag = data[offset+4:offset+8]
        payload = data[offset+8:offset+8+length]
        crc = struct.unpack_from('>I', data, offset+8+length)[0]
        assert zlib.crc32(tag+payload) == crc, (path, 'bad CRC')
        if tag == b'IDAT': compressed.extend(payload)
        if tag == b'IEND': ended = True
        offset += length + 12
    assert ended and compressed, (path, 'missing end/data')
    zlib.decompress(compressed)
for path in (assets / 'particles').glob('cosmos_*.json'):
    refs = json.loads(path.read_text())['textures']
    assert len(refs) == 4, path
    for ref in refs:
        assert (assets / 'textures/particle' / (ref.split(':', 1)[1] + '.png')).is_file(), ref
print('Validated 30 cosmos PNGs and all particle references')

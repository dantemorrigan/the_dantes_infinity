"""Reject coplanar overlapping faces in one animated bone (visible depth fighting)."""
import json
from pathlib import Path

path = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/infinity_gauntlet/tribunal/tribunal.geo.json'
bones = json.loads(path.read_text())['minecraft:geometry'][0]['bones']
for bone in bones:
    faces = {}
    for index, cube in enumerate(bone.get('cubes', [])):
        origin = cube['origin']
        end = [a + b for a, b in zip(origin, cube['size'])]
        for axis in range(3):
            other = [i for i in range(3) if i != axis]
            bounds = [(origin[i], end[i]) for i in other]
            for plane in [origin[axis], end[axis]]:
                key = (axis, round(plane, 5))
                for previous, rectangle in faces.get(key, []):
                    if all(min(a[1], b[1]) - max(a[0], b[0]) > .001 for a, b in zip(bounds, rectangle)):
                        raise AssertionError(f'{bone["name"]}: coplanar overlapping cubes {previous}/{index}')
                faces.setdefault(key, []).append((index, bounds))
print('Tribunal mesh: no overlapping coplanar faces within animated bones')

# Parent/child hips are separate bones but share rest-pose world planes.
hip_faces={}
for bone in bones:
    if bone['name'] not in ('body','right_leg','left_leg'): continue
    for index,cube in enumerate(bone.get('cubes',[])):
        origin=cube['origin'];end=[a+b for a,b in zip(origin,cube['size'])]
        if end[1]<28: continue
        for axis in range(3):
            bounds=[(origin[i],end[i]) for i in range(3) if i!=axis]
            for plane in (origin[axis],end[axis]):
                key=(axis,round(plane,5))
                for name,previous,rectangle in hip_faces.get(key,[]):
                    if name!=bone['name'] and all(min(a[1],b[1])-max(a[0],b[0])>.001 for a,b in zip(bounds,rectangle)):
                        raise AssertionError(f'Hip seam: {name}/{previous} overlaps {bone["name"]}/{index}')
                hip_faces.setdefault(key,[]).append((bone['name'],index,bounds))
print('Tribunal hips: no parent/leg coplanar overlap')

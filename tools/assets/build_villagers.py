#!/usr/bin/env python3
"""Builds BranchWorld villager GLBs from KayKit Adventurers bodies and KayKit Rig_Medium animation clips.

Each output keeps one textured body, removes combat props, and embeds only the clips the village renderer uses,
renamed to short stable names. The rig and body share bone names, so channels are retargeted by node name.

Usage: python3 -I tools/assets/build_villagers.py <repo root>
"""
import json
import struct
import sys
from pathlib import Path

BODIES = {
    "villager_ranger.glb": ("Ranger.glb", {"Ranger_Quiver"}),
    "villager_rogue.glb": ("Rogue.glb", set()),
    "villager_barbarian.glb": ("Barbarian.glb", {"Barbarian_BearHat"}),
}

# Output clip name -> (rig file, source clip)
CLIPS = {
    "idle": ("Rig_Medium_General.glb", "Idle_A"),
    "walk": ("Rig_Medium_MovementBasic.glb", "Walking_A"),
    "carry": ("Rig_Medium_MovementBasic.glb", "Walking_C"),
    "chop": ("Rig_Medium_Tools.glb", "Chopping"),
    "dig": ("Rig_Medium_Tools.glb", "Digging"),
    "hammer": ("Rig_Medium_Tools.glb", "Hammering"),
    "pick": ("Rig_Medium_Tools.glb", "Pickaxing"),
    "gather": ("Rig_Medium_Tools.glb", "Working_A"),
    "sit": ("Rig_Medium_Simulation.glb", "Sit_Floor_Idle"),
    "wave": ("Rig_Medium_Simulation.glb", "Waving"),
}

COMPONENTS = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4, "MAT4": 16}
SIZES = {5120: 1, 5121: 1, 5122: 2, 5123: 2, 5125: 4, 5126: 4}


def read_glb(path: Path):
    data = path.read_bytes()
    magic, version, _ = struct.unpack_from("<III", data, 0)
    if magic != 0x46546C67 or version != 2:
        raise ValueError(f"{path} is not a glTF 2 binary")
    offset = 12
    document, binary = None, b""
    while offset < len(data):
        length, kind = struct.unpack_from("<II", data, offset)
        chunk = data[offset + 8: offset + 8 + length]
        if kind == 0x4E4F534A:
            document = json.loads(chunk)
        elif kind == 0x004E4942:
            binary = chunk
        offset += 8 + length
    return document, bytearray(binary)


def write_glb(path: Path, document, binary: bytearray):
    document["buffers"] = [{"byteLength": len(binary)}]
    encoded = json.dumps(document, separators=(",", ":")).encode()
    encoded += b" " * (-len(encoded) % 4)
    binary += b"\0" * (-len(binary) % 4)
    total = 12 + 8 + len(encoded) + 8 + len(binary)
    with path.open("wb") as out:
        out.write(struct.pack("<III", 0x46546C67, 2, total))
        out.write(struct.pack("<II", len(encoded), 0x4E4F534A))
        out.write(encoded)
        out.write(struct.pack("<II", len(binary), 0x004E4942))
        out.write(binary)


def copy_accessor(source_doc, source_bin, index, target_doc, target_bin) -> int:
    accessor = dict(source_doc["accessors"][index])
    view = source_doc["bufferViews"][accessor["bufferView"]]
    element = COMPONENTS[accessor["type"]] * SIZES[accessor["componentType"]]
    stride = view.get("byteStride", element)
    start = view.get("byteOffset", 0) + accessor.get("byteOffset", 0)
    packed = bytearray()
    for item in range(accessor["count"]):
        packed += source_bin[start + item * stride: start + item * stride + element]
    target_bin += b"\0" * (-len(target_bin) % 4)
    target_doc["bufferViews"].append({"buffer": 0, "byteOffset": len(target_bin), "byteLength": len(packed)})
    target_bin += packed
    accessor["bufferView"] = len(target_doc["bufferViews"]) - 1
    accessor.pop("byteOffset", None)
    target_doc["accessors"].append(accessor)
    return len(target_doc["accessors"]) - 1


def build(root: Path):
    bodies = root / "assets/characters/kaykit-adventurers/Characters/gltf"
    rigs = root / "assets/characters/kaykit-character-animations/Animations/gltf/Rig_Medium"
    output = root / "renderer/src/commonMain/resources/diorama"
    loaded = {}
    for name, (body_file, stripped) in BODIES.items():
        document, binary = read_glb(bodies / body_file)
        nodes = {node.get("name"): index for index, node in enumerate(document["nodes"])}
        for node in document["nodes"]:
            if node.get("name") in stripped:
                node.pop("mesh", None)
        document["animations"] = []
        for clip, (rig_file, source_name) in CLIPS.items():
            rig_doc, rig_bin = loaded.setdefault(rig_file, read_glb(rigs / rig_file))
            animation = next(a for a in rig_doc["animations"] if a["name"] == source_name)
            samplers, channels, remap = [], [], {}
            for channel in animation["channels"]:
                target = rig_doc["nodes"][channel["target"]["node"]].get("name")
                if target not in nodes:
                    continue
                sampler_index = channel["sampler"]
                if sampler_index not in remap:
                    sampler = animation["samplers"][sampler_index]
                    remap[sampler_index] = len(samplers)
                    samplers.append({
                        "input": copy_accessor(rig_doc, rig_bin, sampler["input"], document, binary),
                        "output": copy_accessor(rig_doc, rig_bin, sampler["output"], document, binary),
                        "interpolation": sampler.get("interpolation", "LINEAR"),
                    })
                channels.append({"sampler": remap[sampler_index],
                                 "target": {"node": nodes[target], "path": channel["target"]["path"]}})
            if not channels:
                raise ValueError(f"{source_name} has no channels for {body_file}")
            document["animations"].append({"name": clip, "samplers": samplers, "channels": channels})
        write_glb(output / name, document, binary)
        print(f"{name}: {len(document['animations'])} clips, {(output / name).stat().st_size // 1024} KB")


if __name__ == "__main__":
    build(Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve())

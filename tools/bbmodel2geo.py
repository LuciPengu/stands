#!/usr/bin/env python3
"""Convert the stand .bbmodel files into GeckoLib resources for the Fabric mod.

For each <models_dir>/<id>.bbmodel writes, under <assets_dir> (…/assets/stands):
  geckolib/models/stand/<id>.geo.json         bones + cubes (Bedrock geometry 1.12.0)
  geckolib/animations/stand/<id>.animation.json   idle, rush, punch, pose, throw, cast, dash, summon
  textures/entity/stand/<id>.png                the skin as the plugin shows it (set_opacity.py's see-through version)

Coordinate rules follow Blockbench's own Bedrock exporter: X is mirrored for cubes and pivots, bone rotations
become [-x, -y, z], and up/down face UVs are flipped. Keyframe values in a .bbmodel are already Bedrock-space,
so they are copied as-is.

Usage: bbmodel2geo.py <models_dir> <assets_dir>
"""
import base64, json, sys
from pathlib import Path

PNG = "data:image/png;base64,"
LOOP = {"loop": True, "once": False, "hold": "hold_on_last_frame"}


def num(v):
    try:
        return round(float(v), 4)
    except (TypeError, ValueError):
        return 0.0


def cube(el):
    f, t = el["from"], el["to"]
    out = {
        "origin": [num(-t[0]), num(f[1]), num(f[2])],
        "size": [num(t[0] - f[0]), num(t[1] - f[1]), num(t[2] - f[2])],
    }
    if el.get("inflate"):
        out["inflate"] = num(el["inflate"])
    uv = {}
    for face, d in el["faces"].items():
        if d.get("texture") is None:
            continue
        u0, v0, u1, v1 = d["uv"]
        pos, size = [u0, v0], [u1 - u0, v1 - v0]
        if face in ("up", "down"):
            pos = [pos[0] + size[0], pos[1] + size[1]]
            size = [-size[0], -size[1]]
        entry = {"uv": [num(pos[0]), num(pos[1])], "uv_size": [num(size[0]), num(size[1])]}
        if d.get("rotation"):
            entry["uv_rotation"] = d["rotation"]
        uv[face] = entry
    out["uv"] = uv
    return out


def bones_of(model):
    els = {e["uuid"]: e for e in model["elements"]}
    bones, loose = [], []

    def walk(node, parent):
        o, r = node.get("origin", [0, 0, 0]), node.get("rotation", [0, 0, 0])
        b = {"name": node["name"], "pivot": [num(-o[0]), num(o[1]), num(o[2])]}
        if parent:
            b["parent"] = parent
        if any(abs(x) > 1e-6 for x in r):
            b["rotation"] = [num(-r[0]), num(-r[1]), num(r[2])]
        cubes = []
        bones.append(b)
        for c in node.get("children", []):
            if isinstance(c, str):
                if c in els and els[c].get("export", True):
                    cubes.append(cube(els[c]))
            else:
                walk(c, node["name"])
        if cubes:
            b["cubes"] = cubes

    for top in model["outliner"]:
        if isinstance(top, str):
            if top in els:
                loose.append(cube(els[top]))
        else:
            walk(top, None)
    if loose:
        bones.insert(0, {"name": "loose", "pivot": [0, 0, 0], "cubes": loose})
    return bones


def animations_of(model):
    out = {}
    for a in model.get("animations", []):
        bones = {}
        for an in a["animators"].values():
            if an.get("type", "bone") != "bone":
                continue
            ch = {}
            for k in sorted(an["keyframes"], key=lambda k: k["time"]):
                p = k["data_points"][0]
                ch.setdefault(k["channel"], {})[f"{num(k['time'])}"] = [num(p["x"]), num(p["y"]), num(p["z"])]
            if ch:
                bones[an["name"]] = ch
        out[a["name"]] = {"loop": LOOP.get(a.get("loop"), False), "animation_length": num(a["length"]), "bones": bones}
    return out


def main(models_dir, assets_dir):
    models, assets = Path(models_dir), Path(assets_dir)
    geo_dir = assets / "geckolib/models/stand"
    anim_dir = assets / "geckolib/animations/stand"
    tex_dir = assets / "textures/entity/stand"
    for d in (geo_dir, anim_dir, tex_dir):
        d.mkdir(parents=True, exist_ok=True)
    for f in sorted(models.glob("*.bbmodel")):
        m = json.loads(f.read_text())
        sid = f.stem
        res = m.get("resolution", {"width": 64, "height": 64})
        geo = {"format_version": "1.12.0", "minecraft:geometry": [{
            "description": {"identifier": f"geometry.{sid}", "texture_width": res["width"], "texture_height": res["height"],
                            "visible_bounds_width": 4, "visible_bounds_height": 4, "visible_bounds_offset": [0, 1.5, 0]},
            "bones": bones_of(m)}]}
        (geo_dir / f"{sid}.geo.json").write_text(json.dumps(geo, indent=1))
        (anim_dir / f"{sid}.animation.json").write_text(json.dumps({"format_version": "1.8.0", "animations": animations_of(m)}, indent=1))
        tex = m["textures"][0]
        src = tex["source"]
        (tex_dir / f"{sid}.png").write_bytes(base64.b64decode(src[len(PNG):]))
        print(f"{sid}: {len(geo['minecraft:geometry'][0]['bones'])} bones, {len(m.get('animations', []))} animations")


if __name__ == "__main__":
    main(*sys.argv[1:3])

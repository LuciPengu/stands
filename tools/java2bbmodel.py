#!/usr/bin/env python3
"""Convert the mod's MCreator 1.14 Java entity models (decompiled) into Blockbench
.bbmodel files (free/generic format) with the original texture embedded, so
BetterModel can render them on vanilla clients via display entities.

Usage: java2bbmodel.py <entity_src_dir> <textures_dir> <out_dir>
"""
import base64, json, re, struct, sys, uuid
from pathlib import Path

F = r"(-?[\d.]+)F?"
RE_NEW = re.compile(r"this\.(\w+) = new RendererModel\(this\);")
RE_PIVOT = re.compile(rf"this\.(\w+)\.func_78793_a\({F}, {F}, {F}\);")
RE_ROT = re.compile(rf"this\.setRotationAngle\(this\.(\w+), {F}, {F}, {F}\);")
RE_CHILD = re.compile(r"this\.(\w+)\.func_78792_a\(this\.(\w+)\);")
RE_BOX = re.compile(
    rf"this\.(\w+)\.field_78804_l\.add\(new ModelBox\(this\.\w+, (\d+), (\d+), {F}, {F}, {F}, (\d+), (\d+), (\d+), {F}, (true|false)\)\);")
RE_TW = re.compile(r"field_78090_t = (\d+);")
RE_TH = re.compile(r"field_78089_u = (\d+);")
RE_TEX = re.compile(r'ResourceLocation\("jjbamodtst:textures/([^"]+\.png)"\)')
RE_ID = re.compile(r'func_206830_a\("(\w+)"\)|setRegistryName\("(\w+)"\)')
DEG = 57.29577951308232


def png_size(p: Path):
    with p.open("rb") as f:
        head = f.read(24)
    return struct.unpack(">II", head[16:24])


def uid():
    return str(uuid.uuid4())


def box_faces(u, v, w, h, d, mirror):
    """Blockbench box-UV layout (same as vanilla Java ModelBox)."""
    faces = {
        "east":  [u, v + d, u + d, v + d + h],
        "west":  [u + d + w, v + d, u + d + w + d, v + d + h],
        "up":    [u + d + w, v + d, u + d, v],
        "down":  [u + d + 2 * w, v, u + d + w, v + d],
        "south": [u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h],
        "north": [u + d, v + d, u + d + w, v + d + h],
    }
    if mirror:
        faces["east"], faces["west"] = faces["west"], faces["east"]
        for k, uv in faces.items():
            faces[k] = [uv[2], uv[1], uv[0], uv[3]]
    return {k: {"uv": uv, "texture": 0} for k, uv in faces.items()}


def convert(src: Path, tex_dir: Path, out_dir: Path):
    text = src.read_text()
    tex_m = RE_TEX.search(text)
    if not tex_m:
        return None
    bones, order, parent = {}, [], {}
    for line in text.splitlines():
        line = line.strip()
        if m := RE_NEW.match(line):
            bones[m[1]] = {"pivot": [0.0, 0.0, 0.0], "rot": [0.0, 0.0, 0.0], "boxes": []}
            order.append(m[1])
        elif m := RE_PIVOT.match(line):
            if m[1] in bones:
                bones[m[1]]["pivot"] = [float(m[2]), float(m[3]), float(m[4])]
        elif m := RE_ROT.match(line):
            bones[m[1]]["rot"] = [float(m[2]), float(m[3]), float(m[4])]
        elif m := RE_CHILD.match(line):
            parent[m[2]] = m[1]
        elif m := RE_BOX.match(line):
            g = m.groups()
            bones[g[0]]["boxes"].append(dict(
                u=int(g[1]), v=int(g[2]), x=float(g[3]), y=float(g[4]), z=float(g[5]),
                w=int(g[6]), h=int(g[7]), d=int(g[8]), inflate=float(g[9]), mirror=g[10] == "true"))
    if not bones:
        return None

    tw = int(RE_TW.search(text)[1]); th = int(RE_TH.search(text)[1])
    tex_file = tex_dir / tex_m[1]
    name = src.stem.replace("Entity", "").lower()

    # Absolute (unrotated) Java pivot = sum of pivots up the parent chain.
    def abs_pivot(b):
        p = bones[b]["pivot"]
        if b in parent:
            q = abs_pivot(parent[b])
            return [p[0] + q[0], p[1] + q[1], p[2] + q[2]]
        return p

    # Java model space (y down, rendered with scale(-1,-1,1), y=24 = feet)
    # -> Blockbench space: x' = -x, y' = 24 - y, z' = z ; rot' = (-rx, -ry, rz)
    to_bb = lambda p: [-p[0], 24 - p[1], p[2]]
    elements, groups = [], {}
    for b in order:
        ap = abs_pivot(b)
        rx, ry, rz = bones[b]["rot"]
        g = {"name": b, "origin": to_bb(ap),
             "rotation": [round(-rx * DEG, 4), round(-ry * DEG, 4), round(rz * DEG, 4)],
             "color": 0, "uuid": uid(), "export": True, "mirror_uv": False, "isOpen": True,
             "locked": False, "visibility": True, "autouv": 0, "children": []}
        for i, bx in enumerate(bones[b]["boxes"]):
            jx, jy, jz = ap[0] + bx["x"], ap[1] + bx["y"], ap[2] + bx["z"]
            frm = [-(jx + bx["w"]), 24 - (jy + bx["h"]), jz]
            to = [-jx, 24 - jy, jz + bx["d"]]
            el = {"name": f"{b}_{i}", "box_uv": False, "rescale": False, "locked": False,
                  "render_order": "default", "allow_mirror_modeling": True,
                  "from": frm, "to": to, "autouv": 0, "color": i % 8, "inflate": bx["inflate"],
                  "origin": to_bb(ap), "faces": box_faces(bx["u"], bx["v"], bx["w"], bx["h"], bx["d"], bx["mirror"]),
                  "type": "cube", "uuid": uid()}
            elements.append(el)
            g["children"].append(el["uuid"])
        groups[b] = g
    roots = []
    for b in order:
        if b in parent:
            groups[parent[b]]["children"].append(groups[b])
        else:
            roots.append(groups[b])

    pw, ph = png_size(tex_file)
    model = {
        "meta": {"format_version": "4.10", "model_format": "free", "box_uv": False},
        "name": name, "model_identifier": name, "visible_box": [1, 2, 0],
        "variable_placeholders": "", "variable_placeholder_buttons": [], "timeline_setups": [],
        "unhandled_root_fields": {},
        "resolution": {"width": tw, "height": th},
        "elements": elements, "outliner": roots,
        "textures": [{"path": "", "name": f"{name}.png", "folder": "block", "namespace": "", "id": "0",
                      "width": pw, "height": ph, "uv_width": tw, "uv_height": th,
                      "particle": False, "use_as_default": False, "layers_enabled": False,
                      "sync_to_project": "", "render_mode": "default", "render_sides": "auto",
                      "frame_time": 1, "frame_order_type": "loop", "frame_order": "", "frame_interpolate": False,
                      "visible": True, "internal": True, "saved": False, "uuid": uid(),
                      "source": "data:image/png;base64," + base64.b64encode(tex_file.read_bytes()).decode()}],
        "animations": [],
    }
    out = out_dir / f"{name}.bbmodel"
    out.write_text(json.dumps(model))
    return name, len(order), len(elements), f"{tw}x{th}", f"png {pw}x{ph}"


if __name__ == "__main__":
    src_dir, tex_dir, out_dir = map(Path, sys.argv[1:4])
    out_dir.mkdir(parents=True, exist_ok=True)
    for f in sorted(src_dir.glob("*Entity.java")):
        r = convert(f, tex_dir, out_dir)
        print(*(r or (f.name, "SKIPPED")), sep="\t")

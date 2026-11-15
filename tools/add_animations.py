#!/usr/bin/env python3
"""Add a shared animation set (idle, rush, punch, pose, summon) to every stand .bbmodel.

All humanoid stands share the same bone names, so one set fits all of them.
Aerosmith (single bone) gets plane versions with the same animation names.
Keyframe values are Blockbench offsets from the model's rest pose, in degrees / pixels.
Negative X on an arm = swing it forward.

Usage: add_animations.py <models_dir>      (idempotent: replaces the animations list)
"""
import json, sys, uuid
from pathlib import Path

uid = lambda: str(uuid.uuid4())


def kf(channel, t, x, y, z):
    return {"channel": channel, "data_points": [{"x": x, "y": y, "z": z}], "uuid": uid(),
            "time": t, "color": -1, "interpolation": "linear"}


def anim(name, loop, length, tracks, bones):
    """tracks: {bone: {channel: [(t, x, y, z), ...]}}"""
    animators = {}
    for bone, channels in tracks.items():
        if bone not in bones:
            continue
        keys = [kf(ch, *p) for ch, pts in channels.items() for p in pts]
        animators[bones[bone]] = {"name": bone, "type": "bone", "keyframes": keys}
    return {"uuid": uid(), "name": name, "loop": loop, "override": False, "length": length,
            "snapping": 20, "selected": False, "anim_time_update": "", "blend_weight": "",
            "start_delay": "", "loop_delay": "", "animators": animators}


R, P, S = "rotation", "position", "scale"

HUMANOID = [
    ("idle", "loop", 3.0, {
        "body2": {P: [(0, 0, 0, 0), (1.5, 0, 0.8, 0), (3, 0, 0, 0)], R: [(0, 0, 0, 0), (1.5, 2, 0, 0), (3, 0, 0, 0)]},
        "head": {R: [(0, 0, 0, 0), (0.75, -3, 4, 0), (2.25, 2, -4, 0), (3, 0, 0, 0)]},
        "leftArm2": {R: [(0, 0, 0, 0), (1.5, -4, 0, -3), (3, 0, 0, 0)]},
        "rightArm2": {R: [(0, 0, 0, 0), (1.5, 4, 0, 3), (3, 0, 0, 0)]},
        "leftLeg": {R: [(0, 0, 0, 0), (1.5, 4, 0, 0), (3, 0, 0, 0)]},
        "rightLeg2": {R: [(0, 0, 0, 0), (1.5, -3, 0, 0), (3, 0, 0, 0)]},
    }),
    # Fast alternating punches (ORA ORA / MUDA MUDA). 0.3s loop.
    ("rush", "loop", 0.3, {
        "body2": {R: [(0, -12, 10, 0), (0.15, -12, -10, 0), (0.3, -12, 10, 0)], P: [(0, 0, 0, -1), (0.3, 0, 0, -1)]},
        "rightArm2": {R: [(0, -75, 0, 0), (0.15, 25, 0, 0), (0.3, -75, 0, 0)]},
        "rightArm": {R: [(0, 60, 0, 0), (0.15, -20, 0, 0), (0.3, 60, 0, 0)]},
        "leftArm2": {R: [(0, 25, 0, 0), (0.15, -75, 0, 0), (0.3, 25, 0, 0)]},
        "leftArm": {R: [(0, -20, 0, 0), (0.15, 60, 0, 0), (0.3, -20, 0, 0)]},
        "head": {R: [(0, 8, -6, 0), (0.15, 8, 6, 0), (0.3, 8, -6, 0)]},
    }),
    # One heavy punch (finishers, single-hit skills).
    ("punch", "once", 0.6, {
        "body2": {R: [(0, 0, 0, 0), (0.15, 8, -25, 0), (0.3, -15, 25, 0), (0.6, 0, 0, 0)],
                  P: [(0, 0, 0, 0), (0.3, 0, 0, -3), (0.6, 0, 0, 0)]},
        "rightArm2": {R: [(0, 0, 0, 0), (0.15, 40, 0, 0), (0.3, -90, 0, 0), (0.45, -90, 0, 0), (0.6, 0, 0, 0)]},
        "rightArm": {R: [(0, 0, 0, 0), (0.15, -30, 0, 0), (0.3, 70, 0, 0), (0.45, 70, 0, 0), (0.6, 0, 0, 0)]},
        "leftArm2": {R: [(0, 0, 0, 0), (0.3, 30, 0, 0), (0.6, 0, 0, 0)]},
    }),
    # Dramatic pose for ultimates (time stop etc.): arms thrown wide, chest out.
    ("pose", "once", 1.4, {
        "body2": {R: [(0, 0, 0, 0), (0.25, 18, 0, 0), (1.1, 18, 0, 0), (1.4, 0, 0, 0)],
                  P: [(0, 0, 0, 0), (0.25, 0, 3, 0), (1.1, 0, 3, 0), (1.4, 0, 0, 0)]},
        "head": {R: [(0, 0, 0, 0), (0.25, 20, 0, 0), (1.1, 20, 0, 0), (1.4, 0, 0, 0)]},
        "leftArm2": {R: [(0, 0, 0, 0), (0.25, 20, 0, -75), (1.1, 20, 0, -75), (1.4, 0, 0, 0)]},
        "rightArm2": {R: [(0, 0, 0, 0), (0.25, 20, 0, 75), (1.1, 20, 0, 75), (1.4, 0, 0, 0)]},
        "leftArm": {R: [(0, 0, 0, 0), (0.25, 40, 0, 0), (1.1, 40, 0, 0), (1.4, 0, 0, 0)]},
        "rightArm": {R: [(0, 0, 0, 0), (0.25, 40, 0, 0), (1.1, 40, 0, 0), (1.4, 0, 0, 0)]},
    }),
    # Overhand throw (knives, blocks, sword tip).
    ("throw", "once", 0.5, {
        "body2": {R: [(0, 0, 0, 0), (0.15, 10, -20, 0), (0.3, -15, 20, 0), (0.5, 0, 0, 0)]},
        "rightArm2": {R: [(0, 0, 0, 0), (0.15, -170, 0, 0), (0.3, -40, 0, 0), (0.5, 0, 0, 0)]},
        "rightArm": {R: [(0, 0, 0, 0), (0.15, -40, 0, 0), (0.3, 60, 0, 0), (0.5, 0, 0, 0)]},
    }),
    # One arm held out (beams, heals, traps).
    ("cast", "once", 0.9, {
        "body2": {R: [(0, 0, 0, 0), (0.2, -6, 12, 0), (0.7, -6, 12, 0), (0.9, 0, 0, 0)]},
        "rightArm2": {R: [(0, 0, 0, 0), (0.2, -80, 0, 0), (0.7, -80, 0, 0), (0.9, 0, 0, 0)]},
        "rightArm": {R: [(0, 0, 0, 0), (0.2, 70, 0, 0), (0.7, 70, 0, 0), (0.9, 0, 0, 0)]},
        "head": {R: [(0, 0, 0, 0), (0.2, 8, 0, 0), (0.7, 8, 0, 0), (0.9, 0, 0, 0)]},
    }),
    # Lean in, arms swept back (dashes, teleports).
    ("dash", "once", 0.5, {
        "body2": {R: [(0, 0, 0, 0), (0.12, -35, 0, 0), (0.35, -35, 0, 0), (0.5, 0, 0, 0)],
                  P: [(0, 0, 0, 0), (0.12, 0, -1, -3), (0.35, 0, -1, -3), (0.5, 0, 0, 0)]},
        "leftArm2": {R: [(0, 0, 0, 0), (0.12, 70, 0, 0), (0.35, 70, 0, 0), (0.5, 0, 0, 0)]},
        "rightArm2": {R: [(0, 0, 0, 0), (0.12, 70, 0, 0), (0.35, 70, 0, 0), (0.5, 0, 0, 0)]},
        "head": {R: [(0, 0, 0, 0), (0.12, 25, 0, 0), (0.35, 25, 0, 0), (0.5, 0, 0, 0)]},
    }),
    # Pops out of the user.
    ("summon", "once", 0.4, {
        "body2": {S: [(0, 0.1, 0.1, 0.1), (0.25, 1.15, 1.15, 1.15), (0.4, 1, 1, 1)],
                  R: [(0, 0, 180, 0), (0.4, 0, 0, 0)]},
    }),
]

PLANE = [
    ("idle", "loop", 2.0, {"bb_main": {P: [(0, 0, 0, 0), (1, 0, 1.5, 0), (2, 0, 0, 0)],
                                       R: [(0, 0, 0, -6), (1, 0, 0, 6), (2, 0, 0, -6)]}}),
    ("rush", "loop", 0.2, {"bb_main": {R: [(0, -12, 0, -4), (0.1, -12, 0, 4), (0.2, -12, 0, -4)],
                                       P: [(0, 0, 0, 0), (0.1, 0, 0, 1), (0.2, 0, 0, 0)]}}),
    ("punch", "once", 0.6, {"bb_main": {R: [(0, 0, 0, 0), (0.3, -35, 0, 0), (0.6, 0, 0, 0)],
                                        P: [(0, 0, 0, 0), (0.3, 0, -3, -6), (0.6, 0, 0, 0)]}}),
    ("pose", "once", 1.4, {"bb_main": {R: [(0, 0, 0, 0), (0.7, 0, 0, 360), (1.4, 0, 0, 360)],
                                       P: [(0, 0, 0, 0), (0.7, 0, 5, 0), (1.4, 0, 0, 0)]}}),
    ("throw", "once", 0.5, {"bb_main": {R: [(0, 0, 0, 0), (0.25, -25, 0, 0), (0.5, 0, 0, 0)]}}),
    ("cast", "once", 0.9, {"bb_main": {R: [(0, 0, 0, 0), (0.45, 0, 0, 180), (0.9, 0, 0, 360)]}}),
    ("dash", "once", 0.5, {"bb_main": {R: [(0, 0, 0, 0), (0.25, -20, 0, 25), (0.5, 0, 0, 0)], P: [(0, 0, 0, 0), (0.25, 0, 0, -6), (0.5, 0, 0, 0)]}}),
    ("summon", "once", 0.4, {"bb_main": {S: [(0, 0.1, 0.1, 0.1), (0.25, 1.15, 1.15, 1.15), (0.4, 1, 1, 1)]}}),
]


def bone_ids(nodes, out):
    for g in nodes:
        if isinstance(g, dict):
            out[g["name"]] = g["uuid"]
            bone_ids(g["children"], out)
    return out


for f in sorted(Path(sys.argv[1]).glob("*.bbmodel")):
    m = json.loads(f.read_text())
    bones = bone_ids(m["outliner"], {})
    spec = HUMANOID if "body2" in bones else PLANE
    m["animations"] = [anim(n, loop, length, tracks, bones) for n, loop, length, tracks in spec]
    f.write_text(json.dumps(m))
    print(f.stem, [a["name"] for a in m["animations"]], "bones animated:", sorted({b["name"] for a in m["animations"] for b in a["animators"].values()}))

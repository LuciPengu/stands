#!/usr/bin/env python3
"""Make the stand skins see-through (ghostlike). Reversible: the original skin is kept inside each model.

Usage: set_opacity.py <models_dir> <opacity 0.0-1.0>     (1.0 restores the original)
"""
import base64, io, json, sys
from pathlib import Path
from PIL import Image

models, opacity = Path(sys.argv[1]), float(sys.argv[2])
PREFIX = "data:image/png;base64,"
for f in sorted(models.glob("*.bbmodel")):
    m = json.loads(f.read_text())
    for t in m["textures"]:
        original = t.setdefault("source_original", t["source"])
        img = Image.open(io.BytesIO(base64.b64decode(original[len(PREFIX):]))).convert("RGBA")
        if opacity < 1.0:
            r, g, b, a = img.split()
            img = Image.merge("RGBA", (r, g, b, a.point(lambda v: int(v * opacity))))
        out = io.BytesIO(); img.save(out, "PNG")
        t["source"] = PREFIX + base64.b64encode(out.getvalue()).decode()
    f.write_text(json.dumps(m))
    print(f.stem, f"opacity {opacity:.0%}")

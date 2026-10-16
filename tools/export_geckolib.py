"""Export a Blockbench GeckoLib project (.bbmodel) to the mod's runtime assets.

Mirrors Blockbench 5's Bedrock codec, which the GeckoLib plugin uses for its exports:
  * bone pivot x and cube origin x are mirrored, bone/cube rotation x,y are negated
  * per-face UVs become uv + uv_size, with up/down faces flipped
  * animation keyframes: rotation x,y and position x are negated

Usage: python tools/export_geckolib.py art/blockbench/dog.bbmodel
Writes geo/<id>.geo.json, animations/<id>.animation.json and textures/entity/<id>.png
under src/main/resources/assets/<modid>/.
"""
import base64, json, os, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def num(x):
    x = round(float(x), 4)
    return int(x) if x == int(x) else x


def export_geometry(d, ident):
    groups = {g["uuid"]: g for g in d["groups"]}
    elements = {e["uuid"]: e for e in d["elements"]}
    bones = []

    def cube(e):
        frm, to = e["from"], e["to"]
        size = [to[i] - frm[i] for i in range(3)]
        out = {"origin": [num(-(frm[0] + size[0])), num(frm[1]), num(frm[2])], "size": [num(s) for s in size]}
        if e.get("inflate"):
            out["inflate"] = e["inflate"]
        rot = e.get("rotation") or [0, 0, 0]
        if any(rot):
            piv = e.get("origin", [0, 0, 0])
            out["pivot"] = [num(-piv[0]), num(piv[1]), num(piv[2])]
            out["rotation"] = [num(-rot[0]), num(-rot[1]), num(rot[2])]
        if e.get("box_uv"):
            out["uv"] = e["uv_offset"]
            if e.get("mirror_uv"):
                out["mirror"] = True
        else:
            uv = {}
            for key, face in e["faces"].items():
                if face.get("texture") is None:
                    continue
                u = face["uv"]
                f = {"uv": [num(u[0]), num(u[1])], "uv_size": [num(u[2] - u[0]), num(u[3] - u[1])]}
                if key in ("up", "down"):
                    f["uv"] = [num(f["uv"][0] + f["uv_size"][0]), num(f["uv"][1] + f["uv_size"][1])]
                    f["uv_size"] = [num(-f["uv_size"][0]), num(-f["uv_size"][1])]
                if face.get("rotation"):
                    f["uv_rotation"] = face["rotation"]
                uv[key] = f
            out["uv"] = uv
        return out

    def visit(node, parent):
        g = groups[node["uuid"]]
        bone = {"name": g["name"]}
        if parent:
            bone["parent"] = parent
        piv = g["origin"]
        bone["pivot"] = [num(-piv[0]), num(piv[1]), num(piv[2])]
        rot = g.get("rotation", [0, 0, 0])
        if any(rot):
            bone["rotation"] = [num(-rot[0]), num(-rot[1]), num(rot[2])]
        cubes = [cube(elements[c]) for c in node["children"] if isinstance(c, str) and elements[c].get("export", True)]
        if cubes:
            bone["cubes"] = cubes
        bones.append(bone)
        for c in node["children"]:
            if isinstance(c, dict):
                visit(c, g["name"])

    for n in d["outliner"]:
        if isinstance(n, dict):
            visit(n, None)
    vb = d.get("visible_box", [1, 1, 0])
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": f"geometry.{ident}",
                "texture_width": d["resolution"]["width"],
                "texture_height": d["resolution"]["height"],
                "visible_bounds_width": vb[0], "visible_bounds_height": vb[1], "visible_bounds_offset": [0, vb[2], 0],
            },
            "bones": bones,
        }],
    }


def export_animations(d):
    out = {}
    for a in d.get("animations", []):
        anim = {}
        if a["loop"] == "loop":
            anim["loop"] = True
        elif a["loop"] == "hold":
            anim["loop"] = "hold_on_last_frame"
        anim["animation_length"] = num(a["length"])
        bones = {}
        for an in a["animators"].values():
            if an.get("type", "bone") != "bone":
                continue
            chans = {}
            for ch in ("rotation", "position", "scale"):
                keys = sorted((k for k in an["keyframes"] if k["channel"] == ch), key=lambda k: k["time"])
                if not keys:
                    continue
                cm = {}
                prev = None
                for k in keys:
                    p = k["data_points"][0]
                    v = [float(p["x"]), float(p["y"]), float(p["z"])]
                    if ch == "rotation":
                        v = [-v[0], -v[1], v[2]]
                    elif ch == "position":
                        v = [-v[0], v[1], v[2]]
                    v = [num(x) for x in v]
                    t = str(num(k["time"]))
                    if k.get("interpolation") == "catmullrom":
                        cm[t] = {"post": v, "lerp_mode": "catmullrom"}
                        if prev is None and k["time"] > 0 or prev is not None and prev.get("interpolation") != "catmullrom":
                            cm[t]["pre"] = v
                    else:
                        cm[t] = v
                    prev = k
                chans[ch] = cm
            if chans:
                bones[an["name"]] = chans
        anim["bones"] = bones
        out[a["name"]] = anim
    # the GeckoLib plugin tags its exports like this (non-baked bezier keyframes)
    return {"format_version": "1.8.0", "animations": out, "geckolib_format_version": 2}


def main(path):
    d = json.load(open(path, encoding="utf-8"))
    ident = d.get("model_identifier") or os.path.splitext(os.path.basename(path))[0]
    modid = d.get("geckolib_modid") or "goodboys"
    assets = os.path.join(ROOT, "src", "main", "resources", "assets", modid)
    targets = {
        os.path.join(assets, "geo", "entity", f"{ident}.geo.json"): export_geometry(d, ident),
        os.path.join(assets, "animations", "entity", f"{ident}.animation.json"): export_animations(d),
    }
    for p, data in targets.items():
        os.makedirs(os.path.dirname(p), exist_ok=True)
        with open(p, "w", encoding="utf-8", newline="\n") as f:
            json.dump(data, f, indent="\t")
        print("wrote", os.path.relpath(p, ROOT))
    tex = d["textures"][0]
    if tex.get("source", "").startswith("data:image/png;base64,"):
        p = os.path.join(assets, "textures", "entity", f"{ident}.png")
        with open(p, "wb") as f:
            f.write(base64.b64decode(tex["source"].split(",", 1)[1]))
        print("wrote", os.path.relpath(p, ROOT))


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "art", "blockbench", "dog.bbmodel"))

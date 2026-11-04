"""Structural and geometric checks for the generated Quest Plus Blockbench pack."""
import base64, json, math, sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
EPS = 1e-5


def read(rel):
    return json.loads((ROOT / rel).read_text(encoding="utf-8"))


def require(test, message):
    if not test:
        raise AssertionError(message)


def json_track(animation, bone, channel):
    track=animation.get("bones",{}).get(bone,{}).get(channel,{})
    keys=[]
    for timestamp,value in track.items():
        vector=value.get("vector",value) if isinstance(value,dict) else value
        keys.append((float(timestamp),np.array(vector,dtype=float)))
    return sorted(keys,key=lambda key:key[0])


def json_sample(animation, bone, channel, fraction):
    keys=json_track(animation,bone,channel)
    if not keys:return np.zeros(3)
    end=float(animation["animation_length"])
    time=max(keys[0][0],min(keys[-1][0],fraction*end))
    for index,(t1,v1) in enumerate(keys):
        if t1>=time:
            t0,v0=keys[max(index-1,0)]
            return v0 if t1-t0<=EPS else v0+(v1-v0)*((time-t0)/(t1-t0))
    return keys[-1][1]


def validate_choreography(export, namespace, model):
    animations=export["animations"]
    opening=animations[f"{namespace}.open"]
    closing=animations[f"{namespace}.close"]
    idle=animations[f"{namespace}.idle"]
    def pose(animation,bone,fraction):
        return np.concatenate([json_sample(animation,bone,channel,fraction) for channel in ("position","rotation")])
    start_r=pose(opening,"rightArm",0)
    start_l=pose(opening,"leftArm",0)
    at20_r=pose(opening,"rightArm",.2)
    at20_l=pose(opening,"leftArm",.2)
    at50_l=pose(opening,"leftArm",.5)
    require(np.linalg.norm(at20_r-start_r)>EPS,"right hand has not started moving by 20% of opening")
    require(np.allclose(at20_l,start_l,atol=EPS),"left hand moves before the right hand finishes its initial reach")
    require(np.linalg.norm(at50_l-start_l)>EPS,"left hand never joins the tablet during opening")
    source={a["name"]:a for a in model["animations"]}
    source_open=source[f"{namespace}.open"]
    source_idle=source[f"{namespace}.idle"]
    source_close=source[f"{namespace}.close"]
    start_pos=interpolate(source_open,"tablet_root","position",0)
    end_pos=interpolate(source_open,"tablet_root","position",source_open["length"])
    require(start_pos[0]>end_pos[0]+EPS and start_pos[1]<end_pos[1]-EPS,"tablet does not begin from the lower-right")
    idle_tablet_rot=interpolate(source_idle,"tablet_root","rotation",source_idle["length"])
    require(abs(abs(idle_tablet_rot[1])-180)<1e-3,"tablet screen does not face the player at the end of opening/idle")
    for channel in ("position","rotation","scale"):
        for index in range(41):
            fraction=index/40
            opened=interpolate(source_open,"tablet_root",channel,fraction*source_open["length"])
            closed=interpolate(source_close,"tablet_root",channel,(1-fraction)*source_close["length"])
            require(np.allclose(opened,closed,atol=2e-3),f"tablet open/close paths are not reverses for {channel} at {fraction:.2f}")
        require(np.allclose(interpolate(source_open,"tablet_root",channel,source_open["length"]),interpolate(source_idle,"tablet_root",channel,0),atol=2e-3),f"tablet opening to idle is discontinuous for {channel}")
        require(np.allclose(interpolate(source_idle,"tablet_root",channel,0),interpolate(source_close,"tablet_root",channel,0),atol=2e-3),f"tablet idle to close is discontinuous for {channel}")

    open_bones=opening.get("bones",{})
    close_bones=closing.get("bones",{})
    for bone,open_channels in open_bones.items():
        require(bone in close_bones,"close animation is missing a bone from open")
        for channel,open_keys in open_channels.items():
            require(channel in close_bones[bone],f"close animation is missing {bone}.{channel}")
            close_keys=close_bones[bone][channel]
            for index in range(41):
                fraction=index/40
                a=json_sample(opening,bone,channel,fraction)
                b=json_sample(closing,bone,channel,1-fraction)
                require(np.allclose(a,b,atol=2e-3),f"open/close paths are not reverses: {bone}.{channel} (open {fraction*opening['animation_length']:.4f}s vs close {(1-fraction)*closing['animation_length']:.4f}s)")
            idle_start=json_sample(idle,bone,channel,0)
            close_start=json_sample(closing,bone,channel,0)
            require(np.allclose(json_sample(opening,bone,channel,1),idle_start,atol=2e-3),f"opening to idle is discontinuous: {bone}.{channel}")
            require(np.allclose(json_sample(idle,bone,channel,0),close_start,atol=2e-3),f"idle to closing is discontinuous: {bone}.{channel}")
    return {"right_hand_starts_first":True,"tablet_starts_lower_right":True,"held_screen_faces_player":True,"open_close_reverse_path_samples":41}


def mat(origin, angles, shift=(0, 0, 0)):
    x, y, z = np.radians(angles)
    cx, sx = math.cos(x), math.sin(x)
    cy, sy = math.cos(y), math.sin(y)
    cz, sz = math.cos(z), math.sin(z)
    r = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]) @ np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]) @ np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    result = np.eye(4)
    result[:3, :3] = r
    result[:3, 3] = np.array(origin) - r @ origin + shift
    return result


def interpolate(anim, bone, channel, time):
    animator = next((a for a in anim["animators"].values() if a["name"] == bone), None)
    if animator is None:
        return np.ones(3) if channel == "scale" else np.zeros(3)
    keys = sorted((k for k in animator["keyframes"] if k["channel"] == channel), key=lambda k: k["time"])
    if not keys:
        return np.ones(3) if channel == "scale" else np.zeros(3)
    for index, current in enumerate(keys):
        if current["time"] >= time:
            previous = keys[max(index - 1, 0)]
            v0 = np.array([float(previous["data_points"][0][c]) for c in "xyz"], float)
            v1 = np.array([float(current["data_points"][0][c]) for c in "xyz"], float)
            span = current["time"] - previous["time"]
            alpha = 0 if span <= EPS else (time - previous["time"]) / span
            return v0 + (v1 - v0) * alpha
    return np.array([float(keys[-1]["data_points"][0][c]) for c in "xyz"], float)


def bounds(e, transform):
    lo, hi = np.array(e["from"], float), np.array(e["to"], float)
    corners = np.array([[x, y, z, 1] for x in (lo[0], hi[0]) for y in (lo[1], hi[1]) for z in (lo[2], hi[2])])
    element = mat(e.get("origin", [0, 0, 0]), e.get("rotation", [0, 0, 0]))
    return (transform @ element @ corners.T).T[:, :3]


def group_transform(group, anim, time):
    name=group["name"]
    origin=group.get("origin",[0,0,0])
    matrix=mat(origin,interpolate(anim,name,"rotation",time),interpolate(anim,name,"position",time))
    scale=interpolate(anim,name,"scale",time)
    scaling=np.eye(4)
    scaling[:3,:3]=np.diag(scale)
    scaling[:3,3]=np.array(origin)-np.diag(scale)@origin
    return matrix@scaling


def overlaps(a, b):
    """Full OBB separating-axis test; boundary contact is permitted."""
    if np.any(a.max(axis=0) <= b.min(axis=0) + EPS) or np.any(b.max(axis=0) <= a.min(axis=0) + EPS):
        return False
    def axes(poly):
        x, y, z = poly[1] - poly[0], poly[2] - poly[0], poly[4] - poly[0]
        return [x / np.linalg.norm(x), y / np.linalg.norm(y), z / np.linalg.norm(z)]
    aa, bb = axes(a), axes(b)
    directions = aa + bb + [np.cross(x, y) for x in aa for y in bb]
    for axis in directions:
        norm = np.linalg.norm(axis)
        if norm < EPS:
            continue
        axis /= norm
        pa, pb = a @ axis, b @ axis
        if pa.max() <= pb.min() + EPS or pb.max() <= pa.min() + EPS:
            return False
    return True


def static_checks():
    textures = {path.name: path.read_bytes() for path in (ROOT / "textures").glob("*.png")}
    require("questplus_tablet.png" in textures, "tablet texture missing")
    texture = textures["questplus_tablet.png"]
    image = Image.open(ROOT / "textures/questplus_tablet.png")
    require(image.size == (256, 256), "texture must be 256x256")
    layout = read("models/atlas-layout.json")
    require(layout["resolution"] == [256, 256], "atlas-layout resolution mismatch")
    for i, a in enumerate(layout["islands"]):
        x, y, w, h, _ = a
        require(x >= 0 and y >= 0 and x + w <= 256 and y + h <= 256, f"atlas island out of bounds: {a}")
        for b in layout["islands"][i + 1:]:
            xx, yy, ww, hh, _ = b
            require(x + w <= xx or xx + ww <= x or y + h <= yy or yy + hh <= y, f"atlas islands overlap: {a} and {b}")
    result = {"passed": [], "warnings": [], "models": {}, "arm_tablet_collisions": {}}
    models = {}
    for path in sorted((ROOT / "blockbench").glob("*.bbmodel")):
        data = json.loads(path.read_text(encoding="utf-8"))
        name = path.stem
        models[name] = data
        element_ids = [e["uuid"] for e in data["elements"]]
        group_ids = [g["uuid"] for g in data["groups"]]
        animation_ids = [a["uuid"] for a in data["animations"]]
        keyframe_ids = [k["uuid"] for a in data["animations"] for b in a["animators"].values() for k in b["keyframes"]]
        require(len(keyframe_ids) == len(set(keyframe_ids)), f"duplicate editable keyframe UUID: {path.name}")
        require(len(set(element_ids + group_ids + animation_ids)) == len(element_ids + group_ids + animation_ids), f"duplicate UUID in {path.name}")
        by_group = set(group_ids)
        by_element = set(element_ids)
        def visit(node):
            require(node["uuid"] in by_group, f"dangling outliner group in {path.name}: {node['uuid']}")
            for child in node["children"]:
                if isinstance(child, dict):
                    visit(child)
                else:
                    require(child in by_element, f"dangling outliner element in {path.name}: {child}")
        referenced_groups=[]
        referenced_elements=[]
        def collect(node):
            referenced_groups.append(node["uuid"])
            for child in node["children"]:
                if isinstance(child, dict):collect(child)
                else:referenced_elements.append(child)
        for node in data["outliner"]:
            visit(node)
            collect(node)
        require(set(referenced_groups) == by_group and len(referenced_groups) == len(set(referenced_groups)), f"orphaned or duplicate groups in outliner: {path.name}")
        require(set(referenced_elements) == by_element and len(referenced_elements) == len(set(referenced_elements)), f"orphaned or duplicate elements in outliner: {path.name}")
        for element in data["elements"]:
            lo, hi = np.array(element["from"], float), np.array(element["to"], float)
            require(np.all(hi > lo), f"non-positive cube: {element['name']} ({name})")
            require(np.all(np.isfinite(lo)) and np.all(np.isfinite(hi)), f"non-finite cube: {element['name']}")
            require(set(element["faces"]) == {"north", "south", "east", "west", "up", "down"}, f"incomplete faces: {element['name']}")
            for face in element["faces"].values():
                u, v, U, V = face["uv"]
                require(0 <= u < U <= 256 and 0 <= v < V <= 256, f"UV out of range: {element['name']} {face['uv']}")
        for tex in data["textures"]:
            embedded = tex["source"].split(",", 1)[1]
            image_name = tex.get("name") or Path(tex.get("path", "")).name
            require(image_name in textures, f"external texture missing: {image_name} ({path.name})")
            require(base64.b64decode(embedded) == textures[image_name], f"embedded texture differs: {image_name} ({path.name})")
        for anim in data["animations"]:
            require(anim["length"] > 0 and anim["loop"] in ("loop", "once", "hold"), f"bad animation header: {anim['name']}")
            for group_id, animator in anim["animators"].items():
                require(group_id in by_group, f"animation references missing group: {anim['name']} -> {group_id}")
                for channel in ("position", "rotation", "scale"):
                    keys = sorted((k for k in animator["keyframes"] if k["channel"] == channel), key=lambda k:k["time"])
                    times = [k["time"] for k in keys]
                    require(times == sorted(set(times)), f"duplicate or unsorted {channel} keys: {anim['name']}/{animator['name']}")
                    require(all(0 <= t <= anim["length"] for t in times), f"keyframe outside animation duration: {anim['name']}")
                    for key in keys:
                        require(all(math.isfinite(float(key["data_points"][0][axis])) for axis in "xyz"), f"non-finite animation key: {anim['name']}")
        def component(anim,bone,channel,endpoint):
            track=next((a for a in anim["animators"].values() if a["name"]==bone),None)
            keys=sorted((k for k in track["keyframes"] if k["channel"]==channel),key=lambda k:k["time"]) if track else []
            if not keys:return None
            key=keys[0] if endpoint=="start" else keys[-1]
            return [float(key["data_points"][0][axis]) for axis in "xyz"]
        for idle in (a for a in data["animations"] if a["loop"]=="loop"):
            for bone_animation in idle["animators"].values():
                for channel in ("position","rotation","scale"):
                    first=component(idle,bone_animation["name"],channel,"start")
                    last=component(idle,bone_animation["name"],channel,"end")
                    if first is not None and last is not None:
                        require(np.allclose(first,last,atol=EPS),f"idle loop does not close: {idle['name']}/{bone_animation['name']}/{channel}")
        result["models"][name] = {"cubes": len(element_ids), "groups": len(group_ids), "animations": [a["name"] for a in data["animations"]]}
    result["passed"].extend(["256x256 texture", "atlas bounds and non-overlap", "Blockbench UUID/outliner/UV/embedded-texture structure"])
    require("questplus_tablet" in models, "missing tablet .bbmodel")
    tablet = models["questplus_tablet"]
    runtime = ROOT.parent / "src/main/resources/assets/questplus/tablet/geometry.json"
    require(json.loads(runtime.read_text(encoding="utf-8")) == tablet["elements"], "runtime tablet geometry differs from editable source")
    book = {e["name"]: e for e in tablet["elements"] if e["name"].startswith("book_")}
    require(all(e.get("rotation", [0, 0, 0]) == [0, 0, 0] for e in book.values()), "book must remain straight without tilted pages or covers")
    for model in models.values():
        for clip in model["animations"]:
            if clip["name"].endswith(("open", "close")):
                require(clip["length"] == 1.0, "tablet opening and closing must last one second")
    for side in (-1, 1):
        page = book[f"book_open_page_{side}"]
        require(min(abs(page["from"][0]), abs(page["to"][0])) <= .055, "book pages leave a gap at the binding")
    require(len(tablet["elements"]) == 79, "tablet geometry should contain 79 authored cubes")
    require(not any("cluster" in e["name"] or "shard" in e["name"] for e in tablet["elements"]), "outer crystal clusters should be absent")
    require(len([e for e in tablet["elements"] if "copper" in e["name"]]) >= 3, "copper frame detailing missing")
    required_animations={a["name"] for a in tablet["animations"]}
    require(required_animations=={"questplus.tablet_open","questplus.tablet_idle","questplus.tablet_close"},"tablet animation names missing")
    screen=next(e for e in tablet["elements"] if e["name"]=="blank_display")
    require(np.allclose(np.subtract(screen["to"],screen["from"])[:2],[20,12]),"blank display must remain 20x12 model units")
    for name in ("questplus_first_person", "questplus_first_person_slim", "questplus_third_person", "questplus_third_person_slim"):
        model=models[name]
        for element in model["elements"]:
            if element["name"] not in book:
                continue
            reference = book[element["name"]]
            for field in ("from", "to", "origin"):
                require(np.allclose(element[field], np.array(reference[field]) * .65 + [0, 18.8, -10.5], atol=EPS), f"{name}: book {field} differs from tablet source")
            require(element.get("rotation", [0, 0, 0]) == reference.get("rotation", [0, 0, 0]), f"{name}: book rotation differs from tablet source")
        idle=next(a for a in model["animations"] if a["name"].endswith("idle"))
        root=next(a for a in idle["animators"].values() if a["name"]=="tablet_root")
        rotations=sorted((k for k in root["keyframes"] if k["channel"]=="rotation"),key=lambda k:k["time"])
        require(rotations and abs(abs(float(rotations[-1]["data_points"][0]["y"]))-180)<EPS,"held tablet screen must face the player (+Z)")
    fp=models["questplus_first_person"]
    require(any(g["name"]=="fp_player_arms" for g in fp["groups"]),"first-person PAL root bone missing")
    # The display front is z=-0.82. Nothing except the screen cube may cross it in its blank rectangle.
    for e in tablet["elements"]:
        if e["name"] == "blank_display":
            continue
        poly = bounds(e, np.eye(4))
        hits = poly[:, 2].min() <= -.82 + EPS and poly[:, 2].max() >= -.82 - EPS
        xy = poly[:, :2]
        spans = xy[:, 0].min() < 10 - EPS and xy[:, 0].max() > -10 + EPS and xy[:, 1].min() < 6 - EPS and xy[:, 1].max() > -6 + EPS
        require(not (hits and spans), f"decoration crosses blank display: {e['name']}")
    geo=read("models/questplus_tablet.geo.json")
    require(geo["format_version"] == "1.12.0", "unexpected geometry format")
    geobones=geo["minecraft:geometry"][0]["bones"]
    cubecount=sum(len(b.get("cubes",[])) for b in geobones)
    require(cubecount == len(tablet["elements"]), f"Gecko geometry cube mismatch: {cubecount}")
    result["passed"].extend(["blank display clearance", "GeckoLib geometry cube count"])
    fp_export=read("animations/player/questplus_first_person.animation.json")
    require(fp_export.get("format_version")=="1.8.0" and fp_export.get("azurelib_format_version")==2 and "animations" in fp_export,"FP PAL export is missing Bedrock/AzureLib animation root")
    fp_open=fp_export["animations"].get("questplus.open")
    require(fp_open is not None,"FP PAL open animation missing")
    display=fp_open.get("player_animation_library",{})
    require(display.get("fpDisplayAnchor")=="camera" and "fpDisplayTranslation" in display and "fpDisplayScale" in display,"FP PAL camera display config missing")
    require({"fp_player_arms","tablet_root"}.issubset(fp_open.get("bones",{})),"FP PAL rig bones missing")
    for name in ("questplus_first_person", "questplus_third_person"):
        export=read(f"animations/player/{name}.animation.json")
        require(export.get("format_version")=="1.8.0" and "animations" in export,"player PAL export root missing")
        if name == "questplus_first_person":
            try:
                result["choreography"]=validate_choreography(export,"questplus",models[name])
            except AssertionError as error:
                result.setdefault("errors",[]).append(str(error))
    result["passed"].append("first-person PAL root and animation export")
    return models, result


def posed_collisions(models, result):
    for model_name in ("questplus_first_person", "questplus_first_person_slim", "questplus_third_person", "questplus_third_person_slim"):
        data=models[model_name]
        groups={g["uuid"]:g for g in data["groups"]}
        elems={e["uuid"]:e for e in data["elements"]}
        parents={}
        def walk(node,parent=None):
            parents[node["uuid"]]=parent
            for c in node["children"]:
                if isinstance(c,dict):walk(c,node["uuid"])
                else:parents[c]=node["uuid"]
        for root in data["outliner"]:walk(root)
        name_to_group={g["name"]:g["uuid"] for g in data["groups"]}
        tablet_root=name_to_group["tablet_root"]
        tablet_groups=set()
        tablet_elements=set()
        def find_tablet(node):
            if node["uuid"]==tablet_root:
                def descend(n):
                    tablet_groups.add(n["uuid"])
                    for child in n["children"]:
                        if isinstance(child,dict):descend(child)
                        else:tablet_elements.add(child)
                descend(node)
                return True
            for child in node["children"]:
                if isinstance(child,dict) and find_tablet(child):return True
            return False
        for node in data["outliner"]:find_tablet(node)
        tablet_ids=list(tablet_elements)
        hand_ids=[e["uuid"] for e in data["elements"] if e["name"].endswith("_hand")]
        body_ids=[e["uuid"] for e in data["elements"] if e["name"] in ("preview_torso", "preview_head")]
        require(tablet_ids and len(hand_ids)==2, f"collision selection failed for {model_name}")
        report={}; body_report={}
        for anim in data["animations"]:
            if not anim["name"].endswith(("open","close")):
                continue
            key_times=sorted({0.0,anim["length"],*(k["time"] for a in anim["animators"].values() for k in a["keyframes"])})
            sample_times=sorted(set(key_times+[(a+b)/2 for a,b in zip(key_times,key_times[1:])]+[anim["length"]*i/40 for i in range(41)]))
            contacts=[]; body_contacts=[]
            for t in sample_times:
                group_mats={}
                def world(gid):
                    if gid in group_mats:return group_mats[gid]
                    g=groups[gid]
                    anim_group=next((a for a in anim["animators"].values() if a["name"]==g["name"]),None)
                    parent=parents.get(gid)
                    matrix=world(parent) if parent else np.eye(4)
                    group_mats[gid]=matrix@group_transform(g,anim,t)
                    return group_mats[gid]
                def element_world(e):
                    chain=[];group=parents[e["uuid"]]
                    while group:
                        chain.append(group);group=parents.get(group)
                    matrix=np.eye(4)
                    for gid in reversed(chain):matrix=matrix@group_transform(groups[gid],anim,t)
                    return matrix
                tablet_scale=interpolate(anim,"tablet_root","scale",t)
                tablets=[] if np.any(np.abs(tablet_scale)<EPS) else [(elems[i],bounds(elems[i],element_world(elems[i]))) for i in tablet_ids]
                for hand_id in hand_ids:
                    hand=elems[hand_id];hp=bounds(hand,element_world(hand))
                    for te,tp in tablets:
                        if overlaps(hp,tp):
                            contacts.append({"time":round(t,4),"hand":hand["name"],"part":te["name"]})
                            break
                if body_ids:
                    for body_id in body_ids:
                        body=elems[body_id];bp=bounds(body,element_world(body))
                        for te,tp in tablets:
                            if overlaps(bp,tp):
                                body_contacts.append({"time":round(t,4),"body":body["name"],"part":te["name"]})
                                break
            report[anim["name"]]={"sampled_times":len(sample_times),"first_intersections":contacts[:12],"intersection_samples":len(contacts)}
            if body_ids:body_report[anim["name"]]={"sampled_times":len(sample_times),"first_intersections":body_contacts[:12],"intersection_samples":len(body_contacts)}
        result["arm_tablet_collisions"][model_name]=report
        if body_report:result.setdefault("body_tablet_collisions",{})[model_name]=body_report
    result["passed"].append("hand/tablet OBB scan at 41 uniform samples, keyframes and midpoints per transition")
    if result.get("body_tablet_collisions"):
        result["passed"].append("third-person head/torso OBB scan at transition samples")
    return result


def main():
    models, result=static_checks()
    for tablet_anim, player_anim in zip(models['questplus_tablet']['animations'],models['questplus_third_person']['animations']):
        for t in np.linspace(0,tablet_anim['length'],41):
            for channel in ('position','rotation','scale'):
                require(np.allclose(interpolate(tablet_anim,'tablet_root',channel,t),interpolate(player_anim,'tablet_root',channel,t),atol=EPS),'standalone tablet is not synchronized with third person')
    result['passed'].append('standalone tablet synchronized with third-person prop track')
    posed_collisions(models,result)
    output=ROOT/"validation.json"
    output.write_text(json.dumps(result,indent=2)+"\n",encoding="utf-8")
    print(json.dumps(result,indent=2))
    intersections=sum(item["intersection_samples"] for group in result["arm_tablet_collisions"].values() for item in group.values())
    intersections+=sum(item["intersection_samples"] for group in result.get("body_tablet_collisions",{}).values() for item in group.values())
    require(not result.get("errors"),"asset validation failures: "+"; ".join(result.get("errors",[])))
    require(intersections==0,f"detected {intersections} arm/body-tablet collision samples; see {output}")


if __name__ == "__main__":
    main()

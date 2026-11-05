package dev.questplus.fabric.client;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Samples the actual exported Blockbench first-person keyframes (no artificial 250 ms screen slide). */
final class TabletFirstPersonAnimation {
    record Vector(float x,float y,float z) {
        static final Vector ZERO=new Vector(0,0,0);
        static final Vector ONE=new Vector(1,1,1);
    }
    record Pose(Vector rightRotation,Vector rightPosition,Vector leftRotation,Vector leftPosition,
                Vector tabletRotation,Vector tabletPosition,boolean tabletVisible) {}
    private record Frame(double at,Vector value) {}
    private record Channel(List<Frame> frames,Vector fallback) {
        Vector sample(double t) {
            if (frames.isEmpty()) return fallback;
            if (t<=frames.getFirst().at()) return frames.getFirst().value();
            for (int i=1;i<frames.size();i++) {
                Frame after=frames.get(i);
                if (t<=after.at()) {
                    Frame before=frames.get(i-1);
                    double alpha=(t-before.at())/(after.at()-before.at());
                    return new Vector(mix(before.value().x(),after.value().x(),alpha),
                        mix(before.value().y(),after.value().y(),alpha),
                        mix(before.value().z(),after.value().z(),alpha));
                }
            }
            return frames.getLast().value();
        }
    }
    private record Bone(Channel rotation,Channel position,Channel scale) {}
    private record Clip(double duration,boolean loop,Map<String,Bone> bones) {
        Bone bone(String name) {
            var value=bones.get(name);
            if (value==null) throw new IllegalStateException("Missing Blockbench bone: "+name);
            return value;
        }
    }
    private static final Map<String,Clip> CLIPS=load();
    static final long OPEN_NANOS=(long)(CLIPS.get("questplus.open").duration()*1_000_000_000L);
    private TabletFirstPersonAnimation() {}

    static Pose pose(String clipName,double seconds) {
        Clip clip=CLIPS.get(clipName);
        if (clip==null) throw new IllegalArgumentException("Unknown Blockbench animation "+clipName);
        double t=clip.loop() ? Math.floorMod((long)(Math.max(0,seconds)*1_000_000), (long)(clip.duration()*1_000_000))/1_000_000.0
            : Math.clamp(seconds,0,clip.duration());
        Bone right=clip.bone("rightArm"),left=clip.bone("leftArm"),tablet=clip.bone("tablet_root");
        return new Pose(right.rotation().sample(t),right.position().sample(t),
            left.rotation().sample(t),left.position().sample(t),
            tablet.rotation().sample(t),tablet.position().sample(t),tablet.scale().sample(t).x()>.5f);
    }

    private static float mix(float from,float to,double alpha) { return (float)(from+(to-from)*alpha); }
    private static Channel channel(JsonObject bone,String key,Vector fallback) {
        List<Frame> frames=new ArrayList<>();
        if (bone.has(key)) {
            for (var entry:bone.getAsJsonObject(key).entrySet()) {
                JsonArray v=entry.getValue().getAsJsonArray();
                if (v.size()!=3) throw new IllegalStateException("Invalid Blockbench channel "+key);
                frames.add(new Frame(Double.parseDouble(entry.getKey()),
                    new Vector(v.get(0).getAsFloat(),v.get(1).getAsFloat(),v.get(2).getAsFloat())));
            }
            frames.sort(Comparator.comparingDouble(Frame::at));
        }
        return new Channel(List.copyOf(frames),fallback);
    }
    private static Map<String,Clip> load() {
        try (var stream=TabletFirstPersonAnimation.class.getResourceAsStream("/assets/questplus/animations/first_person.json")) {
            if (stream==null) throw new IOException("Missing Quest Plus Blockbench first-person animation");
            var animations=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonObject("animations");
            Map<String,Clip> clips=new HashMap<>();
            for (var entry:animations.entrySet()) {
                var definition=entry.getValue().getAsJsonObject();
                double duration=definition.get("animation_length").getAsDouble();
                if (duration<=0 || !Double.isFinite(duration)) throw new IOException("Invalid animation duration: "+entry.getKey());
                Map<String,Bone> bones=new HashMap<>();
                for (var b:definition.getAsJsonObject("bones").entrySet()) {
                    var value=b.getValue().getAsJsonObject();
                    bones.put(b.getKey(),new Bone(channel(value,"rotation",Vector.ZERO),
                        channel(value,"position",Vector.ZERO),channel(value,"scale",Vector.ONE)));
                }
                clips.put(entry.getKey(),new Clip(duration,definition.has("loop") && definition.get("loop").getAsBoolean(),Map.copyOf(bones)));
            }
            for (String required:List.of("questplus.open","questplus.idle","questplus.close"))
                if (!clips.containsKey(required)) throw new IOException("Missing Blockbench animation "+required);
            return Map.copyOf(clips);
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("Cannot load Quest Plus Blockbench animation",error);
        }
    }
}

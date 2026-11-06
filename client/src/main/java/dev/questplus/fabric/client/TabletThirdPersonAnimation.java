package dev.questplus.fabric.client;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Dense, linear keyframes exported from the third-person Blockbench model. */
final class TabletThirdPersonAnimation {
    record Vector(float x,float y,float z) {}
    record Pose(Vector right,Vector rightPosition,Vector left,Vector leftPosition,Vector head,Vector position,Vector rotation,Vector scale) {
        Pose blend(Pose next,double amount) {
            return new Pose(vector(right,next.right,amount),vector(rightPosition,next.rightPosition,amount),
                vector(left,next.left,amount),vector(leftPosition,next.leftPosition,amount),vector(head,next.head,amount),
                vector(position,next.position,amount),vector(rotation,next.rotation,amount),vector(scale,next.scale,amount));
        }
    }
    private record Frame(double time,Vector value) {}
    private record Channel(List<Frame> frames,Vector fallback) {
        Vector sample(double time) {
            if (frames.isEmpty()) return fallback;
            if (time<=frames.getFirst().time()) return frames.getFirst().value();
            for (int i=1;i<frames.size();i++) {
                Frame next=frames.get(i);
                if (time<=next.time()) {
                    Frame previous=frames.get(i-1);
                    double t=(time-previous.time())/(next.time()-previous.time());
                    return new Vector(mix(previous.value().x(),next.value().x(),t),
                        mix(previous.value().y(),next.value().y(),t),mix(previous.value().z(),next.value().z(),t));
                }
            }
            return frames.getLast().value();
        }
    }
    private record Bone(Channel rotation,Channel position,Channel scale) {}
    private record Clip(double length,Map<String,Bone> bones) {}
    private static final Vector ZERO=new Vector(0,0,0), ONE=new Vector(1,1,1);
    private static final Map<String,Clip> CLIPS=load();
    static final double OPEN_SECONDS=CLIPS.get("questplus.third_person_open").length();
    // First visible Blockbench frame: right hand at the hip, left arm still free.
    static final double CARRY_SECONDS=.2167;
    static Pose carry() { return sample(false,CARRY_SECONDS); }
    private TabletThirdPersonAnimation() {}
    static Pose sample(boolean idle,double seconds) {
        Clip clip=CLIPS.get(idle ? "questplus.third_person_idle" : "questplus.third_person_open");
        double time=idle ? Math.max(0,seconds)%clip.length() : Math.clamp(seconds,0,clip.length());
        Bone right=clip.bones().get("rightArm"),left=clip.bones().get("leftArm"),head=clip.bones().get("head"),tablet=clip.bones().get("tablet_root");
        return new Pose(right.rotation().sample(time),right.position().sample(time),left.rotation().sample(time),left.position().sample(time),head.rotation().sample(time),
            tablet.position().sample(time),tablet.rotation().sample(time),tablet.scale().sample(time));
    }
    private static float mix(float a,float b,double t) { return (float)(a+(b-a)*t); }
    private static Vector vector(Vector a,Vector b,double t) {
        return new Vector(mix(a.x(),b.x(),t),mix(a.y(),b.y(),t),mix(a.z(),b.z(),t));
    }
    private static Channel channel(JsonObject bone,String name,Vector fallback) {
        List<Frame> frames=new ArrayList<>();
        if (bone.has(name)) for (var entry:bone.getAsJsonObject(name).entrySet()) {
            var v=entry.getValue().getAsJsonArray();
            if (v.size()!=3) throw new IllegalStateException("Invalid third-person animation channel "+name);
            frames.add(new Frame(Double.parseDouble(entry.getKey()),new Vector(v.get(0).getAsFloat(),v.get(1).getAsFloat(),v.get(2).getAsFloat())));
        }
        frames.sort(Comparator.comparingDouble(Frame::time));
        return new Channel(List.copyOf(frames),fallback);
    }
    private static Map<String,Clip> load() {
        try (var stream=TabletThirdPersonAnimation.class.getResourceAsStream("/assets/questplus/animations/third_person.json")) {
            if (stream==null) throw new IOException("Missing third-person Blockbench animation");
            var animations=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("animations");
            Map<String,Clip> clips=new HashMap<>();
            for (var entry:animations.entrySet()) {
                var definition=entry.getValue().getAsJsonObject();
                double length=definition.get("animation_length").getAsDouble();
                if (!Double.isFinite(length) || length<=0) throw new IOException("Invalid animation duration: "+entry.getKey());
                Map<String,Bone> bones=new HashMap<>();
                for (var b:definition.getAsJsonObject("bones").entrySet()) {
                    var value=b.getValue().getAsJsonObject();
                    bones.put(b.getKey(),new Bone(channel(value,"rotation",ZERO),channel(value,"position",ZERO),channel(value,"scale",ONE)));
                }
                for (String bone:List.of("rightArm","leftArm","head","tablet_root"))
                    if (!bones.containsKey(bone)) throw new IOException("Missing animation bone: "+bone);
                clips.put(entry.getKey(),new Clip(length,Map.copyOf(bones)));
            }
            for (String name:List.of("questplus.third_person_open","questplus.third_person_idle","questplus.third_person_close"))
                if (!clips.containsKey(name)) throw new IOException("Missing animation: "+name);
            return Map.copyOf(clips);
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("Cannot load third-person Blockbench animation",error);
        }
    }
}

package dev.questplus.fabric.client;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import org.joml.Matrix4f;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Small Bedrock geometry reader for QuestPlus's first-person tablet.
 * It renders only the tablet subtree. Minecraft provides the player arms.
 */
final class TabletNativeGeometry {
	enum Pass { BODY, SCREEN, EFFECTS }

	private static final String[] FACES = {"north", "south", "east", "west", "up", "down"};
	private static final Map<String, Bone> BONES = read();
	private static final Bone ROOT = requireBone("tablet_root");
	private static final int TEXTURE_SIZE = 256;

	private record Cube(float x, float y, float z, float dx, float dy, float dz,
			float px, float py, float pz, float rx, float ry, float rz,
			float inflate, boolean mirror, float[][] uvs) {}
	private static final class Bone {
		final String name, parent;
		final float[] pivot, rotation;
		final List<Cube> cubes = new ArrayList<>();
		final List<Bone> children = new ArrayList<>();
		Bone(String name, String parent, float[] pivot, float[] rotation) {
			this.name = name;
			this.parent = parent;
			this.pivot = pivot;
			this.rotation = rotation;
		}
	}
	private TabletNativeGeometry() {}

	static int boneCount() { return BONES.size(); }
	static boolean hasScreen() { return BONES.containsKey("screen") && BONES.get("screen").cubes.size() == 1; }

	private static Bone requireBone(String name) {
		Bone b = BONES.get(name);
		if (b == null) throw new IllegalStateException("Missing native tablet bone " + name);
		return b;
	}

	static void render(PoseStack pose, VertexConsumer vertices, int light, Pass pass,
			String clip, double seconds, boolean surface) {
		renderBone(pose, vertices, light, ROOT, pass, clip, seconds, surface);
	}

	private static void renderBone(PoseStack pose, VertexConsumer vertices, int light, Bone bone,
			Pass pass, String clip, double seconds, boolean surface) {
		pose.pushPose();
		applyAnimatedBone(pose, bone, clip, seconds);
		if (pass == Pass.BODY && "screen".equals(bone.name) && surface)
			TabletFaceAnchor.capture(pose);
		if (pass == Pass.EFFECTS && "tablet_root".equals(bone.name)) {
			pose.pushPose();
			pose.translate(bone.pivot[0] / 16f, bone.pivot[1] / 16f, bone.pivot[2] / 16f);
			pose.scale(.65f, .65f, .65f);
			TabletIdleEffects.draw(pose.last(), vertices, TabletIdleEffects.frame());
			pose.popPose();
		}
		if (pass != Pass.EFFECTS) {
			boolean screenBone = "screen".equals(bone.name);
			if ((pass == Pass.SCREEN && screenBone && surface)
					|| (pass == Pass.BODY && (!screenBone || !surface))) {
				for (Cube cube : bone.cubes)
					drawCube(pose, vertices, pass == Pass.SCREEN, cube,
							pass == Pass.SCREEN && TabletShaderCompat.illuminatedScreen()
									? LightCoordsUtil.FULL_BRIGHT : light);
			}
		}
		for (Bone child : bone.children)
			renderBone(pose, vertices, light, child, pass, clip, seconds, surface);
		pose.popPose();
	}

	private static void applyAnimatedBone(PoseStack pose, Bone bone, String clip, double seconds) {
		var pos = clip == null || !("tablet_root".equals(bone.name))
				? TabletFirstPersonAnimation.Vector.ZERO
				: TabletFirstPersonAnimation.boneValue(clip, seconds, bone.name, "position");
		var rot = clip == null || !("tablet_root".equals(bone.name))
				? TabletFirstPersonAnimation.Vector.ZERO
				: TabletFirstPersonAnimation.boneValue(clip, seconds, bone.name, "rotation");
		var scale = clip == null || !("tablet_root".equals(bone.name))
				? TabletFirstPersonAnimation.Vector.ONE
				: TabletFirstPersonAnimation.boneValue(clip, seconds, bone.name, "scale");
		pose.translate(-pos.x() / 16f, pos.y() / 16f, pos.z() / 16f);
		pose.translate(bone.pivot[0] / 16f, bone.pivot[1] / 16f, bone.pivot[2] / 16f);
		pose.mulPose(new Matrix4f().rotationZYX((float) Math.toRadians(rot.z() + bone.rotation[2]),
				(float) -Math.toRadians(rot.y() + bone.rotation[1]),
				(float) -Math.toRadians(rot.x() + bone.rotation[0])));
		pose.scale(scale.x(), scale.y(), scale.z());
		pose.translate(-bone.pivot[0] / 16f, -bone.pivot[1] / 16f, -bone.pivot[2] / 16f);
	}

	private static void drawCube(PoseStack pose, VertexConsumer vertices, boolean screen, Cube c, int light) {
		if (c.dx <= 0 || c.dy <= 0 || c.dz <= 0) return;
		pose.pushPose();
		pose.translate(c.px / 16f, c.py / 16f, c.pz / 16f);
		pose.mulPose(new Matrix4f().rotationZYX(c.rz, c.ry, c.rx));
		pose.translate(-c.px / 16f, -c.py / 16f, -c.pz / 16f);
		float x0 = (c.x - c.inflate) / 16f, y0 = (c.y - c.inflate) / 16f, z0 = (c.z - c.inflate) / 16f;
		float x1 = (c.x + c.dx + c.inflate) / 16f;
		float y1 = (c.y + c.dy + c.inflate) / 16f;
		float z1 = (c.z + c.dz + c.inflate) / 16f;
		if (screen) {
			// Wound towards the viewer: the true-emissive pipeline culls back faces.
			face(vertices, pose.last(), x1,y0,z0, x0,y0,z0, x0,y1,z0, x1,y1,z0,
					0,0,-1, new float[] {
					TabletSurfaceRenderer.screenX() + TabletSurfaceRenderer.screenWidth(),
					TabletSurfaceRenderer.atlasHeight() - TabletSurfaceRenderer.screenHeight(),
					-TabletSurfaceRenderer.screenWidth(), TabletSurfaceRenderer.screenHeight()
					}, TabletSurfaceRenderer.atlasWidth(), TabletSurfaceRenderer.atlasHeight(), light, true);
		} else {
			float[][] uv=c.uvs;
			face(vertices,pose.last(),x0,y0,z0,x1,y0,z0,x1,y1,z0,x0,y1,z0,0,0,-1,uv[0],TEXTURE_SIZE,TEXTURE_SIZE,light,c.mirror);
			face(vertices,pose.last(),x1,y0,z1,x0,y0,z1,x0,y1,z1,x1,y1,z1,0,0,1,uv[1],TEXTURE_SIZE,TEXTURE_SIZE,light,c.mirror);
			face(vertices,pose.last(),x1,y0,z0,x1,y0,z1,x1,y1,z1,x1,y1,z0,1,0,0,uv[2],TEXTURE_SIZE,TEXTURE_SIZE,light,c.mirror);
			face(vertices,pose.last(),x0,y0,z1,x0,y0,z0,x0,y1,z0,x0,y1,z1,-1,0,0,uv[3],TEXTURE_SIZE,TEXTURE_SIZE,light,c.mirror);
			face(vertices,pose.last(),x0,y0,z1,x1,y0,z1,x1,y0,z0,x0,y0,z0,0,-1,0,uv[4],TEXTURE_SIZE,TEXTURE_SIZE,light,c.mirror);
			face(vertices,pose.last(),x0,y1,z0,x1,y1,z0,x1,y1,z1,x0,y1,z1,0,1,0,uv[5],TEXTURE_SIZE,TEXTURE_SIZE,light,c.mirror);
		}
		pose.popPose();
	}

	private static void face(VertexConsumer out, PoseStack.Pose pose,
			float ax,float ay,float az,float bx,float by,float bz,
			float cx,float cy,float cz,float dx,float dy,float dz,
			float nx,float ny,float nz,float[] uv,float w,float h,int light,boolean mirror) {
		if (uv == null) return;
		float u0=uv[0]/w, v0=uv[1]/h, u1=(uv[0]+uv[2])/w, v1=(uv[1]+uv[3])/h;
		if (mirror) {float tmp=u0;u0=u1;u1=tmp;}
		vertex(out,pose,ax,ay,az,u0,v0,nx,ny,nz,light);
		vertex(out,pose,bx,by,bz,u1,v0,nx,ny,nz,light);
		vertex(out,pose,cx,cy,cz,u1,v1,nx,ny,nz,light);
		vertex(out,pose,dx,dy,dz,u0,v1,nx,ny,nz,light);
	}

	private static void vertex(VertexConsumer out, PoseStack.Pose pose,float x,float y,float z,
			float u,float v,float nx,float ny,float nz,int light) {
		out.addVertex(pose,x,y,z).setColor(-1).setUv(u,v)
				.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose,nx,ny,nz);
	}

	private static float[] vec(JsonObject obj,String key) {
		if (!obj.has(key)) return new float[] {0,0,0};
		var a=obj.getAsJsonArray(key);
		return new float[] {a.get(0).getAsFloat(),a.get(1).getAsFloat(),a.get(2).getAsFloat()};
	}
	private static float num(JsonObject obj,String key,float fallback) {
		return obj.has(key) ? obj.get(key).getAsFloat() : fallback;
	}
	private static float[] uv(JsonObject entry) {
		if (entry == null || !entry.has("uv") || !entry.has("uv_size")) return null;
		var a=entry.getAsJsonArray("uv");var b=entry.getAsJsonArray("uv_size");
		return new float[] {a.get(0).getAsFloat(),a.get(1).getAsFloat(),b.get(0).getAsFloat(),b.get(1).getAsFloat()};
	}
	private static Map<String,Bone> read() {
		try (var stream=TabletNativeGeometry.class.getResourceAsStream(
				"/assets/questplus/fp_models/questplus_first_person.geo.json")) {
			if (stream==null) throw new IllegalStateException("Missing tablet geometry");
			var geo=JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
					.getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
			var map=new LinkedHashMap<String,Bone>();
			for (var el:geo.getAsJsonArray("bones")) {
				var obj=el.getAsJsonObject();
				String name=obj.get("name").getAsString();
				var pivot=vec(obj,"pivot");
				pivot[0]=-pivot[0];
				Bone bone=new Bone(name,obj.has("parent")?obj.get("parent").getAsString():null,pivot,vec(obj,"rotation"));
				if (obj.has("cubes")) for (var ce:obj.getAsJsonArray("cubes")) {
					var co=ce.getAsJsonObject();
					var origin=vec(co,"origin");
					var size=vec(co,"size");
					var cp=vec(co,"pivot"); cp[0]=-cp[0];
					var cr=vec(co,"rotation");
					float[][] uvs=new float[6][];
					var uvEl=co.get("uv");
					if (uvEl!=null && uvEl.isJsonObject()) {
						var o=uvEl.getAsJsonObject();
						for(int i=0;i<FACES.length;i++)
							uvs[i]=o.has(FACES[i])?uv(o.getAsJsonObject(FACES[i])):null;
					} else if (uvEl!=null && uvEl.isJsonArray()) {
						var u=uvEl.getAsJsonArray();
						for(int i=0;i<6;i++)
							uvs[i]=new float[] {u.get(0).getAsFloat(),u.get(1).getAsFloat(),
									size[0],size[1]};
					}
					bone.cubes.add(new Cube(-(origin[0]+size[0]),origin[1],origin[2],
							size[0],size[1],size[2],cp[0],cp[1],cp[2],
							(float)-Math.toRadians(cr[0]),(float)-Math.toRadians(cr[1]),(float)Math.toRadians(cr[2]),
							num(co,"inflate",0),co.has("mirror")&&co.get("mirror").getAsBoolean(),uvs));
				}
				map.put(name,bone);
			}
			for (Bone bone:map.values()) if (bone.parent!=null && map.containsKey(bone.parent))
				map.get(bone.parent).children.add(bone);
			return Collections.unmodifiableMap(map);
		} catch (Exception e) {throw new IllegalStateException("Unable to load native tablet rig",e);}
	}
}

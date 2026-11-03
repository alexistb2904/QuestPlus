package dev.questplus.fabric.client;

import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerModelPart;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Native picture-in-picture model; the UI is composited over the blank 20×12 display. */
public final class TabletRenderer {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("questplus", "textures/entity/tablet.png");
    private static final Model.Simple MODEL = load();
    private static final Model.Simple[][] ARMS = {{arms(false,0),arms(false,1),arms(false,2),arms(false,3)},
        {arms(true,0),arms(true,1),arms(true,2),arms(true,3)}};
    private TabletRenderer() {}

    /** The requested aperture is 5:3; the caller must draw the UI after this method. */
    public static void draw(GuiGraphicsExtractor graphics, int left, int top, int right, int bottom) {
        int width = right - left, height = bottom - top;
        if (width <= 0 || height <= 0) return;
        int marginX = Math.max(4, (int) Math.ceil(width * .13));
        int marginY = Math.max(4, (int) Math.ceil(height * .24));
        var minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            var skin = minecraft.player.getSkin();
            boolean slim = skin.model() == PlayerModelType.SLIM;
            int sleeves = (minecraft.options.isModelPartEnabled(PlayerModelPart.LEFT_SLEEVE) ? 1 : 0)
                | (minecraft.options.isModelPartEnabled(PlayerModelPart.RIGHT_SLEEVE) ? 2 : 0);
            var arms = ARMS[slim ? 1 : 0][sleeves];
            int armBottom = bottom + (int)(height * .9);
            arms.root().y = 25.616f - (armBottom - (top + bottom) / 2f) * 16f / (width * .8f);
            graphics.skin(arms, skin.body().texturePath(), width * .8f, 0, 0, 0,
                left - marginX * 2, top - marginY, right + marginX * 2, armBottom);
            graphics.nextStratum();
        }
        MODEL.root().y = 25.616f - (height + 2 * marginY) * 8f / (width * .8f);
        graphics.skin(MODEL, TEXTURE, width * .8f, 0, 0, 0,
            left - marginX, top - marginY, right + marginX, bottom + marginY);
        graphics.nextStratum();
    }

    private static Model.Simple arms(boolean slim, int sleeves) {
        Map<String,ModelPart> children = new HashMap<>();
        float width = slim ? 3 : 4;
        for (int side : new int[]{-1,1}) {
            boolean left = side < 0;
            List<ModelPart.Cube> cubes = new ArrayList<>();
            cubes.add(new ModelPart.Cube(left ? 32 : 40, left ? 48 : 16, -width/2,0,-2,width,12,4,0,0,0,false,64,64,EnumSet.allOf(Direction.class)));
            if ((sleeves & (left ? 1 : 2)) != 0) cubes.add(new ModelPart.Cube(left ? 48 : 40, left ? 48 : 32, -width/2,0,-2,width,12,4,.25f,.25f,.25f,false,64,64,EnumSet.allOf(Direction.class)));
            var arm = new ModelPart(cubes,Map.of());
            arm.setPos(side * 11,2,-1); arm.zRot = (float)Math.toRadians(-side * 20);
            children.put(left ? "left" : "right", arm);
        }
        return new Model.Simple(new ModelPart(List.of(),children), RenderTypes::entityCutout);
    }

    private static Model.Simple load() {
        try (var stream = TabletRenderer.class.getResourceAsStream("/assets/questplus/models/tablet/geometry.json")) {
            if (stream == null) throw new IOException("Missing tablet geometry");
            var elements = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonArray();
            Map<String,ModelPart> children = new LinkedHashMap<>();
            int index = 0;
            for (var element : elements) {
                var e = element.getAsJsonObject(); var from = e.getAsJsonArray("from"); var to = e.getAsJsonArray("to");
                var origin = e.getAsJsonArray("origin");
                float ox = value(origin,0), oy = -value(origin,1), oz = value(origin,2);
                float x = value(from,0)-ox, y = -value(to,1)-oy, z = value(from,2)-oz;
                float sx = value(to,0)-value(from,0), sy = value(to,1)-value(from,1), sz = value(to,2)-value(from,2);
                var cube = new ModelPart.Cube(0,0,x,y,z,sx,sy,sz,0,0,0,false,256,256,EnumSet.allOf(Direction.class));
                var faces = e.getAsJsonObject("faces");
                for (var polygon : cube.polygons) {
                    var normal = polygon.normal();
                    String face = normal.x() > .5 ? "east" : normal.x() < -.5 ? "west" : normal.y() > .5 ? "down" : normal.y() < -.5 ? "up" : normal.z() > .5 ? "south" : "north";
                    var uv = faces.getAsJsonObject(face).getAsJsonArray("uv");
                    float u1=value(uv,0)/256, v1=value(uv,1)/256, u2=value(uv,2)/256, v2=value(uv,3)/256;
                    var v=polygon.vertices();
                    v[0]=v[0].remap(u2,v1); v[1]=v[1].remap(u1,v1); v[2]=v[2].remap(u1,v2); v[3]=v[3].remap(u2,v2);
                }
                var part = new ModelPart(List.of(cube),Map.of()); part.setPos(ox,oy,oz);
                if (e.has("rotation")) {
                    var rotation=e.getAsJsonArray("rotation");
                    part.setRotation((float)Math.toRadians(-value(rotation,0)),(float)Math.toRadians(value(rotation,1)),(float)Math.toRadians(-value(rotation,2)));
                }
                children.put(Integer.toString(index++),part);
            }
            var root = new ModelPart(List.of(),children);
            root.setPos(0,25.616f,0);
            return new Model.Simple(root, RenderTypes::entityCutout);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Cannot load Quest Plus tablet model",e);
        }
    }
    private static float value(JsonArray array, int index) { return array.get(index).getAsFloat(); }
    static Model.Simple worldModel() { var model = load(); model.root().setPos(0,0,0); return model; }
    static Identifier texture() { return TEXTURE; }
}

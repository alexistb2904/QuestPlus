package dev.questplus.fabric.client;

import com.mojang.blaze3d.vertex.*;
import com.google.gson.*;
import com.mojang.math.Axis;
import dev.questplus.fabric.QuestLootChest;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.*;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.*;
import net.minecraft.core.particles.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.*;
import net.minecraft.util.*;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.*;

import static dev.questplus.fabric.QuestLootChest.*;

public final class QuestLootChestRenderer implements BlockEntityRenderer<Stage, QuestLootChestRenderer.State> {
	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("questplus",
			"textures/block/loot_chest.png");
	static final int[] COLORS = { 0xE8E8E8, 0x5BE35B, 0x4AA8FF, 0xC45BFF, 0xFFB020 };
	private static final Identifier GLOW = Identifier.fromNamespaceAndPath("questplus",
			"textures/entity/tablet_light.png");
	private static final Identifier MODEL = Identifier.fromNamespaceAndPath("questplus", "models/item/loot_crate.json");
	private static final String[] FACES = { "north", "south", "east", "west", "up", "down" };
	private static final float ORB_TOP = 1.85f;
	private final Font font;
	private final ItemModelResolver resolver;
	private final List<Box> base = new ArrayList<>(), lid = new ArrayList<>(), gems = new ArrayList<>();

	record Box(float[] from, float[] to, float[][] uv) {
	}

	public static final class State extends BlockEntityRenderState {
		float t;
		int rarity, facing;
		long seed;
		List<ItemStackRenderState> items = List.of();
		Vec3 target;
		FormattedCharSequence title, name;
	}

	private QuestLootChestRenderer(BlockEntityRendererProvider.Context context) {
		font = context.font();
		resolver = context.itemModelResolver();
		try (var reader = Minecraft.getInstance().getResourceManager().getResourceOrThrow(MODEL).openAsReader()) {
			for (var box : load(JsonParser.parseReader(reader).getAsJsonObject()).entrySet())
				(box.getKey().startsWith("lid") ? lid : box.getKey().startsWith("gem") ? gems : base)
						.add(box.getValue());
		} catch (Exception error) {
			dev.questplus.fabric.QuestPlusMod.LOGGER.warn("Cannot load loot crate model: {}", error.getMessage());
		}
	}

	static Map<String, Box> load(JsonObject model) {
		var boxes = new LinkedHashMap<String, Box>();
		var elements = model.getAsJsonArray("elements");
		for (int i = 0; i < elements.size(); i++) {
			var element = elements.get(i).getAsJsonObject();
			var faces = element.getAsJsonObject("faces");
			var uv = new float[FACES.length][];
			for (int f = 0; f < FACES.length; f++)
				if (faces.has(FACES[f])) {
					var array = faces.getAsJsonObject(FACES[f]).getAsJsonArray("uv");
					uv[f] = new float[4];
					for (int k = 0; k < 4; k++)
						uv[f][k] = array.get(k).getAsFloat() / 16;
				}
			String name = element.has("name") ? element.get("name").getAsString() : "#" + i;
			boxes.put(boxes.containsKey(name) ? name + "#" + i : name,
					new Box(floats(element.getAsJsonArray("from")), floats(element.getAsJsonArray("to")), uv));
		}
		return boxes;
	}

	private static float[] floats(JsonArray array) {
		return new float[] { array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat() };
	}

	static void register() {
		BlockEntityRendererRegistry.register(QuestLootChest.ENTITY, QuestLootChestRenderer::new);
		QuestLootChest.clientTicker = QuestLootChestRenderer::tick;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(Stage be, State state, float delta, Vec3 camera,
			ModelFeatureRenderer.CrumblingOverlay overlay) {
		BlockEntityRenderer.super.extractRenderState(be, state, delta, camera, overlay);
		var level = be.getLevel();
		state.t = level == null || be.start < 0 ? -1 : level.getGameTime() - be.start + delta;
		state.rarity = be.rarity;
		state.facing = be.facing;
		state.seed = be.getBlockPos().asLong();
		var items = new ArrayList<ItemStackRenderState>();
		for (var stack : be.items) {
			var item = new ItemStackRenderState();
			resolver.updateForTopItem(item, stack, ItemDisplayContext.FIXED, level, null, items.size());
			items.add(item);
		}
		state.items = items;
		var owner = level == null || be.owner == null ? null : level.getPlayerByUUID(be.owner);
		state.target = owner == null ? null
				: owner.getPosition(delta).add(0, owner.getEyeHeight() * .7, 0)
						.subtract(Vec3.atLowerCornerOf(be.getBlockPos()));
		if (state.target != null && state.target.distanceToSqr(.5, 1, .5) > 24 * 24)
			state.target = null;
		state.title = Component.translatable("questplus.rarity." + be.rarity).withStyle(s -> s.withBold(true))
				.getVisualOrderText();
		state.name = (be.items.isEmpty() ? Component.translatable("questplus.crate.empty")
				: be.items.size() == 1 ? be.items.getFirst().getHoverName()
						: Component.empty().append(be.items.getFirst().getHoverName())
								.append(" +" + (be.items.size() - 1))).getVisualOrderText();
	}

	@Override
	public void submit(State state, PoseStack pose, SubmitNodeCollector queue, CameraRenderState camera) {
		float t = state.t;
		if (t < 0 || t > DURATION)
			return;
		int color = tierColor(t, state.rarity);
		pose.pushPose();
		pose.translate(.5, 0, .5);
		chest(state, pose, queue, t, color);
		if (t >= OPEN && t < BURST + 6)
			beam(pose, queue, t, color);
		if (t >= OPEN + 2 && t < BURST)
			orb(state, pose, queue, t, color);
		if (t >= BURST && t < BURST + 12)
			shockwave(pose, queue, t, color, !state.items.isEmpty());
		if (t >= BURST)
			loot(state, pose, queue, camera, t, color);
		pose.popPose();
	}

	private void chest(State state, PoseStack pose, SubmitNodeCollector queue, float t, int color) {
		float shake = t >= 8 && t < OPEN ? sq((t - 8) / (OPEN - 8))
				: t >= OPEN && t < OPEN + 5 ? 1 - (t - OPEN) / 5 : 0;
		float sink = t > COLLECT + 15 ? clamp((t - COLLECT - 15) / (DURATION - COLLECT - 15)) : 0;
		float drop = t < 8 ? (1 - bounce(t / 8)) * .9f : 0;
		float squash = t >= 7 && t < 12 ? 1 - .12f * (float) Math.sin((t - 7) / 5 * Math.PI) : 1;
		pose.pushPose();
		pose.translate(0, drop + Math.max(0, Math.sin(t * 1.3)) * shake * .05 - sink * .35, 0);
		pose.last().rotate(Axis.YP.rotationDegrees(180 - Direction.from2DDataValue(state.facing).toYRot()));
		pose.last().rotate(Axis.ZP.rotationDegrees((float) Math.sin(t * 2.9) * shake * 7));
		pose.last().rotate(Axis.XP.rotationDegrees((float) Math.cos(t * 3.7) * shake * 4));
		float size = 1 - sq(sink) * .9f;
		pose.scale(size * (2 - squash), size * squash, size * (2 - squash));
		pose.translate(-.5, 0, -.5);
		pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
		float lidAngle = t < OPEN ? shake * 9 * Math.abs((float) Math.sin(t * 1.7))
				: t < COLLECT + 5 ? 105 * backOut(clamp((t - OPEN) / 6)) : 105 * (1 - sq(clamp((t - COLLECT - 5) / 8)));
		float glow = t < OPEN ? shake * .7f : t < BURST ? 1 : Math.max(0, 1 - (t - BURST) / 30);
		int light = state.lightCoords, seam = t < OPEN ? 0xFFFFF4D0 : 0xFF000000 | color;
		queue.submitCustomGeometry(pose, RenderTypes.entityCutout(TEXTURE), (p, v) -> {
			for (var box : base)
				box(p, v, box, light, -1);
			for (var box : gems)
				box(p, v, box, LightCoordsUtil.FULL_BRIGHT, seam);
		});
		if (glow > 0)
			queue.submitCustomGeometry(pose, glow(), (p, v) -> {
				int c = argb(glow * .8f, seam);
				glow(p, v, 2.5f, 8.9f, 3.5f, c);
				glow(p, v, 2.5f, 8.9f, 12.5f, c);
				glow(p, v, 13.5f, 8.9f, 12.5f, c);
				glow(p, v, 13.5f, 8.9f, 3.5f, c);
			});
		pose.translate(0, 9, 14);
		pose.last().rotate(Axis.XP.rotationDegrees(lidAngle));
		pose.translate(0, -9, -14);
		queue.submitCustomGeometry(pose, RenderTypes.entityCutout(TEXTURE), (p, v) -> {
			for (var box : lid)
				box(p, v, box, light, -1);
		});
		pose.popPose();
	}

	private static void beam(PoseStack pose, SubmitNodeCollector queue, float t, int color) {
		float k = clamp((t - OPEN) / 6) * (t > BURST ? 1 - (t - BURST) / 6 : 1), w = .2f * k, top = .55f + 3.2f * k;
		queue.submitCustomGeometry(pose, glow(), (p, v) -> {
			int base = argb(.55f * k, color), fade = argb(0, color);
			for (int i = 0; i < 2; i++) {
				float dx = i == 0 ? w : 0, dz = i == 0 ? 0 : w;
				glow(p, v, -dx, .55f, -dz, base);
				glow(p, v, dx, .55f, dz, base);
				glow(p, v, dx, top, dz, fade);
				glow(p, v, -dx, top, -dz, fade);
			}
		});
	}

	private static void orb(State state, PoseStack pose, SubmitNodeCollector queue, float t, int color) {
		float p = clamp((t - OPEN - 2) / (BURST - OPEN - 2)), charge = smooth((t - (BURST - 9)) / 8);
		float y = .6f + (ORB_TOP - .6f) * cubicOut(clamp(p * 1.4f));
		float r = (.08f + .3f * cubicOut(p)) * (1 - .4f * charge) * (1 + .06f * (float) Math.sin(t * 1.9));
		var random = new Random(state.seed);
		float jx = charge * .04f * (float) Math.sin(t * 13), jz = charge * .04f * (float) Math.cos(t * 11);
		pose.pushPose();
		pose.translate(jx, y, jz);
		queue.submitCustomGeometry(pose, glow(), (pp, v) -> {
			sphere(pp, v, r * .55f, 0xF0FFFFFF);
			sphere(pp, v, r, argb(.55f, color));
			sphere(pp, v, r * 1.6f, argb(.16f, color));
		});
		pose.last().rotate(Axis.YP.rotationDegrees(t * 3));
		pose.last().rotate(Axis.XP.rotationDegrees(t * 1.3f));
		int rays = 5 + state.rarity * 3;
		float length = (.4f + .25f * state.rarity) * cubicOut(p) * (1 + charge * .8f);
		queue.submitCustomGeometry(pose, glow(), (pp, v) -> {
			for (int i = 0; i < rays; i++) {
				float yy = 1 - 2 * (i + .5f) / rays, ring = (float) Math.sqrt(1 - yy * yy), a = i * 2.39996f;
				var dir = new org.joml.Vector3f(ring * (float) Math.cos(a), yy, ring * (float) Math.sin(a));
				float len = length * (.7f + .6f * random.nextFloat()) * (1 + .15f * (float) Math.sin(t * .7 + i));
				ray(pp, v, dir, len, .035f + .015f * state.rarity, color);
			}
		});
		pose.popPose();
	}

	private static void shockwave(PoseStack pose, SubmitNodeCollector queue, float t, int color, boolean loot) {
		float u = (t - BURST) / 12, fast = clamp((t - BURST) / 5);
		pose.pushPose();
		pose.translate(0, ORB_TOP, 0);
		float alpha = .6f * sq(1 - u);
		// Shader packs boost faint beacon alpha: the fading tail of a dome that can
		// enclose the camera became a veil.
		boolean shell = alpha >= .1f || !TabletShaderCompat.shadersActive();
		queue.submitCustomGeometry(pose, glow(), (p, v) -> {
			if (shell)
				sphere(p, v, .3f + (loot ? 2.6f : .9f) * cubicOut(u), argb(alpha, color));
			if (fast < 1)
				sphere(p, v, .2f + 1.2f * cubicOut(fast), argb(.9f * (1 - fast), 0xFFFFFF));
		});
		pose.popPose();
	}

	private void loot(State state, PoseStack pose, SubmitNodeCollector queue, CameraRenderState camera, float t,
			int color) {
		int n = state.items.size();
		float appear = backOut(clamp((t - BURST) / 8)), fly = clamp((t - COLLECT) / 8);
		if (fly < 1)
			for (int i = 0; i < n; i++) {
				double a = i * Math.PI * 2 / n + t * .02, radius = n == 1 ? 0 : .45 + .05 * n;
				var at = new Vec3(Math.cos(a) * radius, ORB_TOP + .08 * Math.sin(t * .12 + i), Math.sin(a) * radius);
				if (state.target != null)
					at = at.lerp(state.target, cubicIn(fly));
				else
					at = at.add(0, fly * .6, 0);
				float scale = (n == 1 ? .85f : .55f) * appear * (1 - .8f * fly);
				pose.pushPose();
				pose.translate(at.x, at.y, at.z);
				pose.last().rotate(Axis.YP.rotationDegrees(t * 4 + i * 45));
				pose.scale(scale, scale, scale);
				state.items.get(i).submit(pose, queue, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
				pose.popPose();
			}
		float text = clamp((t - BURST - 4) / 6) * (1 - clamp((t - COLLECT + 4) / 4));
		if (text <= 0)
			return;
		pose.pushPose();
		pose.translate(0, ORB_TOP + .95, 0);
		pose.last().rotate(camera.orientation);
		pose.scale(.025f, -.025f, .025f);
		int alpha = Math.max(5, (int) (text * 255)) << 24;
		if (n > 0)
			queue.submitText(pose, -font.width(state.title) / 2f, 0, state.title, false, Font.DisplayMode.NORMAL,
					LightCoordsUtil.FULL_BRIGHT, alpha | color, 0, 0);
		queue.submitText(pose, -font.width(state.name) / 2f, 11, state.name, false, Font.DisplayMode.NORMAL,
				LightCoordsUtil.FULL_BRIGHT, alpha | 0xFFFFFF, (int) (text * 0x50) << 24, 0);
		pose.popPose();
	}

	static void tick(Level level, BlockPos pos, BlockState block, Stage be) {
		if (be.start < 0)
			return;
		int age = (int) (level.getGameTime() - be.start);
		for (int a = Math.max(be.clientAge + 1, age - 10); a <= age; a++)
			beat(level, pos, be, a);
		be.clientAge = Math.max(be.clientAge, age);
	}

	private static void beat(Level level, BlockPos pos, Stage be, int a) {
		var random = level.getRandom();
		double x = pos.getX() + .5, y = pos.getY(), z = pos.getZ() + .5, orb = y + ORB_TOP;
		int color = tierColor(a, be.rarity);
		if (a == 0)
			sound(level, x, y, z, SoundEvents.WOOD_PLACE, 1, .6f);
		if (a == 8)
			for (int i = 0; i < 10; i++)
				level.addParticle(ParticleTypes.POOF, x + random.nextGaussian() * .4, y + .1,
						z + random.nextGaussian() * .4, 0, .02, 0);
		if (a >= 8 && a < OPEN && (a - 8) % 5 == 0)
			sound(level, x, y, z, SoundEvents.NOTE_BLOCK_HAT.value(), .8f, .7f + (a - 8f) / (OPEN - 8) * 1.1f);
		if (a >= 20 && a < OPEN && a % 3 == 0)
			level.addParticle(ParticleTypes.END_ROD, x + random.nextGaussian() * .3, y + .6,
					z + random.nextGaussian() * .3, 0, .03, 0);
		if (a == OPEN) {
			sound(level, x, y, z, SoundEvents.CHEST_OPEN, 1, .8f);
			sound(level, x, y, z, SoundEvents.VAULT_OPEN_SHUTTER, 1, 1);
			for (int i = 0; i < 14; i++)
				level.addParticle(ParticleTypes.END_ROD, x, y + .6, z, random.nextGaussian() * .05,
						.15 + random.nextFloat() * .1, random.nextGaussian() * .05);
		}
		if (a == OPEN + 2)
			sound(level, x, y, z, SoundEvents.BEACON_ACTIVATE, .6f, 1.4f);
		if (a > OPEN && a < BURST)
			for (int i = 0; i < 2; i++) {
				double angle = a * .6 + i * Math.PI, r = 1.2 - (a - OPEN) / (double) (BURST - OPEN);
				dust(level, color, 1.2f, x + Math.cos(angle) * r, y + .6 + (orb - y - .6) * (a - OPEN) / (BURST - OPEN),
						z + Math.sin(angle) * r);
			}
		for (int k = 1; k <= be.rarity; k++)
			if (a == step(k)) {
				sound(level, x, orb, z, SoundEvents.NOTE_BLOCK_BELL.value(), 1, .7f + .2f * k);
				sound(level, x, orb, z, SoundEvents.AMETHYST_BLOCK_CHIME, 1, .8f + .15f * k);
				for (int i = 0; i < 12; i++)
					dust(level, COLORS[k], 1.6f, x + random.nextGaussian() * .35, orb + random.nextGaussian() * .35,
							z + random.nextGaussian() * .35);
			}
		if (a == BURST - 8)
			sound(level, x, orb, z, SoundEvents.BEACON_POWER_SELECT, .9f, 1.6f);
		if (a == BURST)
			burst(level, be, x, orb, z, color);
		if (a > BURST && a < COLLECT && a % 3 == 0 && !be.items.isEmpty())
			level.addParticle(random.nextBoolean() ? ParticleTypes.WAX_ON : ParticleTypes.END_ROD,
					x + random.nextGaussian() * .5, orb + random.nextGaussian() * .3, z + random.nextGaussian() * .5, 0,
					0, 0);
		if (a == BURST + 10 && be.rarity >= 2)
			sound(level, x, orb, z, SoundEvents.FIREWORK_ROCKET_TWINKLE, 1, 1);
		if (a == COLLECT && !be.items.isEmpty())
			sound(level, x, orb, z, SoundEvents.VAULT_EJECT_ITEM, 1, 1);
		if (a == COLLECT + 8 && !be.items.isEmpty())
			sound(level, x, orb, z, SoundEvents.EXPERIENCE_ORB_PICKUP, .8f, 1.2f);
		if (a == COLLECT + 12)
			sound(level, x, y, z, SoundEvents.CHEST_CLOSE, 1, .9f);
		if (a == DURATION - 3)
			for (int i = 0; i < 14; i++)
				level.addParticle(ParticleTypes.POOF, x + random.nextGaussian() * .3, y + .3,
						z + random.nextGaussian() * .3, 0, .03, 0);
	}

	private static void burst(Level level, Stage be, double x, double y, double z, int color) {
		var random = level.getRandom();
		if (be.items.isEmpty()) {
			sound(level, x, y, z, SoundEvents.ENDER_EYE_DEATH, 1, .8f);
			for (int i = 0; i < 16; i++)
				level.addParticle(ParticleTypes.POOF, x, y, z, random.nextGaussian() * .06, random.nextGaussian() * .06,
						random.nextGaussian() * .06);
			return;
		}
		sound(level, x, y, z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 1.2f, 1);
		sound(level, x, y, z, SoundEvents.GENERIC_EXPLODE.value(), .4f, 1.6f);
		level.addParticle(ColorParticleOption.create(ParticleTypes.FLASH, 0xFF000000 | color), x, y, z, 0, 0, 0);
		for (int i = 0; i < 60 + 30 * be.rarity; i++) {
			var d = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize()
					.scale(.2 + random.nextFloat() * 1.6);
			dust(level, color, 1 + random.nextFloat(), x + d.x, y + d.y, z + d.z);
		}
		for (int i = 0; i < 24; i++)
			level.addParticle(ParticleTypes.FIREWORK, x, y, z, random.nextGaussian() * .15, random.nextGaussian() * .15,
					random.nextGaussian() * .15);
		if (be.rarity >= 3) {
			sound(level, x, y, z, SoundEvents.TOTEM_USE, .5f, 1.2f);
			for (int i = 0; i < 40; i++)
				level.addParticle(ParticleTypes.TOTEM_OF_UNDYING, x, y, z, random.nextGaussian() * .3,
						random.nextFloat() * .4, random.nextGaussian() * .3);
		}
		if (be.rarity == 4)
			sound(level, x, y, z, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, .8f, 1);
	}

	/**
	 * Shader packs light dust like terrain, so it turns black at night; trails are
	 * always full-bright.
	 */
	private static void dust(Level level, int color, float size, double x, double y, double z) {
		level.addParticle(TabletShaderCompat.shadersActive()
				? new TrailParticleOption(new Vec3(x, y + .15, z), color, (int) (14 * size))
				: new DustParticleOptions(color, size), x, y, z, 0, 0, 0);
	}

	private static void sound(Level level, double x, double y, double z, SoundEvent sound, float volume, float pitch) {
		level.playLocalSound(x, y, z, sound, SoundSource.BLOCKS, volume, pitch, false);
	}

	private static float step(int tier) {
		return OPEN + 10 + tier * (BURST - OPEN - 22) / 4f;
	}

	static int tierColor(float t, int rarity) {
		float tier = 0;
		for (int k = 1; k <= rarity; k++)
			tier += clamp((t - step(k)) / 3);
		int low = (int) tier;
		return low >= rarity ? COLORS[rarity] : ARGB.srgbLerp(tier - low, COLORS[low], COLORS[low + 1]) & 0xFFFFFF;
	}

	private static void box(PoseStack.Pose p, VertexConsumer v, Box b, int light, int color) {
		float x0 = b.from[0], y0 = b.from[1], z0 = b.from[2], x1 = b.to[0], y1 = b.to[1], z1 = b.to[2];
		face(p, v, b.uv[0], light, color, 0, 0, -1, x1, y1, z0, x0, y1, z0, x0, y0, z0, x1, y0, z0);
		face(p, v, b.uv[1], light, color, 0, 0, 1, x0, y1, z1, x1, y1, z1, x1, y0, z1, x0, y0, z1);
		face(p, v, b.uv[2], light, color, 1, 0, 0, x1, y1, z1, x1, y1, z0, x1, y0, z0, x1, y0, z1);
		face(p, v, b.uv[3], light, color, -1, 0, 0, x0, y1, z0, x0, y1, z1, x0, y0, z1, x0, y0, z0);
		face(p, v, b.uv[4], light, color, 0, 1, 0, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
		face(p, v, b.uv[5], light, color, 0, -1, 0, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0);
	}

	private static void face(PoseStack.Pose p, VertexConsumer v, float[] uv, int light, int color, float nx, float ny,
			float nz, float... c) {
		if (uv == null)
			return;
		float[] us = { uv[0], uv[2], uv[2], uv[0] }, vs = { uv[1], uv[1], uv[3], uv[3] };
		for (int i = 0; i < 4; i++)
			v.addVertex(p, c[i * 3], c[i * 3 + 1], c[i * 3 + 2]).setColor(color).setUv(us[i], vs[i])
					.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
	}

	/**
	 * Shader packs shade and depth-write the lightning type like a solid surface,
	 * which leaves dark bands around the glow. Spider eyes (and translucent
	 * emissive) are no better: MakeUp draws them as the bare white texture, so the
	 * faint shockwave sphere filled the screen. The translucent beacon beam stays
	 * emissive, tinted and without depth writes in every tested pack.
	 */
	private static RenderType glow() {
		return TabletShaderCompat.shadersActive() ? RenderTypes.beaconBeam(GLOW, true) : RenderTypes.lightning();
	}

	/**
	 * A full entity vertex: the lightning format skips every element it does not
	 * declare.
	 */
	private static void glow(PoseStack.Pose p, VertexConsumer v, float x, float y, float z, int color) {
		v.addVertex(p, x, y, z).setColor(color).setUv(.5f, .5f).setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(p, 0, 1, 0);
	}

	private static void sphere(PoseStack.Pose p, VertexConsumer v, float r, int color) {
		int lat = 8, lon = 14;
		for (int i = 0; i < lat; i++)
			for (int j = 0; j < lon; j++) {
				for (int[] k : new int[][] { { i, j }, { i + 1, j }, { i + 1, j + 1 }, { i, j + 1 } }) {
					double theta = Math.PI * k[0] / lat, phi = Math.PI * 2 * k[1] / lon;
					glow(p, v, (float) (Math.sin(theta) * Math.cos(phi) * r), (float) (Math.cos(theta) * r),
							(float) (Math.sin(theta) * Math.sin(phi) * r), color);
				}
			}
	}

	private static void ray(PoseStack.Pose p, VertexConsumer v, org.joml.Vector3f dir, float length, float width,
			int color) {
		var side = new org.joml.Vector3f(dir).cross(Math.abs(dir.y) > .9f ? 1 : 0, Math.abs(dir.y) > .9f ? 0 : 1, 0)
				.normalize(width);
		var other = new org.joml.Vector3f(dir).cross(side).normalize(width);
		int base = argb(.85f, color), tip = argb(0, color);
		for (var s : List.of(side, other)) {
			glow(p, v, s.x, s.y, s.z, base);
			glow(p, v, -s.x, -s.y, -s.z, base);
			glow(p, v, dir.x * length, dir.y * length, dir.z * length, tip);
			glow(p, v, dir.x * length + s.x * .2f, dir.y * length + s.y * .2f, dir.z * length + s.z * .2f, tip);
		}
	}

	private static int argb(float alpha, int rgb) {
		return (int) (clamp(alpha) * 255) << 24 | rgb & 0xFFFFFF;
	}

	private static float clamp(float x) {
		return Math.max(0, Math.min(1, x));
	}

	private static float sq(float x) {
		return x * x;
	}

	private static float smooth(float x) {
		x = clamp(x);
		return x * x * (3 - 2 * x);
	}

	private static float cubicOut(float x) {
		return 1 - (1 - x) * (1 - x) * (1 - x);
	}

	private static float cubicIn(float x) {
		return x * x * x;
	}

	private static float backOut(float x) {
		float c = 1.70158f;
		return 1 + (c + 1) * (x - 1) * (x - 1) * (x - 1) + c * (x - 1) * (x - 1);
	}

	private static float bounce(float x) {
		if (x < 1 / 2.75f)
			return 7.5625f * x * x;
		if (x < 2 / 2.75f)
			return 7.5625f * (x -= 1.5f / 2.75f) * x + .75f;
		if (x < 2.5 / 2.75f)
			return 7.5625f * (x -= 2.25f / 2.75f) * x + .9375f;
		return 7.5625f * (x -= 2.625f / 2.75f) * x + .984375f;
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	@Override
	public int getViewDistance() {
		return 64;
	}
}

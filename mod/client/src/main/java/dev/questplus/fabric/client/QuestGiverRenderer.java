package dev.questplus.fabric.client;

import dev.questplus.platform.ClientPlatform;
import dev.questplus.compat.ClientAdapter;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.questplus.fabric.QuestGiver;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.npc.BabyVillagerModel;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.AgeableMobRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.VillagerProfessionLayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

/**
 * One NPC entity, two looks: a villager in its biome's clothes, or the player
 * model wearing the pack's skin. Each frame hands the entity to the matching
 * renderer.
 */
final class QuestGiverRenderer extends EntityRenderer<QuestGiver, QuestGiverRenderer.Dispatch> {
	interface Submit {
		void run(PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera);
	}

	/**
	 * A living state so GUI portraits can turn the NPC: what they set here is
	 * handed to the chosen model's own state when it is submitted.
	 */
	static final class Dispatch extends LivingEntityRenderState {
		LivingEntityRenderState inner;
		Submit submit;

		void pull() {
			bodyRot = inner.bodyRot;
			yRot = inner.yRot;
			xRot = inner.xRot;
			scale = inner.scale;
			pose = inner.pose;
		}

		void push() {
			inner.bodyRot = bodyRot;
			inner.yRot = yRot;
			inner.xRot = xRot;
			inner.scale = scale;
			inner.boundingBoxWidth = boundingBoxWidth;
			inner.boundingBoxHeight = boundingBoxHeight;
			inner.outlineColor = outlineColor;
			inner.nameTag = nameTag;
		}
	}

	private final SkinLook skin;
	private final VillagerLook villager;

	private QuestGiverRenderer(EntityRendererProvider.Context context) {
		super(context);
		skin = new SkinLook(context);
		villager = new VillagerLook(context);
		shadowRadius = .5f;
	}

	static void register() {
		ClientPlatform.entityRenderer(QuestGiver.TYPE, QuestGiverRenderer::new);
	}

	@Override
	public Dispatch createRenderState() {
		return new Dispatch();
	}

	@Override
	public void extractRenderState(QuestGiver giver, Dispatch state, float partialTick) {
		super.extractRenderState(giver, state, partialTick);
		if (QuestGiverLooks.of(giver.npc()).villager())
			prepare(state, villager, giver, partialTick);
		else
			prepare(state, skin, giver, partialTick);
		state.pull();
	}

	private static <S extends LivingEntityRenderState> void prepare(Dispatch state,
			EntityRenderer<QuestGiver, S> renderer, QuestGiver giver, float partialTick) {
		S inner = renderer.createRenderState(giver, partialTick);
		state.inner = inner;
		state.submit = (pose, collector, camera) -> renderer.submit(inner, pose, collector, camera);
	}

	/** The chosen renderer also draws the name tag, so this one draws nothing itself. */
	@Override
	public void submit(Dispatch state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		state.push();
		state.submit.run(pose, collector, camera);
	}

	/**
	 * The NPC as a portrait in a GUI box, turned toward the mouse and without its
	 * floating name. Same framing as the inventory's player preview.
	 */
	static void portrait(net.minecraft.client.gui.GuiGraphicsExtractor g, QuestGiver giver, int x1, int y1, int x2,
			int y2, int size, float mouseX, float mouseY) {
		var renderer = net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(giver);
		var state = renderer.createRenderState(giver, 1);
		if (!(state instanceof Dispatch dispatch))
			return;
		// Gentler than the inventory preview: a speaker glances at you, it does not crane its neck.
		float turn = (float) Math.atan(((x1 + x2) / 2f - mouseX) / 160f),
				tilt = (float) Math.atan(((y1 + y2) / 2f - mouseY) / 240f);
		var camera = new org.joml.Quaternionf().rotateX(tilt * 20 * (float) Math.PI / 180);
		var rotation = new org.joml.Quaternionf().rotateZ((float) Math.PI).mul(camera);
		dispatch.shadowPieces.clear();
		dispatch.outlineColor = 0;
		dispatch.nameTag = null;
		dispatch.bodyRot = 180 + turn * 20;
		dispatch.yRot = turn * 20;
		dispatch.xRot = -tilt * 20;
		dispatch.boundingBoxWidth /= dispatch.scale;
		dispatch.boundingBoxHeight /= dispatch.scale;
		dispatch.scale = 1;
		ClientAdapter.INSTANCE.entity(g, dispatch, size, new org.joml.Vector3f(0, dispatch.boundingBoxHeight / 2 + .0625f, 0),
				rotation, camera, x1, y1, x2, y2);
	}

	static final class SkinState extends HumanoidRenderState {
		Identifier texture = QuestGiverLooks.DEFAULT_SKIN;
	}

	private static final class SkinLook
			extends HumanoidMobRenderer<QuestGiver, SkinState, HumanoidModel<SkinState>> {
		SkinLook(EntityRendererProvider.Context context) {
			super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), .5f);
		}

		@Override
		public SkinState createRenderState() {
			return new SkinState();
		}

		@Override
		public void extractRenderState(QuestGiver giver, SkinState state, float partialTick) {
			super.extractRenderState(giver, state, partialTick);
			state.texture = QuestGiverLooks.of(giver.npc()).skin();
		}

		@Override
		public Identifier getTextureLocation(SkinState state) {
			return state.texture;
		}
	}

	private static final class VillagerLook extends AgeableMobRenderer<QuestGiver, VillagerRenderState, VillagerModel> {
		private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/entity/villager/villager.png");

		VillagerLook(EntityRendererProvider.Context context) {
			super(context, new VillagerModel(context.bakeLayer(ModelLayers.VILLAGER)),
					new BabyVillagerModel(context.bakeLayer(ModelLayers.VILLAGER_BABY)), .5f);
			// The same clothing layer vanilla villagers wear: biome outfit, no profession.
			addLayer(new VillagerProfessionLayer<>(this, context.getResourceManager(), "villager",
					new VillagerModel(context.bakeLayer(ModelLayers.VILLAGER_NO_HAT)),
					new BabyVillagerModel(context.bakeLayer(ModelLayers.VILLAGER_BABY_NO_HAT))));
		}

		@Override
		public VillagerRenderState createRenderState() {
			return new VillagerRenderState();
		}

		@Override
		public void extractRenderState(QuestGiver giver, VillagerRenderState state, float partialTick) {
			super.extractRenderState(giver, state, partialTick);
			var registries = giver.level().registryAccess();
			var type = registries.lookupOrThrow(Registries.VILLAGER_TYPE).getOrThrow(ResourceKey.create(
					Registries.VILLAGER_TYPE, Identifier.withDefaultNamespace(QuestGiverLooks.of(giver.npc()).villagerType())));
			state.villagerData = new VillagerData(type,
					registries.lookupOrThrow(Registries.VILLAGER_PROFESSION).getOrThrow(VillagerProfession.NONE), 1);
		}

		@Override
		public Identifier getTextureLocation(VillagerRenderState state) {
			return TEXTURE;
		}
	}
}

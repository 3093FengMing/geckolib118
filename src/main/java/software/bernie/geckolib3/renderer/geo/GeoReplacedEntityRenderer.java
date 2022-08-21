package software.bernie.geckolib3.renderer.geo;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.common.collect.Lists;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexConsumers;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.PlayerModelPart;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3f;
import net.minecraft.world.LightType;
import software.bernie.geckolib3.compat.PatchouliCompat;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.IAnimatableModel;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.util.Color;
import software.bernie.geckolib3.geo.render.built.GeoModel;
import software.bernie.geckolib3.model.AnimatedGeoModel;
import software.bernie.geckolib3.model.provider.data.EntityModelData;

@SuppressWarnings({ "rawtypes", "unchecked" })
public abstract class GeoReplacedEntityRenderer<T extends IAnimatable> extends EntityRenderer implements IGeoRenderer {
	private final AnimatedGeoModel<IAnimatable> modelProvider;
	private final T animatable;
	protected final List<GeoLayerRenderer> layerRenderers = Lists.newArrayList();
	private IAnimatable currentAnimatable;
	private static final Map<Class<? extends IAnimatable>, GeoReplacedEntityRenderer> renderers = new ConcurrentHashMap<>();

	static {
		AnimationController.addModelFetcher((IAnimatable object) -> {
			GeoReplacedEntityRenderer renderer = renderers.get(object.getClass());
			return renderer == null ? null : renderer.getGeoModelProvider();
		});
	}

	public GeoReplacedEntityRenderer(EntityRenderDispatcher renderManager, AnimatedGeoModel<IAnimatable> modelProvider,
			T animatable) {
		super(renderManager);
		this.modelProvider = modelProvider;
		this.animatable = animatable;
	}

	public static void registerReplacedEntity(Class<? extends IAnimatable> itemClass,
			GeoReplacedEntityRenderer renderer) {
		renderers.put(itemClass, renderer);
	}

	public static GeoReplacedEntityRenderer getRenderer(Class<? extends IAnimatable> item) {
		return renderers.get(item);
	}

	@Override
	public void render(Entity entityIn, float entityYaw, float partialTicks, MatrixStack matrixStackIn,
			VertexConsumerProvider bufferIn, int packedLightIn) {
		this.render(entityIn, this.animatable, entityYaw, partialTicks, matrixStackIn, bufferIn, packedLightIn);
	}

	public void render(Entity entity, IAnimatable animatable, float entityYaw, float partialTicks, MatrixStack stack,
			VertexConsumerProvider bufferIn, int packedLightIn) {
		this.currentAnimatable = animatable;
		LivingEntity entityLiving;
		if (entity instanceof LivingEntity) {
			entityLiving = (LivingEntity) entity;
		} else {
			throw (new RuntimeException("Replaced renderer was not an instanceof LivingEntity"));
		}

		stack.push();
		if (entity instanceof MobEntity) {
			Entity leashHolder = ((MobEntity) entity).getHoldingEntity();
			if (leashHolder != null) {
				this.renderLeash(((MobEntity) entity), partialTicks, stack, bufferIn, leashHolder);
			}
		}
		boolean shouldSit = entity.hasVehicle() && (entity.getVehicle() != null);
		EntityModelData entityModelData = new EntityModelData();
		entityModelData.isSitting = shouldSit;
		entityModelData.isChild = entityLiving.isBaby();

		float f = MathHelper.lerpAngleDegrees(partialTicks, entityLiving.prevBodyYaw, entityLiving.bodyYaw);
		float f1 = MathHelper.lerpAngleDegrees(partialTicks, entityLiving.prevHeadYaw, entityLiving.headYaw);
		float f2 = f1 - f;
		if (shouldSit && entity.getVehicle() instanceof LivingEntity) {
			LivingEntity livingentity = (LivingEntity) entity.getVehicle();
			f = MathHelper.lerpAngleDegrees(partialTicks, livingentity.prevBodyYaw, livingentity.bodyYaw);
			f2 = f1 - f;
			float f3 = MathHelper.wrapDegrees(f2);
			if (f3 < -85.0F) {
				f3 = -85.0F;
			}

			if (f3 >= 85.0F) {
				f3 = 85.0F;
			}

			f = f1 - f3;
			if (f3 * f3 > 2500.0F) {
				f += f3 * 0.2F;
			}

			f2 = f1 - f;
		}

		float f6 = MathHelper.lerp(partialTicks, entity.prevPitch, entity.pitch);
		if (entity.getPose() == EntityPose.SLEEPING) {
			Direction direction = entityLiving.getSleepingDirection();
			if (direction != null) {
				float f4 = entity.getEyeHeight(EntityPose.STANDING) - 0.1F;
				stack.translate((float) (-direction.getOffsetX()) * f4, 0.0D, (float) (-direction.getOffsetZ()) * f4);
			}
		}
		float f7 = this.handleRotationFloat(entityLiving, partialTicks);
		this.applyRotations(entityLiving, stack, f7, f, partialTicks);
		this.preRenderCallback(entityLiving, stack, partialTicks);

		float limbSwingAmount = 0.0F;
		float limbSwing = 0.0F;
		if (!shouldSit && entity.isAlive()) {
			limbSwingAmount = MathHelper.lerp(partialTicks, entityLiving.lastLimbDistance, entityLiving.limbDistance);
			limbSwing = entityLiving.limbAngle - entityLiving.limbDistance * (1.0F - partialTicks);
			if (entityLiving.isBaby()) {
				limbSwing *= 3.0F;
			}

			if (limbSwingAmount > 1.0F) {
				limbSwingAmount = 1.0F;
			}
		}

		entityModelData.headPitch = -f6;
		entityModelData.netHeadYaw = -f2;
		AnimationEvent predicate = new AnimationEvent(animatable, limbSwing, limbSwingAmount, partialTicks,
				!(limbSwingAmount > -0.15F && limbSwingAmount < 0.15F), Collections.singletonList(entityModelData));
		if (modelProvider instanceof IAnimatableModel) {
			((IAnimatableModel) modelProvider).setLivingAnimations(animatable, this.getUniqueID(entity), predicate);
		}

		stack.translate(0, 0.01f, 0);
		MinecraftClient.getInstance().getTextureManager().bindTexture(getTexture(entity));
		GeoModel model = modelProvider.getModel(modelProvider.getModelLocation(animatable));
		Color renderColor = getRenderColor(animatable, partialTicks, stack, bufferIn, null, packedLightIn);
		RenderLayer renderType = getRenderType(entity, partialTicks, stack, bufferIn, null, packedLightIn,
				getTexture(entity));
		if (!entity.isInvisibleTo(MinecraftClient.getInstance().player)) {
			VertexConsumer glintBuffer = bufferIn.getBuffer(RenderLayer.getDirectEntityGlint());
			VertexConsumer translucentBuffer = bufferIn
					.getBuffer(RenderLayer.getEntityTranslucentCull(getTextureLocation(entity)));
			render(model, entity, partialTicks, renderType, stack, bufferIn,
					glintBuffer != translucentBuffer ? VertexConsumers.union(glintBuffer, translucentBuffer) : null,
					packedLightIn, getPackedOverlay(entityLiving, this.getOverlayProgress(entityLiving, partialTicks)),
					(float) renderColor.getRed() / 255f, (float) renderColor.getGreen() / 255f,
					(float) renderColor.getBlue() / 255f, (float) renderColor.getAlpha() / 255);
		}

		if (!entity.isSpectator()) {
			for (GeoLayerRenderer layerRenderer : this.layerRenderers) {
				layerRenderer.render(stack, bufferIn, packedLightIn, entity, limbSwing, limbSwingAmount, partialTicks,
						f7, f2, f6);
			}
		}
		if (FabricLoader.getInstance().isModLoaded("patchouli")) {
			PatchouliCompat.patchouliLoaded(stack);
		}
		stack.pop();
		super.render(entity, entityYaw, partialTicks, stack, bufferIn, packedLightIn);
	}

	protected float getOverlayProgress(LivingEntity livingEntityIn, float partialTicks) {
		return 0.0F;
	}

	protected void preRenderCallback(LivingEntity entitylivingbaseIn, MatrixStack matrixStackIn,
			float partialTickTime) {
	}

	@Override
	public Identifier getTexture(Entity entity) {
		return getTextureLocation(currentAnimatable);
	}

	@Override
	public AnimatedGeoModel getGeoModelProvider() {
		return this.modelProvider;
	}

	public static int getPackedOverlay(LivingEntity livingEntityIn, float uIn) {
		return OverlayTexture.packUv(OverlayTexture.getU(uIn),
				OverlayTexture.getV(livingEntityIn.hurtTime > 0 || livingEntityIn.deathTime > 0));
	}

	protected void applyRotations(LivingEntity entityLiving, MatrixStack matrixStackIn, float ageInTicks,
			float rotationYaw, float partialTicks) {
		EntityPose pose = entityLiving.getPose();
		if (pose != EntityPose.SLEEPING) {
			matrixStackIn.multiply(Vec3f.POSITIVE_Y.getDegreesQuaternion(180.0F - rotationYaw));
		}

		if (entityLiving.deathTime > 0) {
			float f = ((float) entityLiving.deathTime + partialTicks - 1.0F) / 20.0F * 1.6F;
			f = MathHelper.sqrt(f);
			if (f > 1.0F) {
				f = 1.0F;
			}

			matrixStackIn.multiply(Vec3f.POSITIVE_Z.getDegreesQuaternion(f * this.getDeathMaxRotation(entityLiving)));
		} else if (entityLiving.isUsingRiptide()) {
			matrixStackIn.multiply(Vec3f.POSITIVE_X.getDegreesQuaternion(-90.0F - entityLiving.pitch));
			matrixStackIn.multiply(
					Vec3f.POSITIVE_Y.getDegreesQuaternion(((float) entityLiving.age + partialTicks) * -75.0F));
		} else if (pose == EntityPose.SLEEPING) {
			Direction direction = entityLiving.getSleepingDirection();
			float f1 = direction != null ? getFacingAngle(direction) : rotationYaw;
			matrixStackIn.multiply(Vec3f.POSITIVE_Y.getDegreesQuaternion(f1));
			matrixStackIn.multiply(Vec3f.POSITIVE_Z.getDegreesQuaternion(this.getDeathMaxRotation(entityLiving)));
			matrixStackIn.multiply(Vec3f.POSITIVE_Y.getDegreesQuaternion(270.0F));
		} else if (entityLiving.hasCustomName() || entityLiving instanceof PlayerEntity) {
			String s = Formatting.strip(entityLiving.getName().getString());
			if (("Dinnerbone".equals(s) || "Grumm".equals(s)) && (!(entityLiving instanceof PlayerEntity)
					|| ((PlayerEntity) entityLiving).isPartVisible(PlayerModelPart.CAPE))) {
				matrixStackIn.translate(0.0D, entityLiving.getHeight() + 0.1F, 0.0D);
				matrixStackIn.multiply(Vec3f.POSITIVE_Z.getDegreesQuaternion(180.0F));
			}
		}

	}

	@Override
	protected boolean hasLabel(Entity entity) {
		double d0 = this.dispatcher.getSquaredDistanceToCamera(entity);
		float f = entity.isSneaking() ? 32.0F : 64.0F;
		if (d0 >= (double) (f * f)) {
			return false;
		} else {
			return entity == this.dispatcher.targetedEntity && entity.hasCustomName() && MinecraftClient.isHudEnabled();
		}
	}

	protected boolean isVisible(LivingEntity livingEntityIn) {
		return !livingEntityIn.isInvisible();
	}

	private static float getFacingAngle(Direction facingIn) {
		switch (facingIn) {
		case SOUTH:
			return 90.0F;
		case WEST:
			return 0.0F;
		case NORTH:
			return 270.0F;
		case EAST:
			return 180.0F;
		default:
			return 0.0F;
		}
	}

	protected float getDeathMaxRotation(LivingEntity entityLivingBaseIn) {
		return 90.0F;
	}

	/**
	 * Returns where in the swing animation the living entity is (from 0 to 1). Args
	 * : entity, partialTickTime
	 */
	protected float getHandSwingProgress(LivingEntity livingBase, float partialTickTime) {
		return livingBase.getHandSwingProgress(partialTickTime);
	}

	/**
	 * Defines what float the third param in setRotationAngles of ModelBase is
	 */
	protected float handleRotationFloat(LivingEntity livingBase, float partialTicks) {
		return (float) livingBase.age + partialTicks;
	}

	@Override
	public Identifier getTextureLocation(Object instance) {
		return this.modelProvider.getTextureLocation((IAnimatable) instance);
	}

	public final boolean addLayer(GeoLayerRenderer<? extends LivingEntity> layer) {
		return this.layerRenderers.add(layer);
	}

	public <E extends Entity> void renderLeash(MobEntity entity, float partialTicks, MatrixStack poseStack,
			VertexConsumerProvider buffer, E leashHolder) {
		int u;
		poseStack.push();
		Vec3d vec3d = leashHolder.method_30951(partialTicks);
		double d = (double) (MathHelper.lerp(partialTicks, entity.bodyYaw, entity.prevBodyYaw)
				* ((float) Math.PI / 180)) + 1.5707963267948966;
		Vec3d vec3d2 = ((Entity) entity).method_29919();
		double e = Math.cos(d) * vec3d2.z + Math.sin(d) * vec3d2.x;
		double f = Math.sin(d) * vec3d2.z - Math.cos(d) * vec3d2.x;
		double g = MathHelper.lerp(partialTicks, entity.prevX, entity.getX()) + e;
		double h = MathHelper.lerp(partialTicks, entity.prevY, entity.getY()) + vec3d2.y;
		double i = MathHelper.lerp(partialTicks, entity.prevZ, entity.getZ()) + f;
		poseStack.translate(e, vec3d2.y, f);
		float j = (float) (vec3d.x - g);
		float k = (float) (vec3d.y - h);
		float l = (float) (vec3d.z - i);
		VertexConsumer vertexConsumer = buffer.getBuffer(RenderLayer.getLeash());
		Matrix4f matrix4f = poseStack.peek().getModel();
		float n = MathHelper.fastInverseSqrt(j * j + l * l) * 0.025f / 2.0f;
		float o = l * n;
		float p = j * n;
		BlockPos blockPos = new BlockPos(entity.getCameraPosVec(partialTicks));
		BlockPos blockPos2 = new BlockPos(leashHolder.getCameraPosVec(partialTicks));
		int q = this.getBlockLight(entity, blockPos);
		int r = leashHolder.isOnFire() ? 15 : leashHolder.world.getLightLevel(LightType.BLOCK, blockPos2);
		int s = entity.world.getLightLevel(LightType.SKY, blockPos);
		int t = entity.world.getLightLevel(LightType.SKY, blockPos2);
		for (u = 0; u <= 24; ++u) {
			GeoReplacedEntityRenderer.renderLeashPiece(vertexConsumer, matrix4f, j, k, l, q, r, s, t, 0.025f, 0.025f, o,
					p, u, false);
		}
		for (u = 24; u >= 0; --u) {
			GeoReplacedEntityRenderer.renderLeashPiece(vertexConsumer, matrix4f, j, k, l, q, r, s, t, 0.025f, 0.0f, o,
					p, u, true);
		}
		poseStack.pop();
	}

	private static void renderLeashPiece(VertexConsumer vertexConsumer, Matrix4f positionMatrix, float f, float g,
			float h, int leashedEntityBlockLight, int holdingEntityBlockLight, int leashedEntitySkyLight,
			int holdingEntitySkyLight, float i, float j, float k, float l, int pieceIndex, boolean isLeashKnot) {
		float m = (float) pieceIndex / 24.0f;
		int n = (int) MathHelper.lerp(m, leashedEntityBlockLight, holdingEntityBlockLight);
		int o = (int) MathHelper.lerp(m, leashedEntitySkyLight, holdingEntitySkyLight);
		int p = LightmapTextureManager.pack(n, o);
		float q = pieceIndex % 2 == (isLeashKnot ? 1 : 0) ? 0.7f : 1.0f;
		float r = 0.5f * q;
		float s = 0.4f * q;
		float t = 0.3f * q;
		float u = f * m;
		float v = g > 0.0f ? g * m * m : g - g * (1.0f - m) * (1.0f - m);
		float w = h * m;
		vertexConsumer.vertex(positionMatrix, u - k, v + j, w + l).color(r, s, t, 1.0f).light(p).next();
		vertexConsumer.vertex(positionMatrix, u + k, v + i - j, w - l).color(r, s, t, 1.0f).light(p).next();
	}
}

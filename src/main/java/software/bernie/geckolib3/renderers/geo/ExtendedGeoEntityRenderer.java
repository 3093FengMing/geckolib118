package software.bernie.geckolib3.renderers.geo;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.google.common.collect.Maps;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Quaternion;
import com.mojang.math.Vector3f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.SkullModelBase;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.ModelPart.Cube;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.ItemTransforms.TransformType;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeableArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.ForgeHooksClient;
import software.bernie.geckolib3.core.bone.IBone;
import software.bernie.geckolib3.geo.render.AnimatingBone;
import software.bernie.geckolib3.geo.render.AnimatingModel;
import software.bernie.geckolib3.geo.render.built.GeoCube;
import software.bernie.geckolib3.item.GeoArmorItem;
import software.bernie.geckolib3.model.GeoModelType;

/**
 * @author DerToaster98 Copyright (c) 30.03.2022 Developed by DerToaster98
 *         GitHub: https://github.com/DerToaster98
 * 
 *         Purpose of this class: This class is a extended version of
 *         {@code GeoEnttiyRenderer}. It automates the process of rendering
 *         items at hand bones as well as standard armor at certain bones. The
 *         model must feature a few special bones for this to work.
 */
@OnlyIn(Dist.CLIENT)
public abstract class ExtendedGeoEntityRenderer<T extends LivingEntity> extends GeoEntityRenderer<T> {

	static enum EModelRenderCycle {
		INITIAL, REPEATED, SPECIAL /* For special use by the user */
	}

	protected float widthScale;
	protected float heightScale;

	protected final Queue<Tuple<AnimatingBone, ItemStack>> HEAD_QUEUE = new ArrayDeque<>();

	/*
	 * 0 => Normal model 1 => Magical armor overlay
	 */
	private EModelRenderCycle currentModelRenderCycle = EModelRenderCycle.INITIAL;

	protected EModelRenderCycle getCurrentModelRenderCycle() {
		return this.currentModelRenderCycle;
	}

	protected void setCurrentModelRenderCycle(EModelRenderCycle currentModelRenderCycle) {
		this.currentModelRenderCycle = currentModelRenderCycle;
	}

	protected ExtendedGeoEntityRenderer(EntityRendererProvider.Context renderManager, GeoModelType<T> modelProvider) {
		this(renderManager, modelProvider, 1F, 1F, 0);
	}

	protected ExtendedGeoEntityRenderer(EntityRendererProvider.Context renderManager, GeoModelType<T> modelProvider,
			float widthScale, float heightScale, float shadowSize) {
		super(renderManager, modelProvider);
		this.shadowRadius = shadowSize;
		this.widthScale = widthScale;
		this.heightScale = heightScale;
	}

	// Entrypoint for rendering, calls everything else
	@Override
	public void render(T entity, float entityYaw, float partialTicks, PoseStack stack, MultiBufferSource bufferIn,
			int packedLightIn) {
		this.setCurrentModelRenderCycle(EModelRenderCycle.INITIAL);
		super.render(entity, entityYaw, partialTicks, stack, bufferIn, packedLightIn);
		// Now, render the heads
		this.renderHeads(stack, bufferIn, packedLightIn);
	}

	// Yes, this is necessary to be done after everything else, otherwise it will
	// mess up the texture cause the rendertypebuffer will be modified
	protected void renderHeads(PoseStack stack, MultiBufferSource buffer, int packedLightIn) {
		while (!this.HEAD_QUEUE.isEmpty()) {
			Tuple<AnimatingBone, ItemStack> entry = this.HEAD_QUEUE.poll();

			AnimatingBone bone = entry.getA();
			ItemStack itemStack = entry.getB();

			stack.pushPose();

			this.moveAndRotateMatrixToMatchBone(stack, bone);

			GameProfile skullOwnerProfile = null;
			if (itemStack.hasTag()) {
				CompoundTag compoundnbt = itemStack.getTag();
				if (compoundnbt.contains("SkullOwner", 10)) {
					skullOwnerProfile = NbtUtils.readGameProfile(compoundnbt.getCompound("SkullOwner"));
				} else if (compoundnbt.contains("SkullOwner", 8)) {
					String s = compoundnbt.getString("SkullOwner");
					if (!StringUtils.isBlank(s)) {
						SkullBlockEntity.updateGameprofile(new GameProfile((UUID) null, s), (p_172560_) -> {
							compoundnbt.put("SkullOwner", NbtUtils.writeGameProfile(new CompoundTag(), p_172560_));
						});
					}
				}
			}
			float sx = 1;
			float sy = 1;
			float sz = 1;
			try {
				GeoCube firstCube = bone.bone.childCubes.get(0);
				if (firstCube != null) {
					// Calculate scale in relation to a vanilla head (8x8x8 units)
					sx = firstCube.size.x() / 8;
					sy = firstCube.size.y() / 8;
					sz = firstCube.size.z() / 8;
				}
			} catch (IndexOutOfBoundsException ioobe) {
				// Ignore
			}
			stack.scale(1.1875F * sx, 1.1875F * sy, 1.1875F * sz);
			stack.translate(-0.5, 0, -0.5);
			SkullBlock.Type skullblock$type = ((AbstractSkullBlock) ((BlockItem) itemStack.getItem()).getBlock())
					.getType();
			SkullModelBase skullmodelbase = SkullBlockRenderer
					.createSkullRenderers(Minecraft.getInstance().getEntityModels()).get(skullblock$type);
			RenderType rendertype = SkullBlockRenderer.getRenderType(skullblock$type, skullOwnerProfile);
			SkullBlockRenderer.renderSkull((Direction) null, 0.0F, 0.0F, stack, buffer, packedLightIn, skullmodelbase,
					rendertype);
			stack.popPose();

		}
		;
	}

	// Rendercall to render the model itself
	@Override
	public void render(AnimatingModel model, T animatable, float partialTicks, RenderType type, PoseStack matrixStackIn,
			MultiBufferSource renderTypeBuffer, VertexConsumer vertexBuilder, int packedLightIn, int packedOverlayIn,
			float red, float green, float blue, float alpha) {
		super.render(model, animatable, partialTicks, type, matrixStackIn, renderTypeBuffer, vertexBuilder,
				packedLightIn, packedOverlayIn, red, green, blue, alpha);
		this.setCurrentModelRenderCycle(EModelRenderCycle.REPEATED);
	}

	protected float getWidthScale(T entity) {
		return this.widthScale;
	}

	protected float getHeightScale(T entity) {
		return this.heightScale;
	}

	@Override
	public void renderEarly(T animatable, PoseStack stackIn, float ticks, MultiBufferSource renderTypeBuffer,
			VertexConsumer vertexBuilder, int packedLightIn, int packedOverlayIn, float red, float green, float blue,
			float partialTicks) {
		this.rtb = renderTypeBuffer;
		super.renderEarly(animatable, stackIn, ticks, renderTypeBuffer, vertexBuilder, packedLightIn, packedOverlayIn,
				red, green, blue, partialTicks);
		if (this.getCurrentModelRenderCycle() == EModelRenderCycle.INITIAL /* Pre-Layers */) {
			float width = this.getWidthScale(animatable);
			float height = this.getHeightScale(animatable);
			stackIn.scale(width, height, width);
		}
	}

	@Override
	public ResourceLocation getTextureLocation(T entity) {
		return this.modelProvider.getTextureResource(entity);
	}

	private T currentEntityBeingRendered;
	private MultiBufferSource rtb;

	@Override
	public void renderLate(T animatable, PoseStack stackIn, float ticks, MultiBufferSource renderTypeBuffer,
			VertexConsumer bufferIn, int packedLightIn, int packedOverlayIn, float red, float green, float blue,
			float partialTicks) {
		super.renderLate(animatable, stackIn, ticks, renderTypeBuffer, bufferIn, packedLightIn, packedOverlayIn, red,
				green, blue, partialTicks);
		this.currentEntityBeingRendered = animatable;
		this.currentVertexBuilderInUse = bufferIn;
		this.currentPartialTicks = partialTicks;
	}

	protected final HumanoidModel<LivingEntity> DEFAULT_BIPED_ARMOR_MODEL_INNER = new HumanoidModel<LivingEntity>(
			Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER_INNER_ARMOR));
	protected final HumanoidModel<LivingEntity> DEFAULT_BIPED_ARMOR_MODEL_OUTER = new HumanoidModel<LivingEntity>(
			Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR));

	protected abstract boolean isArmorBone(final AnimatingBone bone);

	private VertexConsumer currentVertexBuilderInUse;
	private float currentPartialTicks;

	protected void moveAndRotateMatrixToMatchBone(PoseStack stack, AnimatingBone bone) {
		// First, let's move our render position to the pivot point...
		stack.translate(bone.getPivotX() / 16, bone.getPivotY() / 16, bone.getPositionZ() / 16);

		stack.mulPose(Vector3f.XP.rotationDegrees(bone.getRotationX()));
		stack.mulPose(Vector3f.YP.rotationDegrees(bone.getRotationY()));
		stack.mulPose(Vector3f.ZP.rotationDegrees(bone.getRotationZ()));
	}

	protected void handleArmorRenderingForBone(AnimatingBone bone, PoseStack stack, VertexConsumer bufferIn,
			int packedLightIn, int packedOverlayIn, ResourceLocation currentTexture) {
		final ItemStack armorForBone = this.getArmorForBone(bone.getName(), currentEntityBeingRendered);
		final EquipmentSlot boneSlot = this.getEquipmentSlotForArmorBone(bone.getName(), currentEntityBeingRendered);
		if (armorForBone != null && boneSlot != null) {
			// Standard armor
			if (armorForBone.getItem() instanceof ArmorItem && !(armorForBone.getItem() instanceof GeoArmorItem)) {
				final ArmorItem armorItem = (ArmorItem) armorForBone.getItem();
				final HumanoidModel<?> armorModel = (HumanoidModel<?>) ForgeHooksClient.getArmorModel(
						currentEntityBeingRendered, armorForBone, boneSlot,
						boneSlot == EquipmentSlot.LEGS ? DEFAULT_BIPED_ARMOR_MODEL_INNER
								: DEFAULT_BIPED_ARMOR_MODEL_OUTER);
				if (armorModel != null) {
					ModelPart sourceLimb = this.getArmorPartForBone(bone.getName(), armorModel);
					List<Cube> cubeList = sourceLimb.cubes;
					if (sourceLimb != null && cubeList != null && !cubeList.isEmpty()) {
						// IMPORTANT: The first cube is used to define the armor part!!
						this.prepareArmorPositionAndScale(bone, cubeList, sourceLimb, stack);
						stack.scale(-1, -1, 1);

						stack.pushPose();

						ResourceLocation armorResource = this.getArmorResource(currentEntityBeingRendered, armorForBone,
								boneSlot, null);

						this.renderArmorOfItem(armorItem, armorForBone, boneSlot, armorResource, sourceLimb, stack,
								packedLightIn, packedOverlayIn);

						stack.popPose();

						bufferIn = rtb.getBuffer(RenderType.entityTranslucent(currentTexture));
					}
				}
			}
			// Geo Armor
			else if (armorForBone.getItem() instanceof GeoArmorItem) {
				final GeoArmorItem armorItem = (GeoArmorItem) armorForBone.getItem();
				@SuppressWarnings("unchecked")
				final GeoArmorRenderer<? extends GeoArmorItem> geoArmorRenderer = GeoArmorRenderer
						.getRenderer(armorItem.getClass(), this.currentEntityBeingRendered);
				final HumanoidModel<?> armorModel = (HumanoidModel<?>) geoArmorRenderer;

				if (armorModel != null) {
					ModelPart sourceLimb = this.getArmorPartForBone(bone.getName(), armorModel);
					if (sourceLimb != null) {
						List<Cube> cubeList = sourceLimb.cubes;
						if (cubeList != null && !cubeList.isEmpty()) {
							// IMPORTANT: The first cube is used to define the armor part!!
							stack.scale(-1, -1, 1);
							stack.pushPose();

							this.prepareArmorPositionAndScale(bone, cubeList, sourceLimb, stack, true,
									boneSlot == EquipmentSlot.CHEST);

							geoArmorRenderer.setCurrentItem(this.currentEntityBeingRendered, armorForBone, boneSlot);
							geoArmorRenderer.applySlot(boneSlot);

							VertexConsumer ivb = ItemRenderer.getArmorFoilBuffer(rtb,
									RenderType.armorCutoutNoCull(GeoArmorRenderer
											.getRenderer(armorItem.getClass(), this.currentEntityBeingRendered)
											.getTextureResource(new ItemStack(armorItem))),
									false, armorForBone.hasFoil());

							geoArmorRenderer.render(this.currentPartialTicks, stack, ivb, packedLightIn);

							stack.popPose();

							bufferIn = rtb.getBuffer(RenderType.entityTranslucent(currentTexture));
						}
					}
				}
			}
			// Head blocks
			else if (armorForBone.getItem() instanceof BlockItem
					&& ((BlockItem) armorForBone.getItem()).getBlock() instanceof AbstractSkullBlock) {
				this.HEAD_QUEUE.add(new Tuple<>(bone, armorForBone));
			}
		}
	}

	protected void renderArmorOfItem(ArmorItem armorItem, ItemStack armorForBone, EquipmentSlot boneSlot,
			ResourceLocation armorResource, ModelPart sourceLimb, PoseStack stack, int packedLightIn,
			int packedOverlayIn) {
		if (armorItem instanceof DyeableArmorItem) {
			int i = ((DyeableArmorItem) armorItem).getColor(armorForBone);
			float r = (float) (i >> 16 & 255) / 255.0F;
			float g = (float) (i >> 8 & 255) / 255.0F;
			float b = (float) (i & 255) / 255.0F;

			renderArmorPart(stack, sourceLimb, packedLightIn, packedOverlayIn, r, g, b, 1, armorForBone, armorResource);
			renderArmorPart(stack, sourceLimb, packedLightIn, packedOverlayIn, 1, 1, 1, 1, armorForBone,
					getArmorResource(currentEntityBeingRendered, armorForBone, boneSlot, "overlay"));
		} else {
			renderArmorPart(stack, sourceLimb, packedLightIn, packedOverlayIn, 1, 1, 1, 1, armorForBone, armorResource);
		}
	}

	protected void prepareArmorPositionAndScale(AnimatingBone bone, List<Cube> cubeList, ModelPart sourceLimb,
			PoseStack stack) {
		prepareArmorPositionAndScale(bone, cubeList, sourceLimb, stack, false, false);
	}

	protected void prepareArmorPositionAndScale(AnimatingBone bone, List<Cube> cubeList, ModelPart sourceLimb,
			PoseStack stack, boolean geoArmor, boolean modMatrixRot) {
		GeoCube firstCube = bone.bone.childCubes.get(0);
		final Cube armorCube = cubeList.get(0);

		final float targetSizeX = firstCube.size.x();
		final float targetSizeY = firstCube.size.y();
		final float targetSizeZ = firstCube.size.z();

		final float sourceSizeX = Math.abs(armorCube.maxX - armorCube.minX);
		final float sourceSizeY = Math.abs(armorCube.maxY - armorCube.minY);
		final float sourceSizeZ = Math.abs(armorCube.maxZ - armorCube.minZ);

		float scaleX = targetSizeX / sourceSizeX;
		float scaleY = targetSizeY / sourceSizeY;
		float scaleZ = targetSizeZ / sourceSizeZ;

		// Modify position to move point to correct location, otherwise it will be off
		// when the sizes are different
		sourceLimb.setPos(-(bone.getPivotX() + sourceSizeX - targetSizeX),
				-(bone.getPivotY() + sourceSizeY - targetSizeY), (bone.getPivotZ() + sourceSizeZ - targetSizeZ));

		if (!geoArmor) {
			sourceLimb.xRot = -bone.getRotationX();
			sourceLimb.yRot = -bone.getRotationY();
			sourceLimb.zRot = bone.getRotationZ();
		} else {
			float xRot = -bone.getRotationX();
			float yRot = -bone.getRotationY();
			float zRot = bone.getRotationZ();
			AnimatingBone tmpBone = bone.parent;
			while (tmpBone != null) {
				xRot -= tmpBone.getRotationX();
				yRot -= tmpBone.getRotationY();
				zRot += tmpBone.getRotationZ();
				tmpBone = tmpBone.parent;
			}

			if (modMatrixRot) {
				xRot = (float) Math.toRadians(xRot);
				yRot = (float) Math.toRadians(yRot);
				zRot = (float) Math.toRadians(zRot);

				stack.mulPose(new Quaternion(0, 0, zRot, false));
				stack.mulPose(new Quaternion(0, yRot, 0, false));
				stack.mulPose(new Quaternion(xRot, 0, 0, false));
			} else {
				sourceLimb.xRot = xRot;
				sourceLimb.yRot = yRot;
				sourceLimb.zRot = zRot;
			}
		}

		stack.scale(scaleX, scaleY, scaleZ);
	}

	@Override
	public void renderRecursively(AnimatingBone bone, PoseStack stack, VertexConsumer bufferIn, int packedLightIn,
			int packedOverlayIn, float red, float green, float blue, float alpha) {
		ResourceLocation tfb = this.getCurrentModelRenderCycle() != EModelRenderCycle.INITIAL ? null
				: this.getTextureForBone(bone.bone.getName(), this.currentEntityBeingRendered);
		boolean customTextureMarker = tfb != null;
		ResourceLocation currentTexture = this.getTextureLocation(this.currentEntityBeingRendered);
		if (customTextureMarker) {
			currentTexture = tfb;

			if (this.rtb != null) {
				RenderType rt = this.getRenderTypeForBone(bone, this.currentEntityBeingRendered,
						this.currentPartialTicks, stack, bufferIn, this.rtb, packedLightIn, currentTexture);
				bufferIn = this.rtb.getBuffer(rt);
			}
		}
		if (this.getCurrentModelRenderCycle() == EModelRenderCycle.INITIAL) {
			stack.pushPose();

			// Render armor
			if (this.isArmorBone(bone)) {
				stack.pushPose();
				this.handleArmorRenderingForBone(bone, stack, bufferIn, packedLightIn, packedOverlayIn,
						currentTexture);
				stack.popPose();
			} else {
				ItemStack boneItem = this.getHeldItemForBone(bone.getName(), this.currentEntityBeingRendered);
				BlockState boneBlock = this.getHeldBlockForBone(bone.getName(), this.currentEntityBeingRendered);
				if (boneItem != null || boneBlock != null) {

					stack.pushPose();

					this.moveAndRotateMatrixToMatchBone(stack, bone);

					if (boneItem != null) {
						this.preRenderItem(stack, boneItem, bone.getName(), this.currentEntityBeingRendered, bone);

						Minecraft.getInstance().getItemInHandRenderer().renderItem(currentEntityBeingRendered, boneItem,
								this.getCameraTransformForItemAtBone(boneItem, bone.getName()), false, stack, rtb,
								packedLightIn);

						this.postRenderItem(stack, boneItem, bone.getName(), this.currentEntityBeingRendered, bone);
					}
					if (boneBlock != null) {
						this.preRenderBlock(boneBlock, bone.getName(), this.currentEntityBeingRendered);

						this.renderBlock(stack, this.rtb, packedLightIn, boneBlock);

						this.postRenderBlock(boneBlock, bone.getName(), this.currentEntityBeingRendered);
					}

					stack.popPose();

					bufferIn = rtb.getBuffer(RenderType.entityTranslucent(currentTexture));
				}
			}
			stack.popPose();
		}
		// reset buffer
		if (customTextureMarker) {
			bufferIn = this.currentVertexBuilderInUse;
		}

		super.renderRecursively(bone, stack, bufferIn, packedLightIn, packedOverlayIn, red, green, blue, alpha);
	}

	private RenderType getRenderTypeForBone(AnimatingBone bone, T currentEntityBeingRendered2,
			float currentPartialTicks2, PoseStack stack, VertexConsumer bufferIn,
			MultiBufferSource currentRenderTypeBufferInUse2, int packedLightIn, ResourceLocation currentTexture) {
		return this.getRenderType(currentEntityBeingRendered2, currentPartialTicks2, stack,
				currentRenderTypeBufferInUse2, bufferIn, packedLightIn, currentTexture);
	}

	// Internal use only. Basically renders the passed "part" of the armor model on
	// a pre-setup location
	protected void renderArmorPart(PoseStack stack, ModelPart sourceLimb, int packedLightIn, int packedOverlayIn,
			float red, float green, float blue, float alpha, ItemStack armorForBone, ResourceLocation armorResource) {
		VertexConsumer ivb = ItemRenderer.getArmorFoilBuffer(rtb, RenderType.armorCutoutNoCull(armorResource), false,
				armorForBone.hasFoil());
		sourceLimb.render(stack, ivb, packedLightIn, packedOverlayIn, red, green, blue, alpha);
	}

	/*
	 * Return null, if the entity's texture is used ALso doesn't work yet, or, well,
	 * i haven't tested it, so, maybe it works...
	 */
	@Nullable
	protected abstract ResourceLocation getTextureForBone(String boneName, T currentEntity);

	protected void renderBlock(PoseStack matrixStack, MultiBufferSource rtb, int packedLightIn,
			BlockState iBlockState) {
		// TODO: Re-implement, doesn't do anything yet
	}

	/*
	 * Return null if there is no item
	 */
	@Nullable
	protected abstract ItemStack getHeldItemForBone(String boneName, T currentEntity);

	protected abstract TransformType getCameraTransformForItemAtBone(ItemStack boneItem, String boneName);

	/*
	 * Return null if there is no held block
	 */
	@Nullable
	protected abstract BlockState getHeldBlockForBone(String boneName, T currentEntity);

	protected abstract void preRenderItem(PoseStack matrixStack, ItemStack item, String boneName, T currentEntity,
			IBone bone);

	protected abstract void preRenderBlock(BlockState block, String boneName, T currentEntity);

	protected abstract void postRenderItem(PoseStack matrixStack, ItemStack item, String boneName, T currentEntity,
			IBone bone);

	protected abstract void postRenderBlock(BlockState block, String boneName, T currentEntity);

	/*
	 * Return null, if there is no armor on this bone
	 * 
	 */
	@Nullable
	protected ItemStack getArmorForBone(String boneName, T currentEntity) {
		return null;
	}

	@Nullable
	protected EquipmentSlot getEquipmentSlotForArmorBone(String boneName, T currentEntity) {
		return null;
	}

	@Nullable
	protected ModelPart getArmorPartForBone(String name, HumanoidModel<?> armorModel) {
		return null;
	}

	// Copied from BipedArmorLayer
	/**
	 * More generic ForgeHook version of the above function, it allows for Items to
	 * have more control over what texture they provide.
	 *
	 * @param entity Entity wearing the armor
	 * @param stack  ItemStack for the armor
	 * @param slot   Slot ID that the item is in
	 * @param type   Subtype, can be null or "overlay"
	 * @return ResourceLocation pointing at the armor's texture
	 */
	private static final Map<String, ResourceLocation> ARMOR_TEXTURE_RES_MAP = Maps.newHashMap();

	protected ResourceLocation getArmorResource(Entity entity, ItemStack stack, EquipmentSlot slot, String type) {
		ArmorItem item = (ArmorItem) stack.getItem();
		String texture = item.getMaterial().getName();
		String domain = "minecraft";
		int idx = texture.indexOf(':');
		if (idx != -1) {
			domain = texture.substring(0, idx);
			texture = texture.substring(idx + 1);
		}
		String s1 = String.format("%s:textures/models/armor/%s_layer_%d%s.png", domain, texture,
				(slot == EquipmentSlot.LEGS ? 2 : 1), type == null ? "" : String.format("_%s", type));

		s1 = net.minecraftforge.client.ForgeHooksClient.getArmorTexture(entity, stack, s1, slot, type);
		ResourceLocation resourcelocation = (ResourceLocation) ARMOR_TEXTURE_RES_MAP.get(s1);

		if (resourcelocation == null) {
			resourcelocation = new ResourceLocation(s1);
			ARMOR_TEXTURE_RES_MAP.put(s1, resourcelocation);
		}

		return resourcelocation;
	}

}

/*
 * Copyright (c) 2020.
 * Author: Bernie G. (Gecko)
 */

package software.bernie.geckolib.animation.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.util.JsonException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;
import software.bernie.geckolib.GeckoLib;
import software.bernie.geckolib.animation.keyframe.AnimationPoint;
import software.bernie.geckolib.entity.IAnimatedEntity;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.file.AnimationFileManager;
import software.bernie.geckolib.json.JSONAnimationUtils;
import java.util.*;
import java.util.List;

/**
 * An AnimatedEntityModel is the equivalent of an Entity Model, except it provides extra functionality for rendering animations from bedrock json animation files. The entity passed into the generic parameter needs to implement IAnimatedEntity.
 *
 * @param <T> the type parameter
 */
public abstract class AnimatedEntityModel<T extends Entity & IAnimatedEntity> extends ModelBase
{
	private JsonObject animationFile;
	private AnimationFileManager animationFileManager;
	private List<AnimatedModelRenderer> modelRendererList = new ArrayList();
	private HashMap<String, Animation> animationList = new HashMap();
	public List<AnimatedModelRenderer> rootBones = new ArrayList<>();
	/**
	 * This resource location needs to point to a json file of your animation file, i.e. "geckolib:animations/frog_animation.json"
	 *
	 * @return the animation file location
	 */
	public abstract ResourceLocation getAnimationFileLocation();

	/**
	 * If animations should loop by default and ignore their pre-existing loop settings (that you can enable in blockbench by right clicking)
	 */
	public boolean loopByDefault = false;

	/**
	 * Instantiates a new Animated entity model and loads the current animation file.
	 */
	public AnimatedEntityModel()
	{
		super();
		try
		{
			animationFileManager = new AnimationFileManager(getAnimationFileLocation());
			setAnimationFile(animationFileManager.loadAnimationFile());
			loadAllAnimations();
		}
		catch (Exception e)
		{
			GeckoLib.LOGGER.error("Encountered error while loading initial animations.", e);
		}
	}

	private void loadAllAnimations()
	{
		Set<Map.Entry<String, JsonElement>> entrySet = JSONAnimationUtils.getAnimations(getAnimationFile());
		for (Map.Entry<String, JsonElement> entry : entrySet)
		{
			String animationName = entry.getKey();
			Animation animation = null;
			try
			{
				animation = JSONAnimationUtils.deserializeJsonToAnimation(
						JSONAnimationUtils.getAnimation(getAnimationFile(), animationName));
				if (loopByDefault)
				{
					animation.loop = true;
				}
			}
			catch (JsonException e)
			{
				GeckoLib.LOGGER.error("Could not load animation: " + animationName, e);
			}
			animationList.put(animationName, animation);
		}
	}

	/**
	 * Gets the current animation file.
	 *
	 * @return the animation file
	 */
	public JsonObject getAnimationFile()
	{
		return animationFile;
	}

	/**
	 * Sets the animation file to read from.
	 *
	 * @param animationFile The animation file
	 */
	public void setAnimationFile(JsonObject animationFile)
	{
		this.animationFile = animationFile;
	}

	/**
	 * Gets the animation file manager for this model.
	 *
	 * @return the animation file manager
	 */
	public AnimationFileManager getAnimationFileManager()
	{
		return animationFileManager;
	}


	/**
	 * Gets a bone by name.
	 *
	 * @param boneName The bone name
	 * @return the bone
	 */
	public AnimatedModelRenderer getBone(String boneName)
	{
		return modelRendererList.stream().filter(x -> x.name.equals(boneName)).findFirst().orElse(
				null);
	}

	/**
	 * Register model renderer. Each AnimatedModelRenderer (group in blockbench) NEEDS to be registered via this method.
	 *
	 * @param modelRenderer The model renderer
	 */
	public void registerModelRenderer(AnimatedModelRenderer modelRenderer)
	{
		modelRenderer.saveInitialSnapshot();
		modelRendererList.add(modelRenderer);
	}

	/**
	 * Gets a json animation by name.
	 *
	 * @param name The name
	 * @return the animation by name
	 * @throws JsonException
	 */
	public Map.Entry<String, JsonElement> getAnimationByName(String name) throws JsonException
	{
		return JSONAnimationUtils.getAnimation(getAnimationFile(), name);
	}


	/**
	 * Sets a rotation angle.
	 *
	 * @param modelRenderer The animated model renderer
	 * @param x             x
	 * @param y             y
	 * @param z             z
	 */
	public void setRotationAngle(AnimatedModelRenderer modelRenderer, float x, float y, float z)
	{
		modelRenderer.rotateAngleX = x;
		modelRenderer.rotateAngleY = y;
		modelRenderer.rotateAngleZ = z;
	}


	@Override
	public void setLivingAnimations(EntityLivingBase entityIn, float limbSwing, float limbSwingAmount, float partialTick)
	{
		T entity = (T) entityIn;
		// Keeps track of which bones have had animations applied to them, and eventually sets the ones that don't have an animation to their default values
		EntityDirtyTracker modelTracker = createNewDirtyTracker();

		// Adding partial tick to smooth out the animation
		double tick = entity.ticksExisted + partialTick;

		// Each animation has it's own collection of animations (called the AnimationControllerCollection), which allows for multiple independent animations
		AnimationControllerCollection controllers = entity.getAnimationControllers();

		// Store the current value of each bone rotation/position/scale
		if(controllers.boneSnapshotCollection.isEmpty())
		{
			controllers.boneSnapshotCollection = createNewBoneSnapshotCollection();
		}
		BoneSnapshotCollection boneSnapshots = controllers.boneSnapshotCollection;

		for (AnimationController<T> controller : controllers.values())
		{

			AnimationTestEvent<T> animationTestEvent = new AnimationTestEvent<T>(entity, tick, limbSwing, limbSwingAmount, partialTick, controller);

			// Process animations and add new values to the point queues
			controller.process(tick, animationTestEvent, modelRendererList, boneSnapshots);

			// Loop through every single bone and lerp each property
			for (BoneAnimationQueue boneAnimation : controller.getBoneAnimationQueues().values())
			{
				AnimatedModelRenderer bone = boneAnimation.bone;
				BoneSnapshot snapshot = boneSnapshots.get(bone.name);
				BoneSnapshot initialSnapshot = bone.getInitialSnapshot();

				AnimationPoint rXPoint = boneAnimation.rotationXQueue.poll();
				AnimationPoint rYPoint = boneAnimation.rotationYQueue.poll();
				AnimationPoint rZPoint = boneAnimation.rotationZQueue.poll();

				AnimationPoint pXPoint = boneAnimation.positionXQueue.poll();
				AnimationPoint pYPoint = boneAnimation.positionYQueue.poll();
				AnimationPoint pZPoint = boneAnimation.positionZQueue.poll();

				AnimationPoint sXPoint = boneAnimation.scaleXQueue.poll();
				AnimationPoint sYPoint = boneAnimation.scaleYQueue.poll();
				AnimationPoint sZPoint = boneAnimation.scaleZQueue.poll();

				// If there's any rotation points for this bone
				if (rXPoint != null && rYPoint != null && rZPoint != null)
				{
					bone.rotateAngleX = AnimationUtils.lerpValues(rXPoint) + initialSnapshot.rotationValueX;
					bone.rotateAngleY = AnimationUtils.lerpValues(rYPoint) + initialSnapshot.rotationValueY;
					bone.rotateAngleZ = AnimationUtils.lerpValues(rZPoint) + initialSnapshot.rotationValueZ;
					snapshot.rotationValueX = bone.rotateAngleX;
					snapshot.rotationValueY = bone.rotateAngleY;
					snapshot.rotationValueZ = bone.rotateAngleZ;

					modelTracker.get(bone).hasRotationChanged = true;
				}
				// If there's any position points for this bone
				if (pXPoint != null && pYPoint != null && pZPoint != null)
				{
					bone.positionOffsetX = AnimationUtils.lerpValues(pXPoint);
					bone.positionOffsetY = AnimationUtils.lerpValues(pYPoint);
					bone.positionOffsetZ = AnimationUtils.lerpValues(pZPoint);
					snapshot.positionOffsetX = bone.positionOffsetX;
					snapshot.positionOffsetY = bone.positionOffsetY;
					snapshot.positionOffsetZ = bone.positionOffsetZ;
					modelTracker.get(bone).hasPositionChanged = true;
				}

				// If there's any scale points for this bone
				if (sXPoint != null && sYPoint != null && sZPoint != null)
				{
					bone.scaleValueX = AnimationUtils.lerpValues(sXPoint);
					bone.scaleValueY = AnimationUtils.lerpValues(sYPoint);
					bone.scaleValueZ = AnimationUtils.lerpValues(sZPoint);
					snapshot.scaleValueX = bone.scaleValueX;
					snapshot.scaleValueY = bone.scaleValueY;
					snapshot.scaleValueZ = bone.scaleValueZ;
					modelTracker.get(bone).hasScaleChanged = true;
				}
			}
		}

		for (DirtyTracker tracker : modelTracker)
		{
			AnimatedModelRenderer model = tracker.model;
			BoneSnapshot initialSnapshot = model.getInitialSnapshot();
			BoneSnapshot saveSnapshot = boneSnapshots.get(tracker.model.name);

			if (!tracker.hasRotationChanged)
			{
				model.rotateAngleX = lerpConstant(saveSnapshot.rotationValueX, initialSnapshot.rotationValueX, 0.02);
				model.rotateAngleY = lerpConstant(saveSnapshot.rotationValueY, initialSnapshot.rotationValueY, 0.02);
				model.rotateAngleZ = lerpConstant(saveSnapshot.rotationValueZ, initialSnapshot.rotationValueZ, 0.02);
				saveSnapshot.rotationValueX = model.rotateAngleX;
				saveSnapshot.rotationValueY = model.rotateAngleY;
				saveSnapshot.rotationValueZ = model.rotateAngleZ;
			}
			if (!tracker.hasPositionChanged)
			{
				model.positionOffsetX = lerpConstant(saveSnapshot.positionOffsetX, initialSnapshot.positionOffsetX, 0.02);
				model.positionOffsetY = lerpConstant(saveSnapshot.positionOffsetY, initialSnapshot.positionOffsetY, 0.02);
				model.positionOffsetZ = lerpConstant(saveSnapshot.positionOffsetZ, initialSnapshot.positionOffsetZ, 0.02);
				saveSnapshot.positionOffsetX = model.positionOffsetX;
				saveSnapshot.positionOffsetY = model.positionOffsetY;
				saveSnapshot.positionOffsetZ = model.positionOffsetZ;
			}
			if (!tracker.hasScaleChanged)
			{
				if(tracker.model.name.equals("Righthand"))
				{
					int sdf = 0;
					//GeckoLib.LOGGER.info(model.rotateAngleX);
				}
				model.scaleValueX = lerpConstant(saveSnapshot.scaleValueX, initialSnapshot.scaleValueX, 0.02);
				model.scaleValueY = lerpConstant(saveSnapshot.scaleValueY, initialSnapshot.scaleValueY, 0.02);
				model.scaleValueZ = lerpConstant(saveSnapshot.scaleValueZ, initialSnapshot.scaleValueZ, 0.02);
				saveSnapshot.scaleValueX = model.scaleValueX;
				saveSnapshot.scaleValueY = model.scaleValueY;
				saveSnapshot.scaleValueZ = model.scaleValueZ;
			}
		}
	}

	private EntityDirtyTracker createNewDirtyTracker()
	{
		EntityDirtyTracker tracker = new EntityDirtyTracker();
		for(AnimatedModelRenderer bone : modelRendererList)
		{
			tracker.add(new DirtyTracker(false, false, false, bone));
		}
		return tracker;
	}

	private BoneSnapshotCollection createNewBoneSnapshotCollection()
	{
		BoneSnapshotCollection collection = new BoneSnapshotCollection();
		for(AnimatedModelRenderer bone : modelRendererList)
		{
			collection.put(bone.name, new BoneSnapshot(bone.getInitialSnapshot()));
		}
		return collection;
	}

	/**
	 * Gets animation.
	 *
	 * @param name The name
	 * @return the animation
	 */
	public Animation getAnimation(String name)
	{
		return animationList.get(name);
	}

	private static float lerpConstant(double currentValue, double finalValue, double speedModifier)
	{
		double lowerBound = finalValue - speedModifier;
		double upperBound = finalValue + speedModifier;

		if(lowerBound <= currentValue && upperBound >= currentValue)
		{
			return (float) currentValue;
		}
		double increment = 0;
		if(currentValue < finalValue)
		{
			increment = speedModifier;
		}
		else {
			increment = -1 * speedModifier;
		}

		return (float) (currentValue + increment);
	}

	@Override
	public void render(Entity entityIn, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, float scale)
	{
		for(AnimatedModelRenderer model : rootBones)
		{
			model.render(scale);
		}
	}

}

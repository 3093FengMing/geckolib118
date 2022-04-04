package software.bernie.example.block.tile;

import net.minecraft.block.entity.BlockEntity;
import software.bernie.example.registry.TileRegistry;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.PlayState;
import software.bernie.geckolib3.core.builder.AnimationBuilder;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;

public class FertilizerTileEntity extends BlockEntity implements IAnimatable {
	private final AnimationFactory manager = new AnimationFactory(this);

	public FertilizerTileEntity() {
		super(TileRegistry.FERTILIZER);
	}

	@SuppressWarnings("unchecked")
	private <E extends BlockEntity & IAnimatable> PlayState predicate(AnimationEvent<E> event) {
		event.getController().transitionLengthTicks = 0;
		if (event.getAnimatable().getWorld().isRaining()) {
			event.getController().setAnimation(new AnimationBuilder().addAnimation("fertilizer.animation.deploy", true)
					.addAnimation("fertilizer.animation.idle", true));
		} else {
			event.getController().setAnimation(new AnimationBuilder().addAnimation("Botarium.anim.deploy", true)
					.addAnimation("Botarium.anim.idle", true));
		}
		return PlayState.CONTINUE;
	}

	@Override
	public void registerControllers(AnimationData data) {
		data.addAnimationController(new AnimationController<FertilizerTileEntity>(this, "controller", 0, this::predicate));
	}

	@Override
	public AnimationFactory getFactory() {
		return this.manager;
	}
}

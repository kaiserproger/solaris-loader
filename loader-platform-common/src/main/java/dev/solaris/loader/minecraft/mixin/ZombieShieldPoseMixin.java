package dev.solaris.loader.minecraft.mixin;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.AbstractZombieRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla zombie renderers omit the ordinary shield arm pose, even while blocking. */
@Mixin(AbstractZombieRenderer.class)
abstract class ZombieShieldPoseMixin {
    @Inject(method = "getArmPose(Lnet/minecraft/world/entity/monster/zombie/Zombie;Lnet/minecraft/world/entity/HumanoidArm;)Lnet/minecraft/client/model/HumanoidModel$ArmPose;",
            at = @At("HEAD"), cancellable = true)
    private void solaris$shieldPose(Zombie entity, HumanoidArm arm,
                                  CallbackInfoReturnable<HumanoidModel.ArmPose> callback) {
        HumanoidArm usedArm = entity.getUsedItemHand() == InteractionHand.MAIN_HAND
                ? entity.getMainArm() : entity.getMainArm().getOpposite();
        if (entity.isBlocking() && arm == usedArm) {
            callback.setReturnValue(HumanoidModel.ArmPose.BLOCK);
        }
    }
}

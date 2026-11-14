package dev.solaris.loader.minecraft.mixin;

import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.monster.zombie.AbstractZombieModel;
import net.minecraft.client.renderer.entity.state.UndeadRenderState;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keep HumanoidModel's already-computed blocking pose instead of overwriting both arms. */
@Mixin(AbstractZombieModel.class)
abstract class ZombieShieldAnimationMixin {
    @Redirect(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/ZombieRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/AnimationUtils;animateZombieArms(Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;ZLnet/minecraft/client/renderer/entity/state/UndeadRenderState;)V"))
    private void solaris$preserveShield(ModelPart left, ModelPart right, boolean aggressive, UndeadRenderState state) {
        ZombieRenderState zombie = (ZombieRenderState) state;
        if (zombie.leftArmPose != HumanoidModel.ArmPose.BLOCK
                && zombie.rightArmPose != HumanoidModel.ArmPose.BLOCK) {
            AnimationUtils.animateZombieArms(left, right, aggressive, state);
        }
    }
}

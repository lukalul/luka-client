package dev.envoy.mixins;

import dev.envoy.Envoy;
import dev.envoy.module.combat.CombatState;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.MovingObjectPosition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MixinCombat {
    @Shadow private int leftClickCounter;
    @Shadow public MovingObjectPosition objectMouseOver;
    @Shadow public WorldClient theWorld;

    @Inject(method = "clickMouse", at = @At("RETURN"), require = 0)
    private void envoy$reconsiderClickDelay(CallbackInfo callback) {
        try {
            if (this.leftClickCounter == 10 && penaltyMayBeRemoved(this.objectMouseOver)) this.leftClickCounter = 0;
        } catch (Throwable failure) {
            Envoy.problem("combat click", failure);
        }
    }

    private boolean penaltyMayBeRemoved(MovingObjectPosition trace) {
        if (trace == null || trace.typeOfHit == MovingObjectPosition.MovingObjectType.MISS) {
            return CombatState.disableMissPenalty();
        }
        if (trace.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK || this.theWorld == null) return false;
        Material material = this.theWorld.getBlockState(trace.getBlockPos()).getBlock().getMaterial();
        return material == Material.air && CombatState.disableBlockMissPenalty();
    }

    @Redirect(method = "rightClickMouse", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/PlayerControllerMP;getIsHittingBlock()Z"), require = 0)
    private boolean envoy$gateConcurrentUse(PlayerControllerMP controller) {
        final boolean digging;
        try {
            digging = controller.getIsHittingBlock();
        } catch (Throwable failure) {
            Envoy.problem("combat use check", failure);
            return true;
        }
        if (!digging) return false;
        try {
            return !CombatState.allowDigAndUse();
        } catch (Throwable failure) {
            Envoy.problem("combat use override", failure);
            return true;
        }
    }
}

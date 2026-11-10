package dev.envoy.mixins;

import dev.envoy.Envoy;
import dev.envoy.module.combat.CombatState;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.MovingObjectPosition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Miss-penalty handling. The 1.8 hook on {@code getIsHittingBlock} is gone: {@code rightClickMouse} in 1.7.10
 * has no digging gate, so dig-and-use already works and there is nothing to redirect.
 */
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
        if (trace == null || "MISS".equals(trace.typeOfHit.name())) {
            return CombatState.disableMissPenalty();
        }
        if (!"BLOCK".equals(trace.typeOfHit.name()) || this.theWorld == null) return false;
        Material material = this.theWorld.getBlock(trace.blockX, trace.blockY, trace.blockZ).getMaterial();
        return material == Material.air && CombatState.disableBlockMissPenalty();
    }
}

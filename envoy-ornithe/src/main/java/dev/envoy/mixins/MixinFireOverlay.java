package dev.envoy.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.envoy.EnvoySettings;
import dev.envoy.module.coloredfire.ColoredFireState;
import dev.envoy.module.coloredfire.FlameRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.entity.player.EntityPlayer;

/** The first-person fire overlay in the local player's Colored Fire color (untinted: the game draws it as always). */
@Mixin(ItemRenderer.class)
public class MixinFireOverlay {

    @Inject(method = "renderFireInFirstPerson", at = @At("HEAD"), cancellable = true, require = 0)
    private void envoy$tintFireOverlay(float partialTicks, CallbackInfo ci) {
        if (!EnvoySettings.coloredFire) return;
        EntityPlayer self = Minecraft.getMinecraft().thePlayer;
        if (self == null) return;
        int tint = ColoredFireState.colorOrZero(self.getUniqueID());
        if (tint != 0 && FlameRenderer.drawOverlay(tint)) ci.cancel();
    }
}

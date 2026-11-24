package dev.envoy.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.envoy.module.coloredfire.ColoredFireState;
import dev.envoy.module.coloredfire.FlameRenderer;
import dev.envoy.EnvoySettings;
import dev.luka.compat.GlStateManager;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Tints a player's flames with their Colored Fire color. A tinted player's fire is drawn by FlameRenderer with a grey
 * copy of the fire texture (the orange texture times pure blue was almost black). Only if that texture cannot be
 * built does the game draw it, and the colour redirect below multiplies the normal fire as before.
 */
@Mixin(Render.class)
public class MixinRenderFire {

    private static int envoy$activeTint;

    @Inject(method = "renderEntityOnFire", at = @At("HEAD"), cancellable = true, require = 0)
    private void envoy$selectFlameTint(Entity entity, double x, double y, double z, float partialTicks,
                                          CallbackInfo ci) {
        envoy$activeTint = tintFor(entity);
        if (envoy$activeTint != 0 && FlameRenderer.drawOnEntity(entity, x, y, z, envoy$activeTint)) ci.cancel();
    }

    private static int tintFor(Entity entity) {
        if (!EnvoySettings.coloredFire || !(entity instanceof EntityPlayer)) return 0;
        return ColoredFireState.colorOrZero(entity.getUniqueID());
    }

    @Redirect(
        method = "renderEntityOnFire",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glColor4f(FFFF)V", remap = false),
        require = 0)
    private void envoy$applyFlameTint(float red, float green, float blue, float alpha) {
        int tint = envoy$activeTint;
        if (tint == 0) {
            GlStateManager.color(red, green, blue, alpha);
            return;
        }
        GlStateManager.color(component(tint, 16), component(tint, 8), component(tint, 0), alpha);
    }

    private static float component(int rgb, int shift) {
        return (rgb >> shift & 255) / 255.0F;
    }
}

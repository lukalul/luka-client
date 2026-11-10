package dev.cbui.mixin;

import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Gives the shader-free menu blur its place in the frame: after the world, before the HUD. */
@Mixin(EntityRenderer.class)
public abstract class GuiBlurFallbackMixin {
    @Inject(method = "updateCameraAndRender", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/EntityRenderer;renderWorld(FJ)V", shift = At.Shift.AFTER), require = 0)
    private void cbui$blurAfterWorld(CallbackInfo callback) {
        dev.cbui.render.GuiBlur.afterWorld();
    }
}

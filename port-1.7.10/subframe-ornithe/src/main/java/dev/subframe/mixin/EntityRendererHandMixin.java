package dev.subframe.mixin;

import dev.subframe.Freecam;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The first-person hand belongs to the recorded player: it is not drawn while the view is the free camera or someone else's eyes. */
@Mixin(EntityRenderer.class)
abstract class EntityRendererHandMixin {
    @Inject(method = "renderHand", at = @At("HEAD"), cancellable = true, require = 0)
    private void subframe$noHand(float partialTicks, int pass, CallbackInfo ci) {
        if (Freecam.detached()) {
            ci.cancel();
        }
    }
}

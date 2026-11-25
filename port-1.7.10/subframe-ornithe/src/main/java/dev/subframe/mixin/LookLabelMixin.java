package dev.subframe.mixin;

import dev.subframe.SubFrameMod;
import net.minecraft.client.renderer.entity.Render;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replay editor "hide nametags": no name labels over entities in that render. Priority 500: before luka's own label hooks. */
@Mixin(value = Render.class, priority = 500)
abstract class LookLabelMixin {
    @Inject(method = "renderLivingLabel", at = @At("HEAD"), cancellable = true, require = 0)
    private void subframe$hideLabel(CallbackInfo ci) {
        if (SubFrameMod.lookHides("nametags")) {
            ci.cancel();
        }
    }
}

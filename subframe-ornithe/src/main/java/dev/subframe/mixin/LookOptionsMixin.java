package dev.subframe.mixin;

import dev.subframe.SubFrameMod;
import net.minecraft.client.settings.GameSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A replay editor render runs in its own copy of the game, with options changed for that video (FOV, brightness, ...).
 * It never writes options.txt, so nothing of that reaches the player's settings.
 */
@Mixin(value = GameSettings.class, priority = 500)
abstract class LookOptionsMixin {
    @Inject(method = "saveOptions", at = @At("HEAD"), cancellable = true, require = 0)
    private void subframe$noSaveInRenderJob(CallbackInfo ci) {
        if (SubFrameMod.inRenderJob()) {
            ci.cancel();
        }
    }
}

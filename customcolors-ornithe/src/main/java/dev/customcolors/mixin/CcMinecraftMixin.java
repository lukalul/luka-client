package dev.customcolors.mixin;

import dev.customcolors.CustomColorsMod;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Starts the mod once the game is up and steps pack animations every client tick. */
@Mixin(Minecraft.class)
public abstract class CcMinecraftMixin {
    @Inject(method = "startGame", at = @At("TAIL"), require = 0)
    private void customcolors$start(CallbackInfo ci) {
        try {
            CustomColorsMod.start((Minecraft) (Object) this);
        } catch (Throwable t) {
            CustomColorsMod.reportOnce("start-up", t);
        }
    }

    @Inject(method = "runTick", at = @At("HEAD"), require = 0)
    private void customcolors$tick(CallbackInfo ci) {
        try {
            CustomColorsMod.tick();
        } catch (Throwable t) {
            CustomColorsMod.reportOnce("tick", t);
        }
    }
}

package dev.subframe.mixin;

import dev.subframe.ServerTasks;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
abstract class MinecraftServerMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void subframe$runTasks(CallbackInfo ci) {
        ServerTasks.drain();
    }
}

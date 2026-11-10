package dev.hcfopt.mixin;

import dev.hcfopt.OslServerRace;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** See {@link OslServerRace}: a server's thread remembers the one before it; a running server keeps the library pointed at itself. */
@Mixin(value = MinecraftServer.class, priority = 500)
public abstract class OslServerRaceMixin {
  @Inject(method = "run", at = @At("HEAD"), require = 0, remap = false)
  private void hcfopt$running(CallbackInfo ci) {
    OslServerRace.running((MinecraftServer) (Object) this);
  }

  @Inject(method = "tick", at = @At("HEAD"), require = 0)
  private void hcfopt$keepServer(CallbackInfo ci) {
    OslServerRace.keep((MinecraftServer) (Object) this);
  }
}

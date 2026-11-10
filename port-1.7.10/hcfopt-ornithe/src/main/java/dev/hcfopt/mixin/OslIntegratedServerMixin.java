package dev.hcfopt.mixin;

import dev.hcfopt.OslServerRace;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.integrated.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** See {@link OslServerRace}: the singleplayer server waits for the old one to end, then points the library at itself. */
@Mixin(value = IntegratedServer.class, priority = 500)
public abstract class OslIntegratedServerMixin {
  @Inject(method = "startServer", at = @At("HEAD"), require = 0)
  private void hcfopt$keepServer(CallbackInfoReturnable<Boolean> cir) {
    OslServerRace.starting((MinecraftServer) (Object) this);
  }
}

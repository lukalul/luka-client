package dev.cbui.mixin;

import dev.cbui.feature.fovchanger.FOVChangerModule;
import net.minecraft.client.entity.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Replaces the value of the player's vanilla FOV modifier with the module's own calculation. */
@Mixin(AbstractClientPlayer.class)
public abstract class FOVChangerPlayerMixin {
  @Inject(method = "getFovModifier", at = @At("RETURN"), cancellable = true, require = 0)
  private void cbui$replaceModifier(CallbackInfoReturnable<Float> cir) {
    FOVChangerModule module = FOVChangerModule.active();
    if (module == null) return;
    try {
      cir.setReturnValue(module.playerModifier((AbstractClientPlayer) (Object) this, cir.getReturnValue()));
    } catch (Throwable failure) {
      // keep vanilla's value
    }
  }
}

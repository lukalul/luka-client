package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.cbui.feature.overlaytweaks.OverlayTweaksModule;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = EntityRenderer.class, priority = 1010)
public abstract class OverlayTweaksEntityRendererMixin {
  /** renderHand skips the hand when the HUD is hidden; this lets it carry on if "Keep Hand in Hidden HUD" is on. */
  @ModifyExpressionValue(
      method = "renderHand",
      at = @At(value = "FIELD", target = "Lnet/minecraft/client/settings/GameSettings;hideGUI:Z"),
      require = 0)
  private boolean cbui$handDespiteHiddenHud(boolean hudHidden) {
    OverlayTweaksModule module = OverlayTweaksModule.active();
    return module == null ? hudHidden : hudHidden && !module.keepHand.get();
  }
}

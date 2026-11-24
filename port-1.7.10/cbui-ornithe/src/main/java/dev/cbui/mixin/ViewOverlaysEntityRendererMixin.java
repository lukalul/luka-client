package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.cbui.Cbui;
import dev.cbui.feature.viewoverlays.ViewOverlaysModule;
import net.minecraft.client.renderer.EntityRenderer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Overlay Tweaks: lets the hand pass ignore F1 when the player asked to keep the hand. */
@Mixin(EntityRenderer.class)
public abstract class ViewOverlaysEntityRendererMixin {
  @ModifyExpressionValue(method = "renderHand",
      at = @At(value = "FIELD", target = "Lnet/minecraft/client/settings/GameSettings;hideGUI:Z", opcode = Opcodes.GETFIELD),
      require = 0)
  private boolean cbui$handIgnoresF1(boolean hidden) {
    try {
      ViewOverlaysModule module = ViewOverlaysModule.active();
      return module == null ? hidden : module.handPassSeesHiddenHud(hidden);
    } catch (Throwable error) {
      Cbui.reportOnce("Overlay Tweaks hand", error);
      return hidden;
    }
  }
}

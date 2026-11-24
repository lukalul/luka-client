package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cbui.hud.module.BossBarModule;
import net.minecraft.client.gui.GuiIngame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The boss bar is drawn by the Boss bar HUD module, at its place in the HUD editor, instead of at the top of the screen. */
@Mixin(GuiIngame.class)
public abstract class BossBarGuiIngameMixin {
  @WrapOperation(
      method = "renderGameOverlay(FZII)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiIngame;renderBossHealth()V"),
      require = 0)
  private void cbui$bossBarToHud(GuiIngame gui, Operation<Void> original) {
    boolean moved;
    try {
      moved = BossBarModule.takesOver();
    } catch (Throwable error) {
      moved = false;
    }
    if (!moved) original.call(gui);
  }
}

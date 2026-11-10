package dev.cbui.mixin;

import dev.cbui.config.GlobalOptions;
import net.minecraft.client.gui.achievement.GuiAchievement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Skips drawing the achievement toast while "Hide achievement popups" is on. */
@Mixin(GuiAchievement.class)
public abstract class GuiAchievementMixin {
  @Inject(method = "updateAchievementWindow", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$hideToast(CallbackInfo ci) {
    if (GlobalOptions.hideAchievements) ci.cancel();
  }
}

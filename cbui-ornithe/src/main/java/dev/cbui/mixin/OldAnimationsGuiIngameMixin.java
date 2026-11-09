package dev.cbui.mixin;

import dev.cbui.feature.oldanimations.OldAnimationsHooks;
import net.minecraft.client.gui.GuiIngame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Old Animations: the 1.7 health flash (hearts blink while the player is invulnerable after a hit). */
@Mixin(GuiIngame.class)
public abstract class OldAnimationsGuiIngameMixin {
  @ModifyVariable(method = "renderPlayerStats", at = @At(value = "STORE", ordinal = 0), index = 4, require = 0)
  private boolean cbui$oldHealthFlash(boolean flash) {
    try { return OldAnimationsHooks.healthFlash(flash); } catch (Throwable ignored) { return flash; }
  }
}

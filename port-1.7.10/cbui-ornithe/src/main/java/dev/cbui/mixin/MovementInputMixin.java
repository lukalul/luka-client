package dev.cbui.mixin;

import dev.cbui.hud.module.ToggleSneakModule;
import net.minecraft.util.MovementInput;
import net.minecraft.util.MovementInputFromOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets the Toggle sneak module decide the sneak state after vanilla has read the keys. */
@Mixin(MovementInputFromOptions.class)
public abstract class MovementInputMixin extends MovementInput {
  @Inject(method = "updatePlayerMoveState", at = @At("RETURN"), require = 0)
  private void cbui$toggleSneak(CallbackInfo ci) {
    try {
      if (!ToggleSneakModule.overriding()) return;
      boolean before = this.sneak;
      boolean after = ToggleSneakModule.sneaking();
      this.sneak = after;
      // vanilla slows movement to 30 percent inside updatePlayerMoveState when sneak was set; redo that for the changed state
      // (without it a toggled sneak moved at full speed, and sprint stayed on while "sneaking")
      if (after && !before) {
        this.moveStrafe *= 0.3F;
        this.moveForward *= 0.3F;
      } else if (!after && before) {
        this.moveStrafe /= 0.3F;
        this.moveForward /= 0.3F;
      }
    } catch (Throwable ignored) {
      // fail open: vanilla sneak stays as it was
    }
  }
}

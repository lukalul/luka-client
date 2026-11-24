package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cbui.Cbui;
import dev.cbui.feature.lookaround.LookAroundModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Freelook: the view key's write of the camera view (its first write in the tick) obeys the card's F5 choice. */
@Mixin(Minecraft.class)
public abstract class LookAroundMinecraftMixin {
  @WrapOperation(method = "runTick", at = @At(value = "FIELD",
      target = "Lnet/minecraft/client/settings/GameSettings;thirdPersonView:I", opcode = 181 /* PUTFIELD */, ordinal = 0), require = 0)
  private void cbui$lookAroundViewKey(GameSettings settings, int value, Operation<Void> original) {
    int write = value;
    LookAroundModule look = LookAroundModule.instance();
    if (look != null) {
      try {
        write = look.viewKeyWrite(value);
      } catch (Throwable error) {
        Cbui.reportOnce("freelook view key", error);
        write = value;
      }
    }
    if (write >= 0) original.call(settings, write);
  }
}

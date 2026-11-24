package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cbui.feature.Features;
import dev.cbui.feature.skyclock.SkyClockModule;
import net.minecraft.client.multiplayer.WorldClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Time Changer: the client world advances its clock by reading the time and writing it back plus one. That read
 * must see the stored time, not the shown one, or the shown time would be written into the world.
 */
@Mixin(WorldClient.class)
public abstract class SkyClockWorldClientMixin {
  @WrapOperation(method = "tick", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/client/multiplayer/WorldClient;getWorldTime()J"), require = 0)
  private long cbui$storedTimeForClockStep(WorldClient world, Operation<Long> original) {
    SkyClockModule module = Features.get(SkyClockModule.class);
    if (module == null) return original.call(world);
    module.rawBegin();
    try {
      return original.call(world);
    } finally {
      module.rawEnd();
    }
  }
}

package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.cbui.feature.smoothskies.SmoothSkiesModule;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(World.class)
public abstract class SmoothSkiesWorldMixin {
  @ModifyReturnValue(method = "getHorizon", at = @At("RETURN"), require = 0)
  private double cbui$horizonAtBottom(double vanillaHorizon) {
    SmoothSkiesModule module = SmoothSkiesModule.active();
    return module != null && module.lowerSkyVoidDarkness.get() ? 0D : vanillaHorizon;
  }
}

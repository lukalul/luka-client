package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cbui.feature.rendertweaks.RenderTweaksModule;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.world.border.EnumBorderStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RenderGlobal.class)
public abstract class RenderTweaksRenderGlobalMixin {
  @Shadow private WorldClient theWorld;

  @WrapOperation(
      method = "renderWorldBorder",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;color(FFFF)V"),
      require = 0)
  private void cbui$recolourBorder(float r, float g, float b, float a, Operation<Void> vanilla) {
    boolean hidden;
    int argb;
    try {
      RenderTweaksModule module = RenderTweaksModule.active();
      if (module == null || theWorld == null) {
        vanilla.call(r, g, b, a);
        return;
      }
      EnumBorderStatus status = theWorld.getWorldBorder().getStatus();
      hidden = module.hidesBorder(status);
      argb = module.borderColor(status);
    } catch (Throwable failure) {
      vanilla.call(r, g, b, a);
      return;
    }
    if (hidden) vanilla.call(r, g, b, 0F);
    else vanilla.call(RenderTweaksModule.channel(argb, 16), RenderTweaksModule.channel(argb, 8),
        RenderTweaksModule.channel(argb, 0), RenderTweaksModule.channel(argb, 24));
  }
}

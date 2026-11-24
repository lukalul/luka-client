package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cbui.Cbui;
import dev.cbui.feature.worldfx.WorldEffectsModule;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderFish;
import net.minecraft.entity.projectile.EntityFishHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Render Tweaks: a cast fishing rod. The bobber is the first draw of the method, the line the second; only the line
 * gets the colour, thickness and blending, and the thickness is put back right after it.
 */
@Mixin(RenderFish.class)
public abstract class WorldFxRenderFishMixin {
  @Inject(method = "doRender(Lnet/minecraft/entity/projectile/EntityFishHook;DDDFF)V", at = @At("HEAD"),
      cancellable = true, require = 0)
  private void cbui$castShown(EntityFishHook hook, double x, double y, double z, float yaw, float partialTicks,
      CallbackInfo ci) {
    try {
      WorldEffectsModule module = WorldEffectsModule.active();
      if (module != null && module.skipCast()) ci.cancel();
    } catch (Throwable error) {
      Cbui.reportOnce("Render Tweaks fishing", error);
    }
  }

  @WrapOperation(method = "doRender(Lnet/minecraft/entity/projectile/EntityFishHook;DDDFF)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/WorldRenderer;color(IIII)Lnet/minecraft/client/renderer/WorldRenderer;"),
      require = 0)
  private WorldRenderer cbui$lineColour(WorldRenderer buffer, int red, int green, int blue, int alpha,
      Operation<WorldRenderer> original) {
    int r = red, g = green, b = blue, a = alpha;
    try {
      WorldEffectsModule module = WorldEffectsModule.active();
      if (module != null && !module.lineIsVanilla()) {
        r = module.lineChannel(0);
        g = module.lineChannel(1);
        b = module.lineChannel(2);
        a = module.lineChannel(3);
      }
    } catch (Throwable error) {
      Cbui.reportOnce("Render Tweaks fishing", error);
      r = red; g = green; b = blue; a = alpha;
    }
    return original.call(buffer, r, g, b, a);
  }

  @WrapOperation(method = "doRender(Lnet/minecraft/entity/projectile/EntityFishHook;DDDFF)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;draw()V", ordinal = 1),
      require = 0)
  private void cbui$lineDraw(Tessellator tessellator, Operation<Void> original) {
    WorldEffectsModule module = null;
    try {
      module = WorldEffectsModule.active();
      if (module != null) module.beforeLine();
    } catch (Throwable error) {
      Cbui.reportOnce("Render Tweaks fishing", error);
    }
    try {
      original.call(tessellator);
    } finally {
      if (module != null) {
        try {
          module.afterLine();
        } catch (Throwable error) {
          Cbui.reportOnce("Render Tweaks fishing", error);
        }
      }
    }
  }
}

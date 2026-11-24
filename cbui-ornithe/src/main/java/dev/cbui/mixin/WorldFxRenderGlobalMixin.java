package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cbui.Cbui;
import dev.cbui.feature.worldfx.WorldEffectsModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.world.border.EnumBorderStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Render Tweaks: the world border picture per state. The tint replaces vanilla's state colour, and its alpha multiplies
 * vanilla's fade with distance. The border itself still blocks movement; only the drawing changes.
 */
@Mixin(RenderGlobal.class)
public abstract class WorldFxRenderGlobalMixin {
  @Inject(method = "renderWorldBorder", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$borderShown(Entity viewer, float partialTicks, CallbackInfo ci) {
    try {
      WorldEffectsModule module = WorldEffectsModule.active();
      if (module == null) return;
      EnumBorderStatus status = cbui$status();
      if (status != null && module.skipBorder(status)) ci.cancel();
    } catch (Throwable error) {
      Cbui.reportOnce("Render Tweaks border", error);
    }
  }

  @WrapOperation(method = "renderWorldBorder",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;color(FFFF)V", ordinal = 0),
      require = 0)
  private void cbui$borderTint(float red, float green, float blue, float fade, Operation<Void> original) {
    float r = red, g = green, b = blue, a = fade;
    try {
      WorldEffectsModule module = WorldEffectsModule.active();
      EnumBorderStatus status = module == null ? null : cbui$status();
      if (status != null && !module.borderIsVanilla(status)) {
        r = module.borderChannel(status, 0);
        g = module.borderChannel(status, 1);
        b = module.borderChannel(status, 2);
        a = fade * module.borderChannel(status, 3);
      }
    } catch (Throwable error) {
      Cbui.reportOnce("Render Tweaks border", error);
      r = red; g = green; b = blue; a = fade;
    }
    original.call(r, g, b, a);
  }

  private static EnumBorderStatus cbui$status() {
    Minecraft mc = Minecraft.getMinecraft();
    if (mc == null || mc.theWorld == null) return null;
    return mc.theWorld.getWorldBorder().getStatus();
  }
}

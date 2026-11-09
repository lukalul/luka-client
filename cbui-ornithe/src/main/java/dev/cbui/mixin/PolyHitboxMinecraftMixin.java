package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cbui.Cbui;
import dev.cbui.feature.Features;
import dev.cbui.feature.polyhitbox.PolyHitboxModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Redirects the F3+B hitbox shortcut to the module's own toggle whenever the module is active. */
@Mixin(Minecraft.class)
public abstract class PolyHitboxMinecraftMixin {
  @WrapOperation(
      method = "runTick",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/RenderManager;setDebugBoundingBox(Z)V"),
      require = 0)
  private void cbui$routeDebugBoxKey(RenderManager manager, boolean newValue, Operation<Void> vanilla) {
    if (!cbui$moduleHandledKey()) vanilla.call(manager, newValue);
  }

  private static boolean cbui$moduleHandledKey() {
    try {
      PolyHitboxModule module = Features.get(PolyHitboxModule.class);
      if (module == null || !module.enabled()) return false;
      module.flipToggle();
      return true;
    } catch (Throwable failure) {
      Cbui.reportOnce("hitbox F3+B hook", failure);
      return false;
    }
  }
}

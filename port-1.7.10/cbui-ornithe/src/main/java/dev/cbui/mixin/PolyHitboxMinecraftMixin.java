package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.cbui.Cbui;
import dev.cbui.feature.Features;
import dev.cbui.feature.polyhitbox.PolyHitboxModule;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Redirects the F3+B hitbox shortcut to the module's own toggle whenever the module is active. */
@Mixin(Minecraft.class)
public abstract class PolyHitboxMinecraftMixin {
  @WrapWithCondition(
      method = "runTick",
      at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/entity/RenderManager;debugBoundingBox:Z", opcode = org.objectweb.asm.Opcodes.PUTSTATIC),
      require = 0)
  private boolean cbui$routeDebugBoxKey(boolean newValue) {
    return !cbui$moduleHandledKey();
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

package dev.cbui.mixin;

import dev.cbui.feature.hitboxes.HitboxesModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderManager;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Hitboxes: on 1.7.10 F3+B writes the static debug-box flag directly. While the module is on, that write is dropped
 * and the module's toggle flips instead.
 */
@Mixin(Minecraft.class)
public abstract class HitboxesMinecraftMixin {
  @Redirect(method = "runTick",
      at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/entity/RenderManager;debugBoundingBox:Z",
          opcode = Opcodes.PUTSTATIC),
      require = 0)
  private void cbui$hitboxDebugKey(boolean show) {
    if (HitboxesModule.takeDebugKey()) return;
    RenderManager.debugBoundingBox = show;
  }
}

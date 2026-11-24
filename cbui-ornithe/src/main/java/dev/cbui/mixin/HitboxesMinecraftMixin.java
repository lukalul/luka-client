package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cbui.feature.hitboxes.HitboxesModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Hitboxes: while the module is on, F3+B flips the module's toggle instead of the game's debug boxes. */
@Mixin(Minecraft.class)
public abstract class HitboxesMinecraftMixin {
  @WrapOperation(method = "runTick",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/RenderManager;setDebugBoundingBox(Z)V"),
      require = 0)
  private void cbui$hitboxDebugKey(RenderManager manager, boolean show, Operation<Void> original) {
    if (HitboxesModule.takeDebugKey()) return;
    original.call(manager, show);
  }
}

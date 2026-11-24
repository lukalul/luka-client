package dev.cbui.mixin;

import dev.cbui.config.GlobalOptions;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** "Entity shadows" off (Performance, World): the round shadow under entities is not drawn. 1.7.10 has no game option for it. */
@Mixin(Render.class)
public abstract class EntityShadowsMixin {
  @Inject(method = "renderShadow", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$noShadow(Entity entity, double x, double y, double z, float shadowAlpha, float partialTicks, CallbackInfo ci) {
    if (!GlobalOptions.entityShadows) ci.cancel();
  }
}

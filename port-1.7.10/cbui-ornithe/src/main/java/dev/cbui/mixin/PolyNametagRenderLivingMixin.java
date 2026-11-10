package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.cbui.Cbui;
import dev.cbui.feature.polynametag.NametagHooks;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.entity.EntityLiving;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RenderLiving.class)
public abstract class PolyNametagRenderLivingMixin {
  @ModifyExpressionValue(method = "canRenderName(Lnet/minecraft/entity/EntityLiving;)Z",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/EntityLiving;getAlwaysRenderNameTagForRender()Z"), require = 0)
  private boolean cbui$includePersistentCustomName(boolean vanilla, @Local(argsOnly = true) EntityLiving entity) {
    if (vanilla) return true;
    try { return NametagHooks.enabledSettings() != null && entity.getAlwaysRenderNameTag(); }
    catch (Throwable error) { Cbui.reportOnce("nametag custom-name hook", error); return false; }
  }
}

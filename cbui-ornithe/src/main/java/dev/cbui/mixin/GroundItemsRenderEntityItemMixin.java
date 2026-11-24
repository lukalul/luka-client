package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.cbui.Cbui;
import dev.cbui.feature.grounditems.GroundItemsModule;
import net.minecraft.client.renderer.entity.RenderEntityItem;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Dropped Item Tweaks on 1.8.9. m_34765557 places one item entity (bob, spin, stack spread) and returns how many copies
 * to draw; m_03196414 turns a stack size into vanilla's copy count.
 */
@Mixin(RenderEntityItem.class)
public abstract class GroundItemsRenderEntityItemMixin {
  @Inject(method = "doRender(Lnet/minecraft/entity/item/EntityItem;DDDFF)V", at = @At("HEAD"), cancellable = true,
      require = 0)
  private void cbui$itemShown(EntityItem item, double x, double y, double z, float yaw, float partialTicks,
      CallbackInfo ci) {
    try {
      GroundItemsModule module = GroundItemsModule.active();
      if (module != null && module.hidden(item.getEntityItem())) ci.cancel();
    } catch (Throwable error) {
      Cbui.reportOnce("Dropped Item Tweaks", error);
    }
  }

  @ModifyExpressionValue(method = "m_34765557",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/util/MathHelper;sin(F)F", ordinal = 0),
      require = 0)
  private float cbui$bob(float wave) {
    try {
      GroundItemsModule module = GroundItemsModule.active();
      return module == null ? wave : module.bobWave(wave);
    } catch (Throwable error) {
      Cbui.reportOnce("Dropped Item Tweaks", error);
      return wave;
    }
  }

  @Inject(method = "m_34765557",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;translate(FFF)V",
          ordinal = 0, shift = At.Shift.AFTER),
      require = 0)
  private void cbui$size(EntityItem item, double x, double y, double z, float partialTicks, IBakedModel model,
      CallbackInfoReturnable<Integer> cir) {
    try {
      GroundItemsModule module = GroundItemsModule.active();
      if (module != null) module.resize(item.getEntityItem(), model);
    } catch (Throwable error) {
      Cbui.reportOnce("Dropped Item Tweaks", error);
    }
  }

  @Inject(method = "m_03196414", at = @At("RETURN"), cancellable = true, require = 0)
  private void cbui$copies(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
    try {
      GroundItemsModule module = GroundItemsModule.active();
      if (module != null) cir.setReturnValue(module.copies(stack, cir.getReturnValue()));
    } catch (Throwable error) {
      Cbui.reportOnce("Dropped Item Tweaks", error);
    }
  }
}

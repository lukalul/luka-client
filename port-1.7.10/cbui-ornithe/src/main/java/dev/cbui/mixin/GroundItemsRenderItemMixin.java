package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.cbui.Cbui;
import dev.cbui.feature.grounditems.GroundItemsModule;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.entity.item.EntityItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dropped Item Tweaks on 1.7.10. Everything is inline in RenderItem.doRender: the bob is its only sine, the copy count
 * is its first int local (picked before the first glTranslatef, which positions the item). Item frames draw their
 * item through the same method with {@code renderInFrame} set; those are left alone.
 */
@Mixin(RenderItem.class)
public abstract class GroundItemsRenderItemMixin {
  private static GroundItemsModule cbui$forGround() {
    if (RenderItem.renderInFrame) return null;
    return GroundItemsModule.active();
  }

  @Inject(method = "doRender(Lnet/minecraft/entity/item/EntityItem;DDDFF)V", at = @At("HEAD"), cancellable = true,
      require = 0)
  private void cbui$itemShown(EntityItem item, double x, double y, double z, float yaw, float partialTicks,
      CallbackInfo ci) {
    try {
      GroundItemsModule module = cbui$forGround();
      if (module != null && item.getEntityItem() != null && module.hidden(item.getEntityItem())) ci.cancel();
    } catch (Throwable error) {
      Cbui.reportOnce("Dropped Item Tweaks", error);
    }
  }

  @ModifyExpressionValue(method = "doRender(Lnet/minecraft/entity/item/EntityItem;DDDFF)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/util/MathHelper;sin(F)F", ordinal = 0),
      require = 0)
  private float cbui$bob(float wave) {
    try {
      GroundItemsModule module = cbui$forGround();
      return module == null ? wave : module.bobWave(wave);
    } catch (Throwable error) {
      Cbui.reportOnce("Dropped Item Tweaks", error);
      return wave;
    }
  }

  @ModifyVariable(method = "doRender(Lnet/minecraft/entity/item/EntityItem;DDDFF)V",
      at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glTranslatef(FFF)V", ordinal = 0),
      ordinal = 0, require = 0)
  private int cbui$copies(int vanillaCopies, EntityItem item) {
    try {
      GroundItemsModule module = cbui$forGround();
      return module == null ? vanillaCopies : module.copies(item.getEntityItem(), vanillaCopies);
    } catch (Throwable error) {
      Cbui.reportOnce("Dropped Item Tweaks", error);
      return vanillaCopies;
    }
  }

  @Inject(method = "doRender(Lnet/minecraft/entity/item/EntityItem;DDDFF)V",
      at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glTranslatef(FFF)V", ordinal = 0,
          shift = At.Shift.AFTER),
      require = 0)
  private void cbui$size(EntityItem item, double x, double y, double z, float yaw, float partialTicks,
      CallbackInfo ci) {
    try {
      GroundItemsModule module = cbui$forGround();
      if (module != null) module.resize(item.getEntityItem());
    } catch (Throwable error) {
      Cbui.reportOnce("Dropped Item Tweaks", error);
    }
  }
}

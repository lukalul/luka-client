package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.cbui.feature.droppeditemtweaks.DroppedItemTweaksModule;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.renderer.entity.RenderEntityItem;

/**
 * Hooks into the item-on-ground renderer. Targets are Ornithe intermediary names: m_34765557 positions and animates one item,
 * m_03196414 returns how many copies of a stack get drawn.
 */
@Mixin(RenderEntityItem.class)
public abstract class DroppedItemTweaksRenderEntityItemMixin {
  @ModifyExpressionValue(method = "m_34765557", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/MathHelper;sin(F)F"), require = 0)
  private float cbui$holdStill(float sine) {
    DroppedItemTweaksModule module = DroppedItemTweaksModule.active();
    return module == null ? sine : module.bob(sine);
  }

  @ModifyReturnValue(method = "m_03196414", at = @At("RETURN"), require = 0)
  private int cbui$capCopies(int vanillaCount, ItemStack stack) {
    DroppedItemTweaksModule module = DroppedItemTweaksModule.active();
    if (module == null) return vanillaCount;
    try {
      return module.stackAmount(vanillaCount, stack);
    } catch (Throwable failure) {
      return vanillaCount;
    }
  }

  @Inject(method = "m_34765557", at = @At("TAIL"), require = 0)
  private void cbui$resize(EntityItem entity, double x, double y, double z, float partialTicks, IBakedModel model,
      CallbackInfoReturnable<Integer> cir) {
    DroppedItemTweaksModule module = DroppedItemTweaksModule.active();
    if (module == null) return;
    try {
      module.applyGroundScale(entity.getEntityItem(), model);
    } catch (Throwable failure) {
      // vanilla size
    }
  }
}

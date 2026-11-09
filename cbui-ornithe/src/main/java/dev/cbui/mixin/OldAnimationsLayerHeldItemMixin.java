package dev.cbui.mixin;

import dev.cbui.feature.oldanimations.OldAnimationsHooks;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.entity.layers.LayerHeldItem;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Old Animations: items held by other players and mobs use their 1.7 placement. */
@Mixin(LayerHeldItem.class)
public abstract class OldAnimationsLayerHeldItemMixin {
  @ModifyArg(method = "doRenderLayer",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemRenderer;renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/renderer/block/model/ItemCameraTransforms$TransformType;)V"),
      index = 2, require = 0)
  private ItemCameraTransforms.TransformType cbui$oldThirdPerson(EntityLivingBase entity, ItemStack stack, ItemCameraTransforms.TransformType type) {
    try { return OldAnimationsHooks.applyThirdPerson(entity, stack) ? ItemCameraTransforms.TransformType.NONE : type; } catch (Throwable ignored) { return type; }
  }
}

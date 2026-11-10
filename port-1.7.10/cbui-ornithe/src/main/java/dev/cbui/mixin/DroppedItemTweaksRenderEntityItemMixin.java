package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.cbui.feature.droppeditemtweaks.DroppedItemTweaksModule;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.entity.item.EntityItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hooks into RenderItem's dropped item path: bobbing height, how many copies of a stack get drawn, and the item's size. */
@Mixin(RenderItem.class)
public abstract class DroppedItemTweaksRenderEntityItemMixin {
  private static final String DRAW = "doRender(Lnet/minecraft/entity/item/EntityItem;DDDFF)V";

  @ModifyExpressionValue(method = DRAW, at = @At(value = "INVOKE", target = "Lnet/minecraft/util/MathHelper;sin(F)F"), require = 0)
  private float cbui$holdStill(float sine) {
    DroppedItemTweaksModule module = DroppedItemTweaksModule.active();
    return module == null ? sine : module.bob(sine);
  }

  private EntityItem cbui$drawn;

  @Inject(method = DRAW, at = @At("HEAD"), require = 0)
  private void cbui$remember(EntityItem entity, double x, double y, double z, float yaw, float partialTicks, CallbackInfo ci) {
    cbui$drawn = entity;
  }

  /** The copies counter n is the first int local doRender stores; its last store follows the stack size checks. */
  @ModifyVariable(method = DRAW, at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glTranslatef(FFF)V", ordinal = 0, remap = false), ordinal = 0, require = 0)
  private int cbui$capCopies(int copies) {
    DroppedItemTweaksModule module = DroppedItemTweaksModule.active();
    if (module == null || cbui$drawn == null) return copies;
    try {
      return module.stackAmount(copies, cbui$drawn.getEntityItem());
    } catch (Throwable failure) {
      return copies;
    }
  }

  /** Runs right after the item is moved to its position, so the scale applies around it. */
  @Inject(method = DRAW, at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glTranslatef(FFF)V", ordinal = 0, shift = At.Shift.AFTER, remap = false), require = 0)
  private void cbui$resize(EntityItem entity, double x, double y, double z, float yaw, float partialTicks, CallbackInfo ci) {
    DroppedItemTweaksModule module = DroppedItemTweaksModule.active();
    if (module == null) return;
    try {
      module.applyGroundScale(entity.getEntityItem());
    } catch (Throwable failure) {
      // vanilla size
    }
  }
}

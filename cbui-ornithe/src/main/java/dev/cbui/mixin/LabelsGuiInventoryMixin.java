package dev.cbui.mixin;

import dev.cbui.feature.labels.LabelsModule;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Nametags: marks the time the inventory and creative screens draw the player model, for "On inventory model". */
@Mixin(GuiInventory.class)
public abstract class LabelsGuiInventoryMixin {
  @Inject(method = "drawEntityOnScreen", at = @At("HEAD"), require = 0)
  private static void cbui$labelsModelStart(int x, int y, int scale, float mouseX, float mouseY, EntityLivingBase entity, CallbackInfo ci) {
    LabelsModule labels = LabelsModule.instance();
    if (labels != null) labels.modelStart();
  }

  @Inject(method = "drawEntityOnScreen", at = @At("RETURN"), require = 0)
  private static void cbui$labelsModelEnd(int x, int y, int scale, float mouseX, float mouseY, EntityLivingBase entity, CallbackInfo ci) {
    LabelsModule labels = LabelsModule.instance();
    if (labels != null) labels.modelEnd();
  }
}

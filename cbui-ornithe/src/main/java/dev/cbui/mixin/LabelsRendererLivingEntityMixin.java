package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.cbui.Cbui;
import dev.cbui.feature.labels.LabelsModule;
import dev.cbui.social.RankIcon;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Nametags: your own label in third person and on the inventory model, F1 left to the card's per-group toggles, and
 * sneaking labels sent through the restyled routine. Priority above the default so Envoy's own cancel at the start
 * of the name method runs first.
 */
@Mixin(value = RendererLivingEntity.class, priority = 1100)
public abstract class LabelsRendererLivingEntityMixin {
  @Shadow
  protected abstract boolean canRenderName(EntityLivingBase entity);

  @Inject(method = "canRenderName(Lnet/minecraft/entity/EntityLivingBase;)Z", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$labelsOwnName(EntityLivingBase entity, CallbackInfoReturnable<Boolean> cir) {
    LabelsModule labels = LabelsModule.active();
    if (labels == null) return;
    try {
      int answer = labels.ownLabel(entity);
      if (answer >= 0) cir.setReturnValue(answer == 1);
    } catch (Throwable error) {
      Cbui.reportOnce("nametag own name", error);
    }
  }

  @ModifyExpressionValue(method = "canRenderName(Lnet/minecraft/entity/EntityLivingBase;)Z",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;isGuiEnabled()Z"), require = 0)
  private boolean cbui$labelsHudHidden(boolean vanilla) {
    // the card decides per group (players, armor stands, others) when the label is drawn
    return vanilla || LabelsModule.active() != null;
  }

  @Inject(method = "renderName(Lnet/minecraft/entity/EntityLivingBase;DDD)V", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$labelsSneaking(EntityLivingBase entity, double x, double y, double z, CallbackInfo ci) {
    LabelsModule labels = LabelsModule.active();
    if (labels == null) return;
    try {
      if (!labels.takesSneakLabel(entity)) return;
      ci.cancel();
      if (canRenderName(entity)) labels.drawSneakLabel((Render<?>) (Object) this, entity, x, y, z);
    } catch (Throwable error) {
      Cbui.reportOnce("nametag sneaking", error);
    }
  }

  @Inject(method = "renderName(Lnet/minecraft/entity/EntityLivingBase;DDD)V", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/client/gui/FontRenderer;drawString(Ljava/lang/String;III)I", ordinal = 0, shift = At.Shift.AFTER), require = 0)
  private void cbui$labelsSneakIcon(EntityLivingBase entity, double x, double y, double z, CallbackInfo ci) {
    try {
      FontRenderer font = ((Render<?>) (Object) this).getFontRendererFromRenderManager();
      if (font == null) return;
      String name = entity.getDisplayName().getFormattedText();
      RankIcon.draw(entity, -font.getStringWidth(name) / 2, 0, false);
    } catch (Throwable error) {
      Cbui.reportOnce("nametag rank icon", error);
    }
  }
}

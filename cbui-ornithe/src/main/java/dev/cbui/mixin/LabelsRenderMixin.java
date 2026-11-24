package dev.cbui.mixin;

import dev.cbui.Cbui;
import dev.cbui.feature.labels.LabelsModule;
import dev.cbui.social.RankIcon;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Nametags: while the card is on, vanilla's label routine is replaced by the card's own drawing. While it is off (and
 * for primed TNT), vanilla draws and the luka rank icon is still added after each of its two text passes.
 */
@Mixin(Render.class)
public abstract class LabelsRenderMixin {
  @Inject(method = "renderLivingLabel", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$labelsRestyle(Entity owner, String text, double x, double y, double z, int maxDistance, CallbackInfo ci) {
    LabelsModule labels = LabelsModule.active();
    if (labels == null) return;
    try {
      if (labels.paint((Render<?>) (Object) this, owner, text, x, y, z, maxDistance)) ci.cancel();
    } catch (Throwable error) {
      Cbui.reportOnce("nametag hook", error);
    }
  }

  @Inject(method = "renderLivingLabel", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/client/gui/FontRenderer;drawString(Ljava/lang/String;III)I", ordinal = 0, shift = At.Shift.AFTER), require = 0)
  private void cbui$labelsIconFaint(Entity owner, String text, double x, double y, double z, int maxDistance, CallbackInfo ci) {
    cbui$labelsIcon(owner, text, false);
  }

  @Inject(method = "renderLivingLabel", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/client/gui/FontRenderer;drawString(Ljava/lang/String;III)I", ordinal = 1, shift = At.Shift.AFTER), require = 0)
  private void cbui$labelsIconSolid(Entity owner, String text, double x, double y, double z, int maxDistance, CallbackInfo ci) {
    cbui$labelsIcon(owner, text, true);
  }

  private void cbui$labelsIcon(Entity owner, String text, boolean solid) {
    try {
      FontRenderer font = ((Render<?>) (Object) this).getFontRendererFromRenderManager();
      if (font == null || text == null) return;
      RankIcon.draw(owner, -font.getStringWidth(text) / 2, "deadmau5".equals(text) ? -10 : 0, solid);
    } catch (Throwable error) {
      Cbui.reportOnce("nametag rank icon", error);
    }
  }
}

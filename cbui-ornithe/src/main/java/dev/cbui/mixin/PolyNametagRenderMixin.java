package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cbui.Cbui;
import dev.cbui.feature.polynametag.NametagHooks;
import dev.cbui.feature.polynametag.PolyNametagModule;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Render.class)
public abstract class PolyNametagRenderMixin {
  @Shadow public abstract FontRenderer getFontRendererFromRenderManager();
  @Unique private Entity cbui$labelOwner;
  @Unique private String cbui$labelText;
  @Unique private final float[] cbui$backgroundVertices = new float[256];

  @Inject(method = "renderLivingLabel", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$checkLabel(Entity entity, String text, double x, double y, double z, int maxDistance, CallbackInfo callback) {
    cbui$labelOwner = entity;
    cbui$labelText = text;
    try { if (!NametagHooks.permits(entity)) callback.cancel(); }
    catch (Throwable error) { Cbui.reportOnce("nametag remove hook", error); }
  }

  @WrapOperation(method = "renderLivingLabel",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getDistanceSqToEntity(Lnet/minecraft/entity/Entity;)D"), require = 0)
  private double cbui$inventoryDistance(Entity entity, Entity viewer, Operation<Double> vanillaCall) {
    try { if (NametagHooks.hiddenInInventory(entity)) return Double.POSITIVE_INFINITY; }
    catch (Throwable error) { Cbui.reportOnce("nametag inventory hook", error); }
    return vanillaCall.call(entity, viewer);
  }

  @WrapOperation(method = "renderLivingLabel",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;scale(FFF)V"), require = 0)
  private void cbui$resizeLabel(float x, float y, float z, Operation<Void> vanillaCall) {
    try { vanillaCall.call(NametagHooks.scaled(x), NametagHooks.scaled(y), NametagHooks.scaled(z)); }
    catch (Throwable error) { Cbui.reportOnce("nametag scale hook", error); vanillaCall.call(x, y, z); }
  }

  @Inject(method = "renderLivingLabel",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;disableLighting()V", shift = At.Shift.AFTER), require = 0)
  private void cbui$positionSneakingLabel(Entity entity, String text, double x, double y, double z, int maxDistance, CallbackInfo callback) {
    try { if (cbui$usesSneakStyle(entity)) GlStateManager.translate(0f, NametagHooks.SNEAK_OFFSET, 0f); }
    catch (Throwable error) { Cbui.reportOnce("nametag sneak hook", error); }
  }

  @WrapOperation(method = "renderLivingLabel",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;disableDepth()V"), require = 0)
  private void cbui$preserveDepthForSneaking(Operation<Void> vanillaCall) {
    try { if (cbui$usesSneakStyle(cbui$labelOwner)) return; }
    catch (Throwable error) { Cbui.reportOnce("nametag depth hook", error); }
    vanillaCall.call();
  }

  @WrapOperation(method = "renderLivingLabel",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;draw()V"), require = 0)
  private void cbui$drawConfiguredBackground(Tessellator tessellator, Operation<Void> vanillaCall) {
    try {
      PolyNametagModule settings = NametagHooks.enabledSettings();
      if (settings != null && cbui$labelText != null) {
        WorldRenderer renderer = tessellator.getWorldRenderer();
        renderer.finishDrawing();
        renderer.begin(7, DefaultVertexFormats.POSITION_COLOR);
        int color = NametagHooks.recolorBackground(0x40000000);
        if ((color >>> 24) != 0) appendBackground(renderer, settings, color);
      }
    } catch (Throwable error) { Cbui.reportOnce("nametag background hook", error); }
    vanillaCall.call(tessellator);
  }

  @Unique private void appendBackground(WorldRenderer renderer, PolyNametagModule settings, int color) {
    int halfWidth = getFontRendererFromRenderManager().getStringWidth(cbui$labelText) / 2;
    int specialOffset = "deadmau5".equals(cbui$labelText) ? -10 : 0;
    int used = NametagHooks.writeBackground(cbui$backgroundVertices, settings, -halfWidth - 1,
        NametagHooks.shifted(specialOffset - 1), halfWidth + 1, NametagHooks.shifted(specialOffset + 8));
    int red = color >>> 16 & 255, green = color >>> 8 & 255, blue = color & 255, alpha = color >>> 24;
    for (int index = 0; index < used; index += 2)
      renderer.pos(cbui$backgroundVertices[index], cbui$backgroundVertices[index + 1], NametagHooks.BACKGROUND_DEPTH)
          .color(red, green, blue, alpha).endVertex();
  }

  @WrapOperation(method = "renderLivingLabel",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/FontRenderer;drawString(Ljava/lang/String;III)I", ordinal = 0), require = 0)
  private int cbui$drawTranslucentText(FontRenderer font, String text, int x, int y, int color, Operation<Integer> vanillaCall) {
    int drawn;
    try {
      if (NametagHooks.enabledSettings() != null) {
        float shiftedY = NametagHooks.shifted(y);
        drawn = font.drawString(NametagHooks.restyle(text), (float) x, shiftedY, NametagHooks.recolorText(color), NametagHooks.useShadow());
        dev.cbui.social.RankIcon.draw(cbui$labelOwner, x, shiftedY, false);
        return drawn;
      }
    } catch (Throwable error) { Cbui.reportOnce("nametag text hook", error); }
    drawn = vanillaCall.call(font, text, x, y, color);
    dev.cbui.social.RankIcon.draw(cbui$labelOwner, x, y, false);
    return drawn;
  }

  @WrapOperation(method = "renderLivingLabel",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/FontRenderer;drawString(Ljava/lang/String;III)I", ordinal = 1), require = 0)
  private int cbui$drawSolidText(FontRenderer font, String text, int x, int y, int color, Operation<Integer> vanillaCall) {
    try {
      if (NametagHooks.enabledSettings() != null) {
        if (cbui$usesSneakStyle(cbui$labelOwner)) return 0;
        float shiftedY = NametagHooks.shifted(y);
        int width = cbui$renderSolid(font, NametagHooks.restyle(text), x, shiftedY, NametagHooks.recolorText(color));
        dev.cbui.social.RankIcon.draw(cbui$labelOwner, x, shiftedY, true);
        return width;
      }
    } catch (Throwable error) {
      Cbui.reportOnce("nametag opaque text hook", error);
      GlStateManager.depthMask(true);
      GlStateManager.colorMask(true, true, true, true);
    }
    int drawn = vanillaCall.call(font, text, x, y, color);
    dev.cbui.social.RankIcon.draw(cbui$labelOwner, x, y, true);
    return drawn;
  }

  @Unique private static int cbui$renderSolid(FontRenderer font, String text, float x, float y, int color) {
    if (!NametagHooks.useShadow()) return font.drawString(text, x, y, color, false);
    GlStateManager.depthMask(false);
    int width = font.drawString(text, x, y, color, true);
    GlStateManager.depthMask(true);
    GlStateManager.colorMask(false, false, false, false);
    font.drawString(text, x, y, color, false);
    GlStateManager.colorMask(true, true, true, true);
    return width;
  }

  @Unique private static boolean cbui$usesSneakStyle(Entity entity) {
    return entity != null && entity.isSneaking() && NametagHooks.enabledSettings() != null;
  }
}

package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cbui.Cbui;
import dev.cbui.feature.polynametag.NametagHooks;
import dev.cbui.feature.polynametag.PolyNametagModule;
import dev.luka.compat.Compat;
import dev.luka.compat.GlStateManager;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** renderLivingLabel's GL calls in order: disableLighting is glDisable ordinal 0 and disableDepth glDisable ordinal 1. */
@Mixin(Render.class)
public abstract class PolyNametagRenderMixin {
  private static final String GL_DISABLE = "Lorg/lwjgl/opengl/GL11;glDisable(I)V";
  private static final String FONT_DRAW = "Lnet/minecraft/client/gui/FontRenderer;drawString(Ljava/lang/String;III)I";

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
      at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glScalef(FFF)V", remap = false), require = 0)
  private void cbui$resizeLabel(float x, float y, float z, Operation<Void> vanillaCall) {
    try { vanillaCall.call(NametagHooks.scaled(x), NametagHooks.scaled(y), NametagHooks.scaled(z)); }
    catch (Throwable error) { Cbui.reportOnce("nametag scale hook", error); vanillaCall.call(x, y, z); }
  }

  @Inject(method = "renderLivingLabel",
      at = @At(value = "INVOKE", target = GL_DISABLE, ordinal = 0, shift = At.Shift.AFTER, remap = false), require = 0)
  private void cbui$positionSneakingLabel(Entity entity, String text, double x, double y, double z, int maxDistance, CallbackInfo callback) {
    try { if (cbui$usesSneakStyle(entity)) GlStateManager.translate(0f, NametagHooks.SNEAK_OFFSET, 0f); }
    catch (Throwable error) { Cbui.reportOnce("nametag sneak hook", error); }
  }

  @WrapOperation(method = "renderLivingLabel",
      at = @At(value = "INVOKE", target = GL_DISABLE, ordinal = 1, remap = false), require = 0)
  private void cbui$preserveDepthForSneaking(int capability, Operation<Void> vanillaCall) {
    // sneaking labels, and every label with "Hide behind walls": keep the depth test, so walls hide them
    try { if (cbui$usesSneakStyle(cbui$labelOwner) || NametagHooks.hiddenBehindWalls()) return; }
    catch (Throwable error) { Cbui.reportOnce("nametag depth hook", error); }
    vanillaCall.call(capability);
  }

  @WrapOperation(method = "renderLivingLabel",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;draw()I"), require = 0)
  private int cbui$drawConfiguredBackground(Tessellator tessellator, Operation<Integer> vanillaCall) {
    try {
      PolyNametagModule settings = NametagHooks.enabledSettings();
      if (settings != null && cbui$labelText != null) {
        Compat.discardTessellator();
        tessellator.startDrawing(7);
        int color = NametagHooks.recolorBackground(0x40000000);
        if ((color >>> 24) != 0) appendBackground(tessellator, settings, color);
      }
    } catch (Throwable error) { Cbui.reportOnce("nametag background hook", error); }
    return vanillaCall.call(tessellator);
  }

  @Unique private void appendBackground(Tessellator tessellator, PolyNametagModule settings, int color) {
    int halfWidth = getFontRendererFromRenderManager().getStringWidth(cbui$labelText) / 2;
    int specialOffset = "deadmau5".equals(cbui$labelText) ? -10 : 0;
    int used = NametagHooks.writeBackground(cbui$backgroundVertices, settings, -halfWidth - 1,
        NametagHooks.shifted(specialOffset - 1), halfWidth + 1, NametagHooks.shifted(specialOffset + 8));
    tessellator.setColorRGBA(color >>> 16 & 255, color >>> 8 & 255, color & 255, color >>> 24);
    for (int index = 0; index < used; index += 2)
      tessellator.addVertex(cbui$backgroundVertices[index], cbui$backgroundVertices[index + 1], NametagHooks.BACKGROUND_DEPTH);
  }

  @WrapOperation(method = "renderLivingLabel", at = @At(value = "INVOKE", target = FONT_DRAW, ordinal = 0), require = 0)
  private int cbui$drawTranslucentText(FontRenderer font, String text, int x, int y, int color, Operation<Integer> vanillaCall) {
    int drawn;
    try {
      if (NametagHooks.enabledSettings() != null) {
        float shiftedY = NametagHooks.shifted(y);
        drawn = Compat.drawString(font, NametagHooks.restyle(text), (float) x, shiftedY, NametagHooks.recolorText(color), NametagHooks.useShadow());
        dev.cbui.social.RankIcon.draw(cbui$labelOwner, x, shiftedY, false);
        return drawn;
      }
    } catch (Throwable error) { Cbui.reportOnce("nametag text hook", error); }
    drawn = vanillaCall.call(font, text, x, y, color);
    dev.cbui.social.RankIcon.draw(cbui$labelOwner, x, y, false);
    return drawn;
  }

  @WrapOperation(method = "renderLivingLabel", at = @At(value = "INVOKE", target = FONT_DRAW, ordinal = 1), require = 0)
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
    if (!NametagHooks.useShadow()) return Compat.drawString(font, text, x, y, color, false);
    GlStateManager.depthMask(false);
    int width = Compat.drawString(font, text, x, y, color, true);
    GlStateManager.depthMask(true);
    GlStateManager.colorMask(false, false, false, false);
    Compat.drawString(font, text, x, y, color, false);
    GlStateManager.colorMask(true, true, true, true);
    return width;
  }

  @Unique private static boolean cbui$usesSneakStyle(Entity entity) {
    return entity != null && entity.isSneaking() && NametagHooks.enabledSettings() != null;
  }
}

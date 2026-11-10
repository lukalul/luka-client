package dev.cbui.feature.polycrosshair;

import dev.cbui.Cbui;
import dev.cbui.feature.Features;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import dev.luka.compat.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityAmbientCreature;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityWaterMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.Team;

public final class CrosshairRenderer {
  private static PolyCrosshairModule feature;
  private static boolean disabledAfterFailure;
  private CrosshairRenderer() {}

  public static boolean render(boolean vanillaVisible) {
    if (disabledAfterFailure) return false;
    if (feature == null) feature = Features.get(PolyCrosshairModule.class);
    if (feature == null || !feature.enabled()) return false;
    try {
      Minecraft client = Minecraft.getMinecraft();
      if (client.thePlayer == null) return false;
      PolyCrosshairModule.Profile selected = feature.profile(classify(client));
      if (selected != null) renderProfile(client, selected, vanillaVisible);
      else if (feature.vanillaOn.get()) renderVanilla(client, vanillaVisible);
      else return false;
      restore(client);
      return true;
    } catch (Throwable problem) {
      disabledAfterFailure = true;
      Cbui.reportOnce("crosshair renderer", problem);
      GlStateManager.color(1f, 1f, 1f, 1f);
      return false;
    }
  }

  private static int classify(Minecraft client) {
    Entity entity = client.objectMouseOver == null ? null : client.objectMouseOver.entityHit;
    EntityPlayer player = client.thePlayer;
    if (entity == null || entity == player || entity.isInvisible()) return 0;
    Team own = player.getTeam();
    Team other = entity instanceof EntityLivingBase ? ((EntityLivingBase) entity).getTeam() : null;
    if (own != null && other != null) return own.isSameTeam(other) ? 1 : 2;
    if (entity instanceof EntityAnimal || entity instanceof EntityAmbientCreature || entity instanceof EntityWaterMob) return 1;
    return entity instanceof IMob || entity instanceof EntityPlayer ? 2 : 0;
  }

  private static boolean allowed(Minecraft client, boolean thirdPerson, boolean spectator, boolean vanillaVisible) {
    boolean first = client.gameSettings.thirdPersonView == 0;
    if (!first && !thirdPerson) return false;
    if (vanillaVisible) return true;
    if (client.gameSettings.showDebugInfo) return false;
    return !first;
  }

  private static void renderProfile(Minecraft client, PolyCrosshairModule.Profile profile, boolean vanillaVisible) {
    if (!allowed(client, profile.third.get(), profile.spectator.get(), vanillaVisible)) return;
    ScaledResolution screen = dev.luka.compat.Compat.scaled(client);
    int gui = screen.getScaleFactor();
    float requested = profile.scaleOn.get() ? profile.scale.get() / gui : 1f;
    int density = Math.max(1, Math.min(16, (int) Math.ceil(gui * requested)));
    CrosshairTextures.Entry textures = CrosshairTextures.get(profile, density);
    if (textures == null) return;
    boolean smooth = profile.smooth.get();
    float matrixScale = smooth ? requested : 1f;
    int extent = smooth ? textures.cells : Math.max(1, Math.round(textures.cells * requested));
    float left = smooth ? (float) Math.floor(screen.getScaledWidth() / requested / 2f) * requested - extent * requested / 2f
        : screen.getScaledWidth() / 2 - extent / 2;
    float top = smooth ? (float) Math.floor(screen.getScaledHeight() / requested / 2f) * requested - extent * requested / 2f
        : screen.getScaledHeight() / 2 - extent / 2;
    int separation = bowSeparation(client, profile, requested / matrixScale);
    GlStateManager.enableBlend(); GlStateManager.enableAlpha(); GlStateManager.pushMatrix();
    try {
      GlStateManager.translate(left, top, 0f); GlStateManager.scale(matrixScale, matrixScale, 1f);
      float center = extent / 2f;
      GlStateManager.translate(center, center, 0f); GlStateManager.rotate(profile.rotation.get(), 0f, 0f, 1f); GlStateManager.translate(-center, -center, 0f);
      if (textures.outline != null) layer(client, textures.outline, extent, textures.cells, separation, profile.outlineColor.get(), false);
      boolean invert = profile.blendOn.get();
      layer(client, textures.art, extent, textures.cells, separation, invert ? profile.blendColor.get() : -1, invert);
      if (profile.healthOn.get() && client.thePlayer.getHealth() <= profile.healthThreshold.get())
        layer(client, textures.silhouette, extent, textures.cells, separation, profile.healthColor.get(), false);
    } finally { GlStateManager.popMatrix(); }
  }

  private static int bowSeparation(Minecraft client, PolyCrosshairModule.Profile profile, float scale) {
    ItemStack item = client.thePlayer.getItemInUse();
    if (!profile.bowOn.get() || item == null || !(item.getItem() instanceof ItemBow)) return 0;
    float progress = Math.min(1f, Math.max(0f, client.thePlayer.getItemInUseDuration() / 20f));
    return Math.round((1f - progress) * profile.bowScale.get() * scale);
  }

  private static void layer(Minecraft client, CrosshairTextures.Layer texture, int extent, int cells, int spread, int color, boolean invert) {
    if (spread == 0) { quad(client, texture, 0, 0, extent, extent, 0, 0, texture.size, texture.size, color, invert); return; }
    int half = (cells + 1) / 2, unit = texture.size / cells;
    float drawnUnit = extent / (float) cells;
    int piece = Math.round(half * drawnUnit);
    for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) {
      int sx = x * (cells - half), sy = y * (cells - half);
      quad(client, texture, Math.round(sx * drawnUnit) + (x == 0 ? -spread : spread), Math.round(sy * drawnUnit) + (y == 0 ? -spread : spread),
          piece, piece, sx * unit, sy * unit, half * unit, half * unit, color, invert);
    }
  }

  private static void quad(Minecraft client, CrosshairTextures.Layer texture, int x, int y, int w, int h, int u, int v, int uw, int vh, int color, boolean invert) {
    client.getTextureManager().bindTexture(texture.location); blend(invert); tint(color);
    Gui.drawScaledCustomSizeModalRect(x, y, u, v, uw, vh, w, h, texture.size, texture.size);
  }

  private static void renderVanilla(Minecraft client, boolean vanillaVisible) {
    if (!allowed(client, feature.vanillaThird.get(), feature.vanillaSpectator.get(), vanillaVisible)) return;
    ScaledResolution screen = dev.luka.compat.Compat.scaled(client);
    double gui = screen.getScaleFactor(), texel = feature.vanillaScaleOn.get() ? feature.vanillaScale.get() : gui;
    float scale = (float) (texel / gui), left, top;
    if (feature.vanillaCentered.get()) {
      left = (float) (Math.floor((client.displayWidth - 15 * texel) / 2) / gui);
      top = (float) (Math.floor((client.displayHeight - 15 * texel) / 2) / gui);
    } else {
      float correction = 7.5f * (1f - scale);
      left = screen.getScaledWidth() / 2 - 7 + correction; top = screen.getScaledHeight() / 2 - 7 + correction;
    }
    boolean invert = feature.vanillaBlend.get();
    GlStateManager.enableBlend(); GlStateManager.enableAlpha(); GlStateManager.pushMatrix();
    try {
      GlStateManager.translate(left, top, 0f); GlStateManager.scale(scale, scale, 1f); client.getTextureManager().bindTexture(Gui.icons);
      blend(invert); tint(invert ? feature.vanillaBlendColor.get() : feature.vanillaColor.get());
      Gui.drawScaledCustomSizeModalRect(0, 0, 0, 0, 16, 16, 16, 16, 256, 256);
    } finally { GlStateManager.popMatrix(); }
  }

  private static void blend(boolean invert) { GlStateManager.tryBlendFuncSeparate(invert ? 775 : 770, invert ? 769 : 771, 1, 0); }
  private static void tint(int color) { GlStateManager.color((color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f, (color & 255) / 255f, (color >>> 24) / 255f); }
  private static void restore(Minecraft client) { GlStateManager.color(1f, 1f, 1f, 1f); GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0); client.getTextureManager().bindTexture(Gui.icons); }
}

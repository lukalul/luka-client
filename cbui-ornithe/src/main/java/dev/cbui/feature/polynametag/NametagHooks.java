package dev.cbui.feature.polynametag;

import dev.cbui.feature.Features;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumChatFormatting;

public final class NametagHooks {
  public static final float BACKGROUND_DEPTH = .01f;
  public static final float SNEAK_OFFSET = 9.374999f;
  private static PolyNametagModule remembered;

  private NametagHooks() {}

  public static PolyNametagModule enabledSettings() {
    PolyNametagModule result = remembered;
    if (result == null) { result = Features.get(PolyNametagModule.class); remembered = result; }
    return result != null && result.enabled() ? result : null;
  }

  public static boolean permits(Entity subject) {
    PolyNametagModule settings = enabledSettings();
    if (settings == null) return true;
    if (settings.removeNametags.get() || subject == null) return false;
    Minecraft game = Minecraft.getMinecraft();
    Entity camera = game.getRenderViewEntity();
    if (camera != null) {
      double limit = settings.renderDistance.get();
      if (subject.getDistanceSqToEntity(camera) > limit * limit) return false;
    }
    return Minecraft.isGuiEnabled() || !hiddenWithHud(settings, subject);
  }

  private static boolean hiddenWithHud(PolyNametagModule settings, Entity subject) {
    if (subject instanceof EntityArmorStand) return settings.hideArmorStand.get();
    return subject instanceof EntityPlayer ? settings.hidePlayer.get() : settings.hideEntity.get();
  }

  public static boolean hudAllows(boolean vanillaResult, Entity subject) {
    if (vanillaResult) return true;
    PolyNametagModule settings = enabledSettings();
    return settings != null && subject != null && !hiddenWithHud(settings, subject);
  }

  public static boolean ownLabel(boolean vanillaResult, Entity subject) {
    PolyNametagModule settings = enabledSettings();
    if (settings == null || !settings.showOwn.get() || subject == null
        || subject != Minecraft.getMinecraft().thePlayer) return vanillaResult;
    return !nearbyServerLabel(subject);
  }

  /** The label is depth tested in all its passes, so blocks in front of it hide it. */
  public static boolean hiddenBehindWalls() {
    PolyNametagModule settings = enabledSettings();
    return settings != null && settings.hideBehindWalls.get();
  }

  public static boolean hiddenInInventory(Entity subject) {
    PolyNametagModule settings = enabledSettings();
    if (settings == null || settings.showInInventory.get() || subject == null) return false;
    Minecraft game = Minecraft.getMinecraft();
    return subject == game.thePlayer
        && (game.currentScreen instanceof GuiInventory || game.currentScreen instanceof GuiContainerCreative);
  }

  private static boolean nearbyServerLabel(Entity player) {
    try {
      AxisAlignedBB search = player.getEntityBoundingBox().expand(.75, 1.25, .75).offset(0, .5, 0);
      List<Entity> candidates = player.worldObj.getEntitiesWithinAABBExcludingEntity(player, search);
      String playerName = player.getName();
      for (Entity candidate : candidates) {
        if (!(candidate instanceof EntityArmorStand) || !candidate.getAlwaysRenderNameTag()) continue;
        String plain = EnumChatFormatting.getTextWithoutFormattingCodes(candidate.getCustomNameTag());
        if (plain != null && plain.contains(playerName)) return true;
      }
    } catch (Throwable ignored) { }
    return false;
  }

  public static float scaled(float value) {
    PolyNametagModule settings = enabledSettings();
    return settings == null ? value : value * settings.scale.get();
  }

  public static float shifted(float value) {
    PolyNametagModule settings = enabledSettings();
    return settings == null ? value : value - settings.heightOffset.get();
  }

  public static boolean useShadow() {
    PolyNametagModule settings = enabledSettings();
    return settings != null && "Shadow".equals(settings.textShadow.get());
  }

  public static int recolorText(int vanillaColor) {
    PolyNametagModule settings = enabledSettings();
    if (settings == null) return vanillaColor;
    return mergeAlpha(settings.textCol.get(), vanillaColor, settings.backgroundOn.get() ? 0 : 0x80, 254);
  }

  public static int recolorBackground(int vanillaColor) {
    PolyNametagModule settings = enabledSettings();
    if (settings == null) return vanillaColor;
    if (!settings.backgroundOn.get() || vanillaColor == 0) return 0;
    return mergeAlpha(settings.backgroundCol.get(), vanillaColor, 0, 32);
  }

  private static int mergeAlpha(int configured, int vanilla, int minimum, int limitedThrough) {
    int alpha = vanilla >>> 24;
    if (alpha == 0 || alpha > limitedThrough) return configured;
    int ceiling = Math.max(alpha, minimum);
    return configured & 0xFFFFFF | Math.min(configured >>> 24, ceiling) << 24;
  }

  public static String restyle(String label) {
    PolyNametagModule settings = enabledSettings();
    if (settings == null || !settings.overrideText.get() || label == null) return label;
    StringBuilder kept = new StringBuilder(label.length());
    int index = 0;
    while (index < label.length()) {
      char current = label.charAt(index);
      if ((current == '\u00a7' || current == '\u00c2') && index + 1 < label.length()) {
        int codeIndex = current == '\u00c2' && index + 2 < label.length() && label.charAt(index + 1) == '\u00a7' ? index + 2 : index + 1;
        char code = Character.toLowerCase(label.charAt(codeIndex));
        if (Character.isDigit(code) || code >= 'a' && code <= 'f') { index = codeIndex + 1; continue; }
      }
      kept.append(current);
      index++;
    }
    return kept.toString();
  }

  /** Writes independent triangles (four vertices each for GL_QUADS) into destination. */
  public static int writeBackground(float[] destination, PolyNametagModule settings,
      float left, float top, float right, float bottom) {
    float x0 = left - settings.paddingX.get(), x1 = right + settings.paddingX.get();
    float y0 = top - settings.paddingY.get(), y1 = bottom + settings.paddingY.get();
    float radius = settings.rounded.get()
        ? Math.min(settings.cornerRadius.get(), Math.min((x1 - x0) * .5f, (y1 - y0) * .5f)) : 0f;
    if (radius <= 0f) {
      float[] rectangle = {x0, y1, x1, y1, x1, y0, x0, y0};
      System.arraycopy(rectangle, 0, destination, 0, rectangle.length);
      return rectangle.length;
    }
    float centerX = (x0 + x1) * .5f, centerY = (y0 + y1) * .5f;
    int output = 0;
    final int steps = 8;
    for (int corner = 0; corner < 4; corner++) {
      float cornerX = corner == 0 || corner == 3 ? x0 + radius : x1 - radius;
      float cornerY = corner < 2 ? y0 + radius : y1 - radius;
      double start = Math.PI + corner * Math.PI * .5;
      for (int step = 0; step < steps; step++) {
        double a = start + step * Math.PI / (steps * 2.0);
        double b = start + (step + 1) * Math.PI / (steps * 2.0);
        destination[output++] = centerX; destination[output++] = centerY;
        destination[output++] = cornerX + radius * (float) Math.cos(b); destination[output++] = cornerY + radius * (float) Math.sin(b);
        destination[output++] = cornerX + radius * (float) Math.cos(a); destination[output++] = cornerY + radius * (float) Math.sin(a);
        destination[output++] = cornerX + radius * (float) Math.cos(a); destination[output++] = cornerY + radius * (float) Math.sin(a);
      }
    }
    return output;
  }
}

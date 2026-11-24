package dev.cbui.feature.saturation;

import dev.cbui.Cbui;
import dev.cbui.render.Draw;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.util.FoodStats;
import net.minecraft.util.ResourceLocation;

/**
 * What the Saturation card draws on the hunger bar. The vanilla bar is ten icons right of the hotbar centre, drawn right to
 * left, each worth 2 hunger; saturation and the previews use the same layout (vanilla's own icons for hunger, a gold
 * outline traced from the pack's food icon for saturation).
 *
 * Exhaustion is not sent by servers, so it is estimated here from what the client sees (walking, sprinting, swimming,
 * jumps, damage taken, natural healing) and corrected whenever the server's saturation or hunger drops (each drop
 * means 4 exhaustion were used).
 */
public final class SaturationHud {
  private SaturationHud() {}

  // ---- exhaustion estimate ----

  private static float exhaustion;
  private static double lastX, lastY, lastZ;
  private static boolean lastOnGround, have;
  private static int lastFood, lastHurt;
  private static float lastSaturation, lastHealth;

  static void tick() {
    EntityPlayer p = Minecraft.getMinecraft().thePlayer;
    if (p == null) {
      have = false;
      return;
    }
    FoodStats food = p.getFoodStats();
    int f = food.getFoodLevel();
    float sat = food.getSaturationLevel(), health = p.getHealth();
    if (have && !p.capabilities.disableDamage) {
      double dx = p.posX - lastX, dy = p.posY - lastY, dz = p.posZ - lastZ;
      double flat = Math.sqrt(dx * dx + dz * dz);
      if (flat < 10) { // a teleport is not movement
        if (p.isInWater()) exhaustion += 0.015f * (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        else if (p.onGround) exhaustion += (p.isSprinting() ? 0.1f : 0.01f) * (float) flat;
      }
      if (lastOnGround && !p.onGround && p.motionY > 0 && !p.isInWater()) exhaustion += p.isSprinting() ? 0.8f : 0.2f;
      if (p.hurtTime > lastHurt && p.hurtTime == p.maxHurtTime) exhaustion += 0.3f;
      // natural healing (food at 18 or more, no regeneration effect) costs 3 per health point
      if (health > lastHealth && lastFood >= 18 && !p.isPotionActive(Potion.regeneration))
        exhaustion += 3f * (health - lastHealth);
      // the server spent 4 exhaustion for every point of saturation, or of hunger once saturation is gone
      if (f <= lastFood) {
        if (sat < lastSaturation - 0.01f) exhaustion -= 4f * (lastSaturation - sat);
        else if (f < lastFood && lastSaturation <= 0.01f) exhaustion -= 4f * (lastFood - f);
      }
      exhaustion = Math.max(0f, Math.min(exhaustion, 3.999f));
    }
    lastX = p.posX;
    lastY = p.posY;
    lastZ = p.posZ;
    lastOnGround = p.onGround;
    lastHurt = p.hurtTime;
    lastFood = f;
    lastSaturation = sat;
    lastHealth = health;
    have = true;
  }

  // ---- drawing ----

  private static SaturationModule active() {
    SaturationModule m = SaturationModule.instance;
    Minecraft mc = Minecraft.getMinecraft();
    if (m == null || !m.enabled() || mc.thePlayer == null || mc.playerController == null) return null;
    if (!mc.playerController.shouldDrawHUD()) return null; // creative and spectator have no hunger bar
    if (mc.thePlayer.ridingEntity instanceof net.minecraft.entity.EntityLivingBase) return null; // mount health replaces it
    return m;
  }

  /** Right edge of the hunger bar and its top. */
  private static int right(int screenWidth) { return screenWidth / 2 + 91; }
  private static int top(int screenHeight) { return screenHeight - 39; }

  /** Before the hunger icons are drawn: the exhaustion bar behind them. */
  public static void beforeFood(Gui gui, int screenWidth, int screenHeight) {
    SaturationModule m = active();
    if (m == null || !m.exhaustion.get() || exhaustion <= 0.01f) return;
    try {
      int r = right(screenWidth), t = top(screenHeight);
      float w = 81f * Math.min(1f, exhaustion / 4f);
      Draw.rect(r - w, t, r, t + 9, 0x55FFFFFF);
    } finally {
      restore();
    }
  }

  /** After the hunger icons: saturation outline and the held-food previews. */
  public static void afterFood(Gui gui, int screenWidth, int screenHeight) {
    SaturationModule m = active();
    if (m == null) return;
    Minecraft mc = Minecraft.getMinecraft();
    EntityPlayer p = mc.thePlayer;
    FoodStats stats = p.getFoodStats();
    int food = stats.getFoodLevel();
    float saturation = stats.getSaturationLevel();
    int r = right(screenWidth), t = top(screenHeight);
    // what the held food would add (eating caps hunger at 20 and saturation at the new hunger level)
    ItemStack held = p.getHeldItem();
    int gainFood = 0;
    float gainSat = 0;
    if (held != null && held.getItem() instanceof ItemFood) {
      ItemFood item = (ItemFood) held.getItem();
      gainFood = item.getHealAmount(held);
      gainSat = gainFood * item.getSaturationModifier(held) * 2f;
    }
    int newFood = Math.min(20, food + gainFood);
    float newSat = Math.min(newFood, saturation + gainSat);
    float flash = 0.45f + 0.35f * (float) Math.sin(System.currentTimeMillis() / 160.0);
    try {
      if (m.saturation.get() && saturation >= 0.5f) outline(0f, saturation, r, t, 1f);
      if (gainFood > 0 && m.foodValues.get()) {
        hunger(gui, food, newFood, r, t, flash);
        if (m.saturation.get() && newSat > saturation) outline(saturation, newSat, r, t, flash);
      }
      if (gainFood > 0 && m.healthRestored.get()) health(gui, p, newFood, newSat, screenWidth, screenHeight, flash);
    } catch (Throwable error) {
      Cbui.reportOnce("saturation overlay", error);
    } finally {
      restore();
    }
  }

  /** Vanilla hunger icons (full or half) for the points from {@code from} to {@code to}, at the given alpha. */
  private static void hunger(Gui gui, int from, int to, int r, int t, float alpha) {
    bindIcons();
    GlStateManager.enableBlend();
    GlStateManager.color(1f, 1f, 1f, alpha);
    for (int i = from / 2; i < (to + 1) / 2; i++) {
      int x = r - i * 8 - 9;
      int points = Math.min(2, to - i * 2);
      if (points <= 0) continue;
      gui.drawTexturedModalRect(x, t, points >= 2 ? 52 : 61, 27, 9, 9);
    }
    GlStateManager.color(1f, 1f, 1f, 1f);
  }

  /** The gold outline for saturation from {@code from} to {@code to}, in quarter steps of an icon. */
  private static void outline(float from, float to, int r, int t, float alpha) {
    ResourceLocation[] quarters = outlineTextures();
    if (quarters == null) return;
    for (int i = 0; i < 10; i++) {
      float lo = i * 2f, hi = lo + 2f;
      float covered = Math.min(hi, to) - Math.max(lo, from);
      // a quarter icon is 0.5 saturation: less than that (a leftover like 0.1) draws nothing, so the bar is only gold
      // where there really is saturation
      int q = Math.min(4, (int) Math.floor(covered * 2f + 0.001f)); // whole quarters of this icon
      if (q <= 0) continue;
      int x = r - i * 8 - 9;
      Draw.texture(quarters[q - 1], x, t, 9, 9, alpha);
    }
  }

  /** Hearts that the held food could heal over time (natural healing needs hunger 18+, each heart point costs 3 exhaustion). */
  private static void health(Gui gui, EntityPlayer p, int newFood, float newSat, int screenWidth, int screenHeight, float alpha) {
    if (p.getAbsorptionAmount() > 0 || p.getMaxHealth() > 20) return; // one plain row of hearts only
    float budget = newSat + Math.max(0, newFood - 17);
    int gain = (int) Math.floor(budget * 4f / 3f);
    int health = (int) Math.ceil(p.getHealth()), max = (int) p.getMaxHealth();
    int to = Math.min(max, health + gain);
    if (to <= health) return;
    bindIcons();
    GlStateManager.enableBlend();
    GlStateManager.color(1f, 1f, 1f, alpha);
    int left = screenWidth / 2 - 91, top = screenHeight - 39;
    for (int i = health / 2; i < (to + 1) / 2; i++) {
      int points = Math.min(2, to - i * 2);
      if (points <= 0) continue;
      gui.drawTexturedModalRect(left + i * 8, top, points >= 2 ? 52 : 61, 0, 9, 9);
    }
    GlStateManager.color(1f, 1f, 1f, 1f);
  }

  private static void bindIcons() {
    Minecraft.getMinecraft().getTextureManager().bindTexture(Gui.icons);
  }

  /** Back to what vanilla expects in the middle of drawing the bars: icons bound, texturing on, white. */
  private static void restore() {
    GlStateManager.enableTexture2D();
    GlStateManager.color(1f, 1f, 1f, 1f);
    bindIcons();
  }

  // ---- the gold outline, traced from the food icon of the resource pack in use ----

  private static ResourceLocation[] outlines;
  private static DynamicTexture[] outlineTextures;
  private static int outlinePacks = Integer.MIN_VALUE;
  private static boolean outlineFailed;

  private static ResourceLocation[] outlineTextures() {
    Minecraft mc = Minecraft.getMinecraft();
    int packs = mc.gameSettings.resourcePacks.hashCode();
    if (outlines != null && packs == outlinePacks) return outlines;
    if (outlineFailed && packs == outlinePacks) return null;
    outlinePacks = packs;
    try (InputStream in = mc.getResourceManager().getResource(Gui.icons).getInputStream()) {
      BufferedImage icons = ImageIO.read(in);
      int s = Math.max(1, icons.getWidth() / 256), size = 9 * s;
      BufferedImage full = icons.getSubimage(52 * s, 27 * s, size, size);
      if (outlineTextures != null) for (DynamicTexture d : outlineTextures) TextureUtil.deleteTexture(d.getGlTextureId());
      outlines = new ResourceLocation[4];
      outlineTextures = new DynamicTexture[4];
      for (int q = 1; q <= 4; q++) {
        // a quarter-filled icon keeps the right part of the outline, like vanilla's half icons
        int keepFrom = size - (int) Math.ceil(size * q / 4.0);
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < size; y++)
          for (int x = keepFrom; x < size; x++)
            if (opaque(full, x, y) && edge(full, x, y)) out.setRGB(x, y, 0xFFF2B634);
        outlineTextures[q - 1] = new DynamicTexture(out);
        outlines[q - 1] = mc.getTextureManager().getDynamicTextureLocation("luka_saturation_" + q, outlineTextures[q - 1]);
      }
      outlineFailed = false;
      return outlines;
    } catch (Throwable error) {
      outlineFailed = true;
      outlines = null;
      Cbui.reportOnce("saturation outline texture", error);
      return null;
    }
  }

  private static boolean opaque(BufferedImage image, int x, int y) {
    return x >= 0 && y >= 0 && x < image.getWidth() && y < image.getHeight() && (image.getRGB(x, y) >>> 24) > 32;
  }

  private static boolean edge(BufferedImage image, int x, int y) {
    return !opaque(image, x - 1, y) || !opaque(image, x + 1, y) || !opaque(image, x, y - 1) || !opaque(image, x, y + 1);
  }
}

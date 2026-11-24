package dev.cbui.hud.module;

import dev.cbui.hud.AbstractHudModule;
import dev.cbui.hud.Anchor;
import dev.cbui.mixin.GuiIngameBossInvoker;
import net.minecraft.client.gui.Gui;
import dev.luka.compat.Compat;
import dev.luka.compat.GlStateManager;
import net.minecraft.entity.boss.BossStatus;

/**
 * The boss bar (wither, ender dragon, and the bars servers show with them) as a HUD element: move it, scale it or switch it off
 * in the HUD editor like any other module. The game's own boss bar drawing runs inside this box ({@code BossBarGuiIngameMixin}
 * stops it at its usual place), so a bar a server styles through Envoy moves along with it. Switched off, no boss bar is shown.
 */
public final class BossBarModule extends AbstractHudModule {
  private static final int BAR = 182;
  private static volatile BossBarModule instance;

  public BossBarModule() {
    super("Boss bar", true, Anchor.TOP_CENTER, 0, 2, BAR, 15);
    background.set(false);
    instance = this;
  }

  /** Called where the game would draw its boss bar: true when this module draws it instead (or hides it). */
  public static boolean takesOver() {
    BossBarModule m = instance;
    if (m == null) return false;
    if (!m.enabled() || !m.shownOnHud()) {
      // hidden: the bar's timer still runs down once a frame, as the game's own drawing would do
      if (BossStatus.bossName != null && BossStatus.statusBarTime > 0) BossStatus.statusBarTime--;
    }
    return true;
  }

  private static boolean active() {
    return BossStatus.bossName != null && BossStatus.statusBarTime > 0;
  }

  @Override protected boolean nothingToShow() { return !active(); }

  @Override protected boolean padded() { return false; }

  @Override
  protected void draw(float partialTicks, boolean editor) {
    if (active() && mc.ingameGUI != null) {
      // the game draws the bar from screen x (width / 2 - 91), y 2 for the name and y 12 for the bar: move that onto this box
      int left = Compat.scaled(mc).getScaledWidth() / 2 - BAR / 2;
      GlStateManager.pushMatrix();
      try {
        GlStateManager.translate(-left, -2, 0);
        GlStateManager.color(1f, 1f, 1f, 1f);
        mc.getTextureManager().bindTexture(Gui.icons);
        ((GuiIngameBossInvoker) mc.ingameGUI).cbui$renderBossHealth();
      } finally {
        GlStateManager.popMatrix();
      }
    } else if (editor && mc.ingameGUI != null) {
      // a sample in the HUD editor: the game's bar, two thirds full, and a name
      GlStateManager.color(1f, 1f, 1f, 1f);
      mc.getTextureManager().bindTexture(Gui.icons);
      mc.ingameGUI.drawTexturedModalRect(0, 10, 0, 74, BAR, 5);
      mc.ingameGUI.drawTexturedModalRect(0, 10, 0, 79, BAR * 2 / 3, 5);
      String name = "Boss bar";
      mc.fontRendererObj.drawStringWithShadow(name, BAR / 2 - mc.fontRendererObj.getStringWidth(name) / 2, 0, 0xFFFFFF);
      GlStateManager.color(1f, 1f, 1f, 1f);
    }
  }
}

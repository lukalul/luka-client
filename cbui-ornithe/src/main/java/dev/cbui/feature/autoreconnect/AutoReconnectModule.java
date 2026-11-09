package dev.cbui.feature.autoreconnect;

import dev.cbui.feature.FeatureModule;
import dev.cbui.feature.Features;
import dev.cbui.hud.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;

/** After a kick, a server restart or a lost connection, joins the same server again after a short countdown (Escape cancels). */
public final class AutoReconnectModule extends FeatureModule implements Features.Synced {
  public final Setting.Slider delay = add(new Setting.Slider(
      "Delay", 5f, 1f, 60f, 1f, "Seconds to wait on the disconnect screen before trying again"));
  public final Setting.Slider attempts = add(new Setting.Slider(
      "Attempts", 5f, 1f, 30f, 1f, "How many times in a row to try before giving up"));

  private ServerData last;
  private int ticksLeft = -1;
  private int tries;
  private boolean cancelled;

  public AutoReconnectModule() { super("Auto Reconnect", true); }
  @Override public String category() { return "UTILITY"; }

  /** Seconds still to wait, or -1 when no reconnect is pending. */
  public int secondsLeft() { return ticksLeft < 0 ? -1 : (ticksLeft + 19) / 20; }
  public int tries() { return tries; }
  public int maxTries() { return (int) attempts.get().floatValue(); }

  public void cancel() { cancelled = true; ticksLeft = -1; }

  @Override public void sync() {
    Minecraft mc = Minecraft.getMinecraft();
    if (mc.theWorld != null && mc.thePlayer != null) {
      ServerData data = mc.getCurrentServerData();
      if (data != null) last = data;
      tries = 0;
      cancelled = false;
      ticksLeft = -1;
      return;
    }
    if (!enabled() || last == null || cancelled || !(mc.currentScreen instanceof GuiDisconnected)) {
      if (!(mc.currentScreen instanceof GuiDisconnected)) ticksLeft = -1;
      return;
    }
    if (tries >= maxTries()) return;
    if (ticksLeft < 0) ticksLeft = (int) (delay.get().floatValue() * 20);
    if (--ticksLeft > 0) return;
    ticksLeft = -1;
    tries++;
    mc.displayGuiScreen(new GuiConnecting(new GuiMultiplayer(new GuiMainMenu()), mc, last));
  }
}

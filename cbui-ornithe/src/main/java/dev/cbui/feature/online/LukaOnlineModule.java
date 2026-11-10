package dev.cbui.feature.online;

import dev.cbui.Cbui;
import dev.cbui.feature.FeatureModule;
import dev.cbui.gui.FriendsScreen;
import dev.cbui.hud.Setting;
import dev.cbui.social.LukaNet;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.settings.KeyBinding;

/**
 * Luka Online: the client's link to luka's service. Other luka players show a luka icon (coloured by rank) beside their nametag and wear
 * their rank's cape, and you get a friends list. Switching the card off stops all contact with the service.
 */
public final class LukaOnlineModule extends FeatureModule implements dev.cbui.feature.Features.Synced {
  public final Setting.Toggle icons = add(new Setting.Toggle("Show luka icon on nametags", true, "A small luka mark beside the nametag of every other luka player, in the colour of their rank (owner, moderator, creator...)."));
  public final Setting.Toggle capes = addIf(dev.cbui.Release.COSMETICS, new Setting.Toggle("Show luka capes", true, "Show the capes luka players have earned (by rank or given to them). Your own is chosen in Cosmetics."));
  public final Setting.Toggle alerts = add(new Setting.Toggle("Friend alerts in chat", true, "A chat line when a friend comes online or sends you a friend request."));
  public final Setting.Toggle share = add(new Setting.Toggle("Show friends my server", true, "Friends see which server you are on (and can join it). Off: they only see that you are online."));

  public final Setting.Toggle pings = addIf(dev.cbui.Release.PINGS, new Setting.Toggle("Show friend pings", true, "Places your friends mark with the Ping key show up as a waypoint (for a few minutes) while you are on the same server."));
  public final Setting.Toggle streamer = add(new Setting.Toggle("Streamer mode", false, "For streaming and recording: friends cannot see your server, friend alerts stay out of the chat, and the server address box shows a placeholder."));

  private KeyBinding friendsKey, pingKey;
  private boolean keyInstalled;

  public LukaOnlineModule() { super("Luka Online", true); }

  /** A setting for a part of Luka Online the service does not offer yet is kept (with its default) but not listed. */
  private <T extends Setting<?>> T addIf(boolean shown, T setting) { return shown ? add(setting) : setting; }

  @Override public String category() { return "UTILITY"; }

  public boolean showIcons() { return icons.get(); }
  public boolean showCapes() { return capes.get(); }
  public boolean friendAlerts() { return alerts.get() && !streamer.get(); }
  public boolean shareServer() { return share.get() && !streamer.get(); }
  public boolean streaming() { return streamer.get(); }
  public boolean showPings() { return pings.get(); }

  /** True while Streamer mode is on (the server address box and similar readouts hide what they would show). */
  public static boolean streamerMode() {
    LukaOnlineModule m = dev.cbui.feature.Features.get(LukaOnlineModule.class);
    return m != null && m.streaming();
  }

  /** Runs every tick, even while the card is off, so the Friends key is always registered (and kept in options.txt). */
  @Override public void sync() {
    try {
      installKey();
      if (friendsKey != null && friendsKey.getKeyCode() != 0 && friendsKey.isPressed() && mc.currentScreen == null && mc.theWorld != null)
        mc.displayGuiScreen(new FriendsScreen(null));
      if (pingKey != null && pingKey.getKeyCode() != 0 && pingKey.isPressed() && mc.currentScreen == null && mc.theWorld != null && enabled()) ping();
      if (dev.cbui.Release.SOCIAL && enabled()) LukaNet.tick(this);
    } catch (Throwable error) { Cbui.reportOnce("luka online sync", error); }
  }

  /** Marks what the crosshair points at (or where you stand when nothing is in reach) for friends on this server. */
  private void ping() {
    net.minecraft.util.MovingObjectPosition hit = mc.thePlayer.rayTrace(300.0, 1.0f);
    if (hit != null && hit.getBlockPos() != null) LukaNet.sendPing(hit.getBlockPos().getX(), hit.getBlockPos().getY() + 1, hit.getBlockPos().getZ());
    else LukaNet.sendPing((int) Math.floor(mc.thePlayer.posX), (int) Math.floor(mc.thePlayer.posY), (int) Math.floor(mc.thePlayer.posZ));
  }

  private KeyBinding makeKey(String description) {
    KeyBinding key = new KeyBinding(description, 0, "luka");
    File options = new File(mc.mcDataDir, "options.txt");
    if (options.isFile()) {
      try {
        String marker = "key_" + key.getKeyDescription() + ":";
        for (String line : Files.readAllLines(options.toPath(), StandardCharsets.UTF_8)) {
          if (!line.startsWith(marker)) continue;
          try { key.setKeyCode(Integer.parseInt(line.substring(marker.length()).trim())); } catch (NumberFormatException ignored) { }
          break;
        }
      } catch (java.io.IOException ignored) { }
    }
    return key;
  }

  private void installKey() {
    if (keyInstalled || mc.gameSettings == null) return;
    keyInstalled = true;
    try {
      KeyBinding friends = makeKey("Open friends");
      KeyBinding ping = dev.cbui.Release.PINGS ? makeKey("Ping location") : null;
      List<KeyBinding> all = new ArrayList<KeyBinding>();
      for (KeyBinding existing : mc.gameSettings.keyBindings) all.add(existing);
      all.add(friends);
      if (ping != null) all.add(ping);
      mc.gameSettings.keyBindings = all.toArray(new KeyBinding[all.size()]);
      KeyBinding.resetKeyBindingArrayAndHash();
      friendsKey = friends;
      pingKey = ping;
    } catch (Throwable error) { Cbui.reportOnce("friends key", error); }
  }
}

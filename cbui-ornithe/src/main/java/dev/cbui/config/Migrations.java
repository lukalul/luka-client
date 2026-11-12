package dev.cbui.config;

import dev.cbui.Cbui;
import dev.cbui.hud.AbstractHudModule;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;

/**
 * One-time changes to saved settings, run once the settings are loaded. Each runs once per game folder (its name is kept
 * in config/luka-migrations.txt), so a player who switches something back afterwards keeps their choice.
 */
public final class Migrations {
  private Migrations() {}

  public static void run() {
    try {
      File file = new File(Minecraft.getMinecraft().mcDataDir, "config/luka-migrations.txt");
      List<String> done = file.isFile() ? Files.readAllLines(file.toPath(), StandardCharsets.UTF_8) : Collections.<String>emptyList();
      // 2026-11-11: Server Cooldowns is off unless the player turns it on (it used to start on, and that was saved)
      once(file, done, "server-cooldowns-off", () -> switchOff("Server Cooldowns"));
      // 2026-11-12: armour and names follow how far players are drawn unless the player picked a distance. The old
      // default (48) was saved like a choice, so only 48 moves; any other number was set by hand and stays.
      once(file, done, "detail-distances-follow-players", () -> {
        boolean changed = false;
        if (GlobalOptions.layerDistance == 48) { GlobalOptions.layerDistance = 128; changed = true; }
        if (GlobalOptions.nametagDistance == 48) { GlobalOptions.nametagDistance = 64; changed = true; }
        if (changed && Cbui.config() != null && Cbui.hud() != null) Cbui.config().save(Cbui.hud());
      });
    } catch (Throwable error) {
      Cbui.reportOnce("settings migration", error);
    }
  }

  private static void once(File file, List<String> done, String name, Runnable change) throws Exception {
    if (done.contains(name)) return;
    change.run();
    file.getParentFile().mkdirs();
    Files.write(file.toPath(), (name + System.lineSeparator()).getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
  }

  private static void switchOff(String module) {
    if (Cbui.hud() == null) return;
    AbstractHudModule m = Cbui.hud().find(module);
    if (m == null || !m.enabled()) return;
    m.enabled(false);
    if (Cbui.config() != null) Cbui.config().save(Cbui.hud());
  }
}

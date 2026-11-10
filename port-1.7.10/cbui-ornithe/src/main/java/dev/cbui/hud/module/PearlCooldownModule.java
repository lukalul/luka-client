package dev.cbui.hud.module;

import dev.cbui.hud.AbstractHudModule;
import dev.cbui.hud.Anchor;
import dev.cbui.hud.Setting;
import net.minecraft.entity.item.EntityEnderPearl;

/** Counts down the server's ender pearl cooldown from the moment the player's own pearl appears (for servers that send no cooldown themselves). */
public final class PearlCooldownModule extends AbstractHudModule {
  private final Setting.Slider seconds = grouped("Cooldown", add(new Setting.Slider("Cooldown", 16, 1, 60, 1, "How long the server makes you wait between pearls, in seconds")));
  private final Setting.Toggle hideWhenReady = grouped("Cooldown", add(new Setting.Toggle("Hide when ready", true, "Show nothing while you can throw")));

  private int knownPearl = Integer.MIN_VALUE;
  private long readyAt;
  private String text = "Pearl: ready";

  public PearlCooldownModule() { super("Pearl cooldown", false, Anchor.CENTER, 0, 40, 70, 11); }

  @Override public void tick() {
    if (mc.theWorld == null || mc.thePlayer == null) { readyAt = 0; knownPearl = Integer.MIN_VALUE; return; }
    for (int i = 0, n = mc.theWorld.loadedEntityList.size(); i < n; i++) {
      Object o = mc.theWorld.loadedEntityList.get(i);
      if (!(o instanceof EntityEnderPearl)) continue;
      EntityEnderPearl pearl = (EntityEnderPearl) o;
      if (pearl.getEntityId() == knownPearl) continue;
      // the client is not told who threw a pearl: one that first shows up right next to the player's head is the player's own
      double dx = pearl.posX - mc.thePlayer.posX, dy = pearl.posY - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight()), dz = pearl.posZ - mc.thePlayer.posZ;
      if (dx * dx + dy * dy + dz * dz > 9.0) { knownPearl = pearl.getEntityId(); continue; }
      knownPearl = pearl.getEntityId();
      readyAt = System.currentTimeMillis() + (long) (seconds.get() * 1000L);
    }
    long left = readyAt - System.currentTimeMillis();
    text = left > 0 ? "Pearl: " + (left / 100) / 10.0 + "s" : "Pearl: ready";
    size(textWidth("Pearl: 16.0s"), linesHeight(1));
  }

  @Override protected boolean nothingToShow() { return hideWhenReady.get() && readyAt <= System.currentTimeMillis(); }

  @Override protected void draw(float partialTicks, boolean editor) {
    boolean active = readyAt > System.currentTimeMillis();
    if (!editor && !active && hideWhenReady.get()) return;
    text(editor ? "Pearl: 12.3s" : text, 0, 0, active ? 0xFFFFAA00 : 0xFF55FF55);
  }
}

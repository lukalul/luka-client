package dev.cbui.hud.module;

import dev.cbui.hud.AbstractHudModule;
import dev.cbui.hud.Anchor;
import dev.cbui.hud.Setting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.resources.ResourcePackRepository;

/** Lists the resource packs currently loaded, highest priority first, so a pack swap is visible at a glance. */
public final class ResourcePacksModule extends AbstractHudModule {
  private final Setting.Slider maxLines = grouped("Display", add(new Setting.Slider("Max packs", 5, 1, 12, 1, "How many active packs to list")));
  private final Setting.Slider maxWidth = grouped("Display", adv(new Setting.Slider("Max width", 160, 60, 300, 10, "Longer pack names are cut short with ...")));

  private final List<String> names = new ArrayList<>();

  public ResourcePacksModule() { super("Resource packs", false, Anchor.TOP_LEFT, 5, 71, 80, 11); }

  private int cooldown;

  @Override public void tick() {
    // the pack list only changes when the player applies packs; checking every two seconds is plenty
    if (cooldown-- > 0 && !names.isEmpty()) return;
    cooldown = 40;
    names.clear();
    List<?> entries = mc.getResourcePackRepository().getRepositoryEntries();
    int limit = maxWidth.get().intValue();
    // the repository lists lowest priority first; show the pack that wins on top
    for (int i = entries.size() - 1; i >= 0 && names.size() < maxLines.get().intValue(); i--)
      names.add(fit(strip(((net.minecraft.client.resources.ResourcePackRepository$Entry) entries.get(i)).getResourcePackName()), limit));
    if (names.isEmpty()) names.add("Default");
    float widest = 0;
    for (String n : names) widest = Math.max(widest, textWidth(n));
    size(widest, linesHeight(names.size()));
  }

  private static String strip(String name) {
    String plain = name.replaceAll("§.", "");
    return plain.endsWith(".zip") ? plain.substring(0, plain.length() - 4) : plain;
  }

  private String fit(String text, int width) {
    if (textWidth(text) <= width) return text;
    String out = text;
    while (out.length() > 1 && textWidth(out + "...") > width) out = out.substring(0, out.length() - 1);
    return out + "...";
  }

  @Override protected void draw(float partialTicks, boolean editor) {
    float y = 0;
    for (String n : names) {
      text(n, 0, y, textColor.get());
      y += lineStep();
    }
  }
}

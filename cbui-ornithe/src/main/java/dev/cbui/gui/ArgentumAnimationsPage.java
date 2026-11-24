package dev.cbui.gui;

import dev.cbui.Cbui;
import dev.cbui.config.AnimationOptions;
import dev.cbui.config.GlobalOptions;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumParticleTypes;
import org.embeddedt.embeddium.impl.gui.framework.TextComponent;
import org.taumc.celeritas.api.OptionGUIConstructionEvent;
import org.taumc.celeritas.api.options.OptionIdentifier;
import org.taumc.celeritas.api.options.control.TickBoxControl;
import org.taumc.celeritas.api.options.structure.OptionGroup;
import org.taumc.celeritas.api.options.structure.OptionImpl;
import org.taumc.celeritas.api.options.structure.OptionPage;
import org.taumc.celeritas.api.options.structure.OptionStorage;

/** Adds luka's fine-grained animation settings to Argentum's Celeritas options UI. */
public final class ArgentumAnimationsPage {
  private static final String MOD_ID = "luka";
  private static final Data DATA = new Data();
  private static final OptionStorage<Data> STORAGE = new OptionStorage<Data>() {
    @Override public Data getData() { return DATA; }

    @Override public void save() {
      Minecraft.getMinecraft().gameSettings.saveOptions();
      if (Cbui.config() != null && Cbui.hud() != null) Cbui.config().save(Cbui.hud());
    }
  };

  private ArgentumAnimationsPage() {}

  /** Uses the same public construction event as Argentum Extras. */
  public static void register() {
    OptionGUIConstructionEvent.BUS.addListener(event -> event.addPage(create()));
  }

  private static OptionPage create() {
    OptionGroup.Builder particles = OptionGroup.createBuilder().setId(id("particles"));
    for (EnumParticleTypes type : EnumParticleTypes.values()) {
      final String key = AnimationOptions.particleKey(type);
      particles.add(toggle(key, AnimationOptions.readable(type)));
    }

    OptionGroup.Builder other = OptionGroup.createBuilder().setId(id("other_animations"));
    for (int i = 0; i < AnimationOptions.OTHER_KEYS.length; i++)
      other.add(toggle(AnimationOptions.OTHER_KEYS[i], AnimationOptions.OTHER_NAMES[i]));

    return new OptionPage(id("animations"), TextComponent.literal("Animations"),
        Arrays.asList(particles.build(), other.build()));
  }

  private static OptionImpl<Data, Boolean> toggle(final String key, String name) {
    return OptionImpl.createBuilder(Boolean.class, STORAGE)
        .setId(OptionIdentifier.create(MOD_ID, key.replace('.', '_'), Boolean.class))
        .setName(TextComponent.literal(name))
        .setBinding((data, value) -> GlobalOptions.animationEnabled(key, value),
            data -> GlobalOptions.animationEnabled(key))
        .setControl(TickBoxControl::new)
        .build();
  }

  private static OptionIdentifier<Void> id(String path) {
    return OptionIdentifier.create(MOD_ID, path);
  }

  private static final class Data {}
}

package dev.cbui.feature.shotchat;

import dev.cbui.feature.FeatureModule;
import dev.cbui.feature.Features;
import dev.cbui.hud.Setting;

/**
 * Screenshot message: replaces vanilla's "saved as" chat line with luka's own line and action buttons, and can copy
 * or re-pack each new screenshot. Lives on the GENERAL settings tab. Writing the picture in the background
 * ({@link ShotQueue}) and the hidden button command ({@link ShotLinks}) work whether the module is on or off.
 */
public final class ShotChatModule extends FeatureModule {
  final Setting.Toggle fileName = grouped("Chat message", add(new Setting.Toggle("Include file name", false,
      "Adds the file name to the message; click it to open the picture.")));
  final Setting.Toggle viewButton = grouped("Chat message", add(new Setting.Toggle("View button", true,
      "A button that opens the picture.")));
  final Setting.Toggle browseButton = grouped("Chat message", add(new Setting.Toggle("Browse button", true,
      "A button that opens the screenshots folder.")));
  final Setting.Toggle clipboardButton = grouped("Chat message", add(new Setting.Toggle("Clipboard button", true,
      "A button that copies the picture itself.")));
  final Setting.Toggle shareButton = grouped("Chat message", add(new Setting.Toggle("Share button", true,
      "A button that uploads the picture to catbox.moe (public) when clicked.")));
  final Setting.Toggle removeButton = grouped("Chat message", add(new Setting.Toggle("Remove button", true,
      "A button that deletes the picture from disk.")));
  final Setting.Toggle askBeforeRemoving = grouped("Chat message", add(new Setting.Toggle("Ask before removing", true,
      "Removing needs a second click within 10 seconds.")));
  final Setting.Toggle copyNew = grouped("After saving", add(new Setting.Toggle("Put new screenshots on clipboard", false,
      "Every new screenshot is copied right away.")));
  final Setting.Toggle shrink = grouped("After saving", add(new Setting.Toggle("Shrink PNG files", true,
      "Re-packs each screenshot smaller without touching a pixel.")));

  public ShotChatModule() {
    super("Screenshot Message Enhancer", true);
  }

  @Override
  public String category() {
    return "UTILITY";
  }

  /** The module whether it is on or not (button clicks on existing messages work while it is off). */
  static ShotChatModule find() {
    return Features.get(ShotChatModule.class);
  }

  /** The module while it is switched on, else null. */
  static ShotChatModule active() {
    ShotChatModule module = find();
    return module != null && module.enabled() ? module : null;
  }
}

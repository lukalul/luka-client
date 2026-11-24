package dev.cbui.mixin;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mods register their keys at different times after the game starts, and saving the options only writes keys registered so far.
 * Without this, a save made too early, or a mod that registers late, loses that mod's saved keybinds.
 */
@Mixin(GameSettings.class)
public abstract class KeepKeybindsMixin {
  @Shadow private File optionsFile;
  @Shadow public KeyBinding[] keyBindings;
  @Unique private List<String> cbui$notRegistered;

  @Inject(method = "saveOptions", at = @At("HEAD"), require = 0)
  private void cbui$rememberNotRegistered(CallbackInfo ci) {
    cbui$notRegistered = new ArrayList<String>();
    try {
      if (!optionsFile.isFile()) return;
      Set<String> known = new HashSet<String>();
      for (KeyBinding key : keyBindings) known.add(key.getKeyDescription());
      for (String line : Files.readAllLines(optionsFile.toPath(), StandardCharsets.UTF_8)) {
        int colon = line.lastIndexOf(':');
        if (!line.startsWith("key_") || colon <= 4) continue;
        String description = line.substring(4, colon), renamed = dev.cbui.config.CardRenames.newKey(description);
        // a renamed key's line under its old name goes once the key is registered (and so saved) under the new one
        if (!known.contains(description) && (renamed == null || !known.contains(renamed))) cbui$notRegistered.add(line);
      }
    } catch (Throwable ignored) {
      cbui$notRegistered.clear();
    }
  }

  @Inject(method = "saveOptions", at = @At("RETURN"), require = 0)
  private void cbui$keepNotRegistered(CallbackInfo ci) {
    try {
      if (cbui$notRegistered != null && !cbui$notRegistered.isEmpty()) {
        Files.write(optionsFile.toPath(), cbui$notRegistered, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
      }
    } catch (Throwable ignored) {
      // the keys are written again by the next save
    }
  }
}

package dev.cbui.mixin;

import java.util.Map;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Sound Tweaks: the sound manager's map from a playing sound to the library's source name. */
@Mixin(SoundManager.class)
public interface SoundMixerSoundManagerAccess {
  @Accessor("invPlayingSounds")
  Map<ISound, String> cbui$soundNames();
}

package dev.cbui.feature.soundmixer;

import dev.cbui.mixin.SoundMixerSoundManagerAccess;
import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.SoundManager;
import org.lwjgl.openal.AL10;
import paulscode.sound.Channel;
import paulscode.sound.Library;
import paulscode.sound.SoundSystem;
import paulscode.sound.SoundSystemConfig;
import paulscode.sound.Source;
import paulscode.sound.libraries.ChannelLWJGLOpenAL;

/**
 * Lets a sound play louder than full volume. The sound library holds every source's volume to 1 and OpenAL holds
 * a source's gain to its maximum gain (1 by default), so a sound whose wanted volume is above 1 is noted here and,
 * once the library has created its source, that source gets the real volume and a higher gain ceiling.
 */
final class LoudSources {
  /** The highest gain a boosted source may reach (200 % of a full-volume sound, with room for category volume). */
  private static final float CEILING = 4f;
  /** Ticks to wait for the library to create a source before giving up on it. */
  private static final int PATIENCE = 40;

  private final Map<ISound, float[]> waiting = new IdentityHashMap<ISound, float[]>();
  private Object manager;
  private Field systemField;
  private Field libraryField;

  /** Called on the client thread whenever the game worked out a sound's volume. */
  void noteVolume(Object soundManager, ISound sound, float volume) {
    if (sound == null) return;
    if (volume > 1f || waiting.containsKey(sound)) {
      manager = soundManager;
      waiting.put(sound, new float[] {volume, 0});
    }
  }

  void clear() {
    waiting.clear();
  }

  /** Called every client tick: hands the wanted volumes to sources the library has created by now. */
  @SuppressWarnings("unchecked")
  void apply() throws ReflectiveOperationException {
    if (waiting.isEmpty() || !(manager instanceof SoundMixerSoundManagerAccess)) return;
    SoundMixerSoundManagerAccess access = (SoundMixerSoundManagerAccess) manager;
    SoundSystem system = system(manager);
    if (system == null) {
      waiting.clear();
      return;
    }
    Library library = (Library) libraryField(system).get(system);
    if (library == null) return;
    Map<ISound, String> names = access.cbui$soundNames();
    synchronized (SoundSystemConfig.THREAD_SYNC) {
      Map<String, Source> sources = library.getSources();
      for (Iterator<Map.Entry<ISound, float[]>> it = waiting.entrySet().iterator(); it.hasNext(); ) {
        Map.Entry<ISound, float[]> entry = it.next();
        float[] want = entry.getValue();
        String name = names.get(entry.getKey());
        Source source = name == null || sources == null ? null : sources.get(name);
        if (source == null || !attached(source)) {
          if (++want[1] > PATIENCE) it.remove();
          continue;
        }
        loudness(source, want[0]);
        it.remove();
      }
    }
  }

  private static boolean attached(Source source) {
    Channel channel = source.channel;
    return channel != null && channel.attachedSource == source;
  }

  private static void loudness(Source source, float volume) {
    if (source.channel instanceof ChannelLWJGLOpenAL) {
      ChannelLWJGLOpenAL al = (ChannelLWJGLOpenAL) source.channel;
      if (al.ALSource != null) AL10.alSourcef(al.ALSource.get(0), AL10.AL_MAX_GAIN, Math.max(1f, Math.min(CEILING, volume)));
    }
    source.sourceVolume = Math.max(0f, Math.min(CEILING, volume));
    source.positionChanged();
  }

  private SoundSystem system(Object soundManager) throws IllegalAccessException {
    if (systemField == null) {
      for (Field f : SoundManager.class.getDeclaredFields()) {
        if (SoundSystem.class.isAssignableFrom(f.getType())) {
          f.setAccessible(true);
          systemField = f;
          break;
        }
      }
      if (systemField == null) throw new IllegalStateException("sound manager has no sound system field");
    }
    return (SoundSystem) systemField.get(soundManager);
  }

  private Field libraryField(SoundSystem system) throws NoSuchFieldException {
    if (libraryField == null) {
      Field f = SoundSystem.class.getDeclaredField("soundLibrary");
      f.setAccessible(true);
      libraryField = f;
    }
    return libraryField;
  }
}

package dev.cbui.feature.shotchat;

import dev.cbui.Cbui;
import java.awt.image.RenderedImage;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.imageio.ImageIO;
import net.minecraft.util.EnumChatFormatting;

/**
 * Background work for screenshots. The PNG is written (and optionally re-packed and copied) on one ordered writer
 * thread, so taking a picture does not stall a frame; the file name is reserved on the game thread first, so two
 * pictures in the same second still get different names. Actions on a file (view, copy, upload, delete) run on
 * helper threads but first wait for that file's last writer job.
 */
public final class ShotQueue {
  private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> daemon(r, "luka screenshot writer"));
  private static final ExecutorService HELPERS = Executors.newCachedThreadPool(r -> daemon(r, "luka screenshot helper"));
  private static final Map<String, Future<?>> LAST_JOB = new ConcurrentHashMap<String, Future<?>>();

  private ShotQueue() {}

  private static Thread daemon(Runnable r, String name) {
    Thread t = new Thread(r, name);
    t.setDaemon(true);
    return t;
  }

  /**
   * Takes over vanilla's PNG write: reserves the file now and writes it in the background. False when the name could
   * not be reserved; the caller then writes it the vanilla way.
   */
  public static boolean saveInBackground(RenderedImage image, String format, File file) {
    try {
      File dir = file.getParentFile();
      if (dir != null && !dir.isDirectory()) dir.mkdirs();
      if (!file.exists() && !file.createNewFile()) return false;
    } catch (IOException | SecurityException cannotReserve) {
      return false;
    }
    ShotChatModule module = ShotChatModule.active();
    boolean shrink = module != null && module.shrink.get();
    boolean copy = module != null && module.copyNew.get();
    Future<?> job = WRITER.submit(() -> write(image, format, file, shrink, copy));
    remember(file, job);
    return true;
  }

  private static void write(RenderedImage image, String format, File file, boolean shrink, boolean copy) {
    try (OutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
      if (!ImageIO.write(image, format, out)) throw new IOException("no " + format + " writer");
    } catch (Throwable error) {
      if (file.length() == 0) file.delete();
      ShotLinks.say(ShotLinks.line("Could not save the screenshot " + file.getName() + ": " + reason(error), EnumChatFormatting.RED));
      return;
    }
    if (shrink) {
      try {
        PngRepack.shrink(file);
      } catch (Throwable error) {
        Cbui.reportOnce("Screenshot Message Enhancer shrink", error);
      }
    }
    if (copy) {
      try {
        ShotDesktop.copyImage(file);
      } catch (Throwable error) {
        ShotLinks.say(ShotLinks.line("Could not copy the screenshot: " + reason(error), EnumChatFormatting.RED));
      }
    }
  }

  /** Runs the task on a helper thread once every writer job on this file has finished. */
  static void afterWrites(File file, Runnable task) {
    HELPERS.execute(() -> {
      Future<?> pending = LAST_JOB.get(key(file));
      if (pending != null) {
        try {
          pending.get();
        } catch (Throwable ignored) {
          // a failed write already said so in chat
        }
      }
      try {
        task.run();
      } catch (Throwable error) {
        Cbui.reportOnce("Screenshot Message Enhancer action", error);
      }
    });
  }

  private static void remember(File file, Future<?> job) {
    LAST_JOB.put(key(file), job);
    if (LAST_JOB.size() > 200) {
      for (Iterator<Future<?>> it = LAST_JOB.values().iterator(); it.hasNext(); ) if (it.next().isDone()) it.remove();
    }
  }

  private static String key(File file) {
    return file.getAbsolutePath();
  }

  static String reason(Throwable error) {
    String message = error.getMessage();
    return message == null || message.trim().isEmpty() ? error.getClass().getSimpleName() : message.trim();
  }
}

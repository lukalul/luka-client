package dev.cbui.feature.crashguard;

import dev.cbui.Cbui;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import net.minecraft.client.Minecraft;
import net.minecraft.crash.CrashReport;
import net.minecraft.util.MinecraftError;
import net.minecraft.util.ReportedException;
import org.apache.logging.log4j.LogManager;

/**
 * Decides whether a crash during play is caught and, if so, recovers: report saved, render state reset, world left
 * when needed, recovery screen opened. Counts the crashes it caught this session so a crash loop still ends the
 * game: the "give up" crash and a third crash within a few seconds of two caught ones go the vanilla way.
 */
public final class CrashRecovery {
  private static final long BURST_MS = 10_000L;

  private static int caught;
  private static long lastCaughtAt;
  private static long previousCaughtAt;

  private CrashRecovery() {}

  /** An error escaped one frame of the game loop. Returns when recovered; otherwise throws it on unchanged. */
  public static void frameFailed(Minecraft mc, Throwable error) {
    if (error instanceof MinecraftError || error instanceof ThreadDeath) throw CrashRecovery.<RuntimeException>sneaky(error);
    boolean recovered = false;
    try {
      recovered = recover(mc, error, null);
    } catch (Throwable recoveryFailed) {
      Cbui.reportOnce("CrashPatch recovery", recoveryFailed);
    }
    if (!recovered) throw CrashRecovery.<RuntimeException>sneaky(error);
  }

  /** The singleplayer server crashed. True when recovered (the caller then clears the crash flag). */
  public static boolean serverCrashed(Minecraft mc, CrashReport report) {
    try {
      return recover(mc, null, report);
    } catch (Throwable recoveryFailed) {
      Cbui.reportOnce("CrashPatch recovery", recoveryFailed);
      return false;
    }
  }

  private static boolean recover(Minecraft mc, Throwable error, CrashReport serverReport) {
    CrashGuardModule module = CrashGuardModule.instance();
    if (module == null || !module.catching()) return false;
    int number = caught + 1;
    int limit = module.giveUpAtCrash();
    if (number >= limit) return false;
    long now = System.currentTimeMillis();
    if (caught >= 2 && now - previousCaughtAt <= BURST_MS) return false;
    caught = number;
    previousCaughtAt = lastCaughtAt;
    lastCaughtAt = now;

    boolean outOfMemory = error instanceof OutOfMemoryError;
    if (outOfMemory) {
      sendQuit(mc);
      try {
        mc.freeMemory();
      } catch (Throwable ignored) {
        // keep going with whatever memory there is
      }
    }
    CrashReport report = serverReport != null ? serverReport : reportFor(mc, error);
    String text = fullText(report, error);
    File file = save(mc, report);
    try {
      LogManager.getLogger("luka").fatal("Caught a crash and kept the game open:\n" + text);
    } catch (Throwable ignored) {
      // logging must not stop the recovery
    }
    GlRescue.reset(mc);
    if (serverReport != null || outOfMemory || number >= module.leaveFromCrash()) leaveWorld(mc);
    mc.displayGuiScreen(new RecoveryScreen(report, error, text, file, caught, Math.max(0, limit - 1), module.pasteSite()));
    return true;
  }

  private static CrashReport reportFor(Minecraft mc, Throwable error) {
    CrashReport report = error instanceof ReportedException
        ? ((ReportedException) error).getCrashReport()
        : new CrashReport("Unexpected error", error);
    try {
      mc.addGraphicsAndWorldToCrashReport(report);
    } catch (Throwable ignored) {
      // a broken world can fail here; the report still has the error
    }
    return report;
  }

  private static String fullText(CrashReport report, Throwable error) {
    try {
      return report.getCompleteReport();
    } catch (Throwable failed) {
      return String.valueOf(report.getDescription()) + "\n" + error;
    }
  }

  /** Saves the report as vanilla would, unless vanilla already did; null when it could not be written. */
  private static File save(Minecraft mc, CrashReport report) {
    try {
      if (report.getFile() != null) return report.getFile();
      File dir = new File(mc.mcDataDir, "crash-reports");
      File file = new File(dir, "crash-" + new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date()) + "-client.txt");
      return report.saveToFile(file) ? file : null;
    } catch (Throwable failed) {
      return null;
    }
  }

  private static void sendQuit(Minecraft mc) {
    try {
      if (mc.theWorld != null) mc.theWorld.sendQuittingDisconnectingPacket();
    } catch (Throwable ignored) {
      // the connection may already be gone
    }
  }

  /** Disconnects and unloads the world, each step on its own. */
  static void leaveWorld(Minecraft mc) {
    if (mc.theWorld == null && !mc.isIntegratedServerRunning()) return;
    sendQuit(mc);
    try {
      mc.loadWorld(null);
    } catch (Throwable error) {
      Cbui.reportOnce("CrashPatch leave world", error);
    }
  }

  @SuppressWarnings("unchecked")
  private static <T extends Throwable> T sneaky(Throwable error) throws T {
    throw (T) error;
  }
}

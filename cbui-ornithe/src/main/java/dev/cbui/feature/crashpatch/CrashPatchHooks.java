package dev.cbui.feature.crashpatch;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.crash.CrashReport;
import net.minecraft.util.MinecraftError;
import net.minecraft.util.ReportedException;
import org.lwjgl.opengl.GL11;

public final class CrashPatchHooks {
  private static final long BURST_PERIOD = 10_000L;
  private static final Deque<Long> burst = new ArrayDeque<Long>();
  private static int handledCount;

  private CrashPatchHooks() {}

  public static boolean handleClientFailure(Minecraft game, Throwable thrown) {
    CrashPatchModule settings = CrashPatchModule.enabledInstance();
    if (!recoverable(settings, thrown) || !reserveAttempt(settings, System.currentTimeMillis())) return false;
    boolean memoryFailure = thrown instanceof OutOfMemoryError;
    if (memoryFailure) try { game.freeMemory(); } catch (Throwable ignored) { }
    CrashReport report = thrown instanceof ReportedException
        ? ((ReportedException) thrown).getCrashReport()
        : new CrashReport(memoryFailure ? "Out of memory" : "Unexpected error", thrown);
    return present(game, settings, report, thrown, memoryFailure);
  }

  public static boolean handleIntegratedServerFailure(Minecraft game, CrashReport report) {
    CrashPatchModule settings = CrashPatchModule.enabledInstance();
    if (settings == null || !settings.catchCrashes.get() || report == null
        || !reserveAttempt(settings, System.currentTimeMillis())) return false;
    return present(game, settings, report, report.getCrashCause(), true);
  }

  private static boolean recoverable(CrashPatchModule settings, Throwable thrown) {
    return settings != null && settings.catchCrashes.get() && thrown != null
        && !(thrown instanceof MinecraftError) && !(thrown instanceof ThreadDeath);
  }

  private static synchronized boolean reserveAttempt(CrashPatchModule settings, long now) {
    while (!burst.isEmpty() && now - burst.peekFirst().longValue() > BURST_PERIOD) burst.removeFirst();
    if (burst.size() >= 2 || handledCount + 1 >= Math.round(settings.crashLimit.get())) return false;
    burst.addLast(Long.valueOf(now));
    handledCount++;
    return true;
  }

  private static boolean present(Minecraft game, CrashPatchModule settings, CrashReport report,
      Throwable thrown, boolean mustDisconnect) {
    try { game.addGraphicsAndWorldToCrashReport(report); } catch (Throwable ignored) { }
    File file = persist(game, report);
    try { org.apache.logging.log4j.LogManager.getLogger("CrashPatch")
        .fatal("Recovered from a game-loop failure", thrown); } catch (Throwable ignored) { }
    makeGuiSafe(game);
    int ordinal;
    synchronized (CrashPatchHooks.class) { ordinal = handledCount; }
    if (mustDisconnect || ordinal >= Math.round(settings.leaveLimit.get())) disconnect(game);
    CrashScreen screen = new CrashScreen(report, thrown, file, ordinal,
        Math.round(settings.crashLimit.get()), settings.uploadMethod.get());
    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        if (attempt != 0) game.currentScreen = null;
        game.displayGuiScreen(screen);
        return game.currentScreen == screen;
      } catch (Throwable ignored) { }
    }
    return false;
  }

  private static File persist(Minecraft game, CrashReport report) {
    try {
      if (report.getFile() != null) return report.getFile();
      String stamp = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date());
      File destination = new File(new File(game.mcDataDir, "crash-reports"), "crash-" + stamp + "-client.txt");
      return report.saveToFile(destination) ? destination : null;
    } catch (Throwable ignored) { return null; }
  }

  private static void makeGuiSafe(Minecraft game) {
    try { Tessellator.getInstance().getWorldRenderer().finishDrawing(); } catch (Throwable ignored) { }
    try { if (OpenGlHelper.isFramebufferEnabled() && game.getFramebuffer() != null)
      game.getFramebuffer().unbindFramebuffer(); } catch (Throwable ignored) { }
    restoreMatrix(GL11.GL_PROJECTION, 2980);
    restoreMatrix(GL11.GL_TEXTURE, 2981);
    restoreMatrix(GL11.GL_MODELVIEW, 2979);
    try {
      GlStateManager.matrixMode(GL11.GL_MODELVIEW);
      GlStateManager.color(1f, 1f, 1f, 1f);
      GlStateManager.enableTexture2D();
      GlStateManager.disableLighting();
      GlStateManager.enableBlend();
      GlStateManager.enableAlpha();
      GlStateManager.depthMask(true);
      while (GL11.glGetError() != GL11.GL_NO_ERROR) { }
    } catch (Throwable ignored) { }
    try { if (game.entityRenderer != null && game.entityRenderer.isShaderActive())
      game.entityRenderer.stopUseShader(); } catch (Throwable ignored) { }
  }

  private static void restoreMatrix(int matrix, int depthName) {
    try {
      GL11.glMatrixMode(matrix);
      int remaining = 64;
      while (remaining-- > 0 && GL11.glGetInteger(depthName) > 1) GL11.glPopMatrix();
    } catch (Throwable ignored) { }
  }

  public static void disconnect(Minecraft game) {
    try {
      if (game.theWorld == null) return;
      try { game.theWorld.sendQuittingDisconnectingPacket(); } catch (Throwable ignored) { }
      game.loadWorld(null);
    } catch (Throwable ignored) { try { game.loadWorld(null); } catch (Throwable ignoredAgain) { } }
  }

  @SuppressWarnings("unchecked")
  public static <E extends Throwable> void propagate(Throwable thrown) throws E { throw (E) thrown; }
}

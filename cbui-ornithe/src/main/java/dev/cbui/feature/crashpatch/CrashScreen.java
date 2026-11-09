package dev.cbui.feature.crashpatch;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.crash.CrashReport;
import org.lwjgl.input.Keyboard;

public final class CrashScreen extends GuiScreen {
  private static final int CONTINUE = 0;
  private static final int COPY = 1;
  private static final int UPLOAD = 2;
  private static final int QUIT = 3;
  private static final Pattern HASTE_KEY = Pattern.compile("\"key\"\\s*:\\s*\"([A-Za-z0-9]+)\"");
  private static final Pattern MCLOGS_URL = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"");

  private final CrashReport crash;
  private final File location;
  private final List<String> detailLines;
  private final String counterLine;
  private final String provider;
  private volatile String activity = "";
  private GuiButton copyControl;
  private GuiButton uploadControl;

  CrashScreen(CrashReport crash, Throwable thrown, File location, int number, int limit, String provider) {
    this.crash = crash;
    this.location = location;
    this.provider = provider;
    this.detailLines = Collections.unmodifiableList(describe(crash, thrown));
    this.counterLine = "Crashes since the game started: " + number
        + " (the game closes normally at " + limit + ", or after 3 crashes within 10 seconds)";
  }

  private static List<String> describe(CrashReport crash, Throwable thrown) {
    List<String> lines = new ArrayList<String>();
    try {
      String description = crash.getDescription();
      lines.add("Description: " + (description == null ? "unknown" : description));
      Throwable cursor = thrown == null ? crash.getCrashCause() : thrown;
      int depth = 0;
      while (cursor != null && cursor.getCause() != null && cursor.getCause() != cursor && depth++ < 20)
        cursor = cursor.getCause();
      if (cursor != null) {
        String message = cursor.getMessage();
        lines.add(cursor.getClass().getName() + (message == null ? "" : ": " + message));
        StackTraceElement[] trace = cursor.getStackTrace();
        int visible = Math.min(trace.length, 5);
        for (int index = 0; index < visible; index++) lines.add("    at " + trace[index].toString());
      }
    } catch (Throwable ignored) { lines.add("The crash details could not be read; see the crash report."); }
    return lines;
  }

  @Override public void initGui() {
    buttonList.clear();
    int left = width / 2 - 100;
    int top = Math.max(height / 2 + 20, height - 110);
    buttonList.add(new GuiButton(CONTINUE, left, top, 200, 20, "Back to title screen"));
    copyControl = new GuiButton(COPY, left, top + 24, 200, 20, "Copy report");
    uploadControl = new GuiButton(UPLOAD, left, top + 48, 200, 20, "Upload report (" + provider + ")");
    buttonList.add(copyControl);
    buttonList.add(uploadControl);
    buttonList.add(new GuiButton(QUIT, left, top + 72, 200, 20, "Quit game"));
  }

  @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
    try {
      drawDefaultBackground();
      int middle = width / 2;
      drawCenteredString(fontRendererObj, "The game crashed", middle, 18, 0xFF5555);
      drawCenteredString(fontRendererObj, "Your game was kept open, but it is safer to go back to the title screen.", middle, 32, 0xAAAAAA);
      List<String> display = new ArrayList<String>(detailLines);
      display.add("");
      display.add(location == null ? "The crash report could not be saved to a file."
          : "Crash report saved to: " + location.getAbsolutePath());
      if (activity.length() != 0) display.add(activity);
      drawDetails(display, Math.max(100, width - 60), Math.max(height / 2 + 10, height - 120));
      drawCenteredString(fontRendererObj, counterLine, middle, height - 14, 0x888888);
    } catch (Throwable ignored) { }
    try { super.drawScreen(mouseX, mouseY, partialTicks); } catch (Throwable ignored) { }
  }

  private void drawDetails(List<String> lines, int wrapWidth, int lowerBound) {
    int cursorY = 52;
    outer: for (String source : lines) {
      List<?> wrapped = fontRendererObj.listFormattedStringToWidth(source, wrapWidth);
      for (Object segment : wrapped) {
        if (cursorY > lowerBound) break outer;
        drawString(fontRendererObj, (String) segment, 30, cursorY, source.startsWith("    at") ? 0x888888 : 0xFFFFFF);
        cursorY += 10;
      }
    }
  }

  @Override protected void actionPerformed(GuiButton button) {
    if (button == null) return;
    try {
      if (button.id == CONTINUE) returnToMenu();
      else if (button.id == COPY) copyReport();
      else if (button.id == UPLOAD) beginUpload();
      else if (button.id == QUIT) mc.shutdown();
    } catch (Throwable ignored) { }
  }

  private void copyReport() {
    GuiScreen.setClipboardString(crash.getCompleteReport());
    if (copyControl != null) copyControl.displayString = "Copied!";
  }

  @Override protected void keyTyped(char typed, int key) {
    if (key == Keyboard.KEY_ESCAPE) returnToMenu();
  }

  private void returnToMenu() {
    try { CrashPatchHooks.disconnect(mc); mc.displayGuiScreen(new GuiMainMenu()); } catch (Throwable ignored) { }
  }

  private void beginUpload() {
    final String body;
    try { body = crash.getCompleteReport(); }
    catch (Throwable ignored) { activity = "Could not build the report text."; return; }
    if (uploadControl != null) uploadControl.enabled = false;
    activity = "Uploading report...";
    final boolean useMclogs = provider.startsWith("mclo.gs");
    Thread worker = new Thread(new Runnable() {
      @Override public void run() {
        try {
          final String link = useMclogs ? sendMclogs(body) : sendHaste(body);
          activity = "Uploaded, link copied: " + link;
          Minecraft.getMinecraft().addScheduledTask(new Runnable() {
            @Override public void run() { try { GuiScreen.setClipboardString(link); } catch (Throwable ignored) { } }
          });
        } catch (Throwable failure) {
          activity = "Upload failed (" + failure.getClass().getSimpleName() + ")";
          if (uploadControl != null) uploadControl.enabled = true;
        }
      }
    }, "luka-crash-upload");
    worker.setDaemon(true);
    worker.start();
  }

  private static String sendHaste(String text) throws Exception {
    String response = request("https://hst.sh/documents", "text/plain; charset=utf-8", text.getBytes(StandardCharsets.UTF_8));
    Matcher match = HASTE_KEY.matcher(response);
    if (!match.find()) throw new IllegalStateException("no key");
    return "https://hst.sh/" + match.group(1);
  }

  private static String sendMclogs(String text) throws Exception {
    byte[] encoded = ("content=" + URLEncoder.encode(text, "UTF-8")).getBytes(StandardCharsets.UTF_8);
    Matcher match = MCLOGS_URL.matcher(request("https://api.mclo.gs/1/log", "application/x-www-form-urlencoded", encoded));
    if (!match.find()) throw new IllegalStateException("no url");
    return match.group(1).replace("\\/", "/");
  }

  private static String request(String endpoint, String contentType, byte[] payload) throws Exception {
    HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
    connection.setConnectTimeout(10_000);
    connection.setReadTimeout(30_000);
    connection.setRequestMethod("POST");
    connection.setDoOutput(true);
    connection.setRequestProperty("Content-Type", contentType);
    connection.setRequestProperty("User-Agent", "luka-client");
    try {
      OutputStream output = connection.getOutputStream();
      try { output.write(payload); } finally { output.close(); }
      InputStream input = connection.getInputStream();
      try {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        for (int read = input.read(chunk); read >= 0; read = input.read(chunk))
          if (read != 0) bytes.write(chunk, 0, read);
        return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
      } finally { input.close(); }
    } finally { connection.disconnect(); }
  }
}

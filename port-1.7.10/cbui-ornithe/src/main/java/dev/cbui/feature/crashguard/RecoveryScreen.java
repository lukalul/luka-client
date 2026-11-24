package dev.cbui.feature.crashguard;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.crash.CrashReport;

/** The screen shown after a caught crash: what happened, where the report is, and four ways on. */
public final class RecoveryScreen extends GuiScreen {
  private static final int TITLE = 0xFF5555;
  private static final int GREY = 0xAAAAAA;
  private static final int WHITE = 0xFFFFFF;
  private static final int DARK_GREY = 0x555555;
  private static final int ID_TITLE = 0, ID_COPY = 1, ID_SHARE = 2, ID_QUIT = 3;
  private static final int BUTTON_W = 220, BUTTON_STEP = 24;

  private final String reason;
  private final String errorLine;
  private final List<String> stack = new ArrayList<String>();
  private final String fileLine;
  private final String reportText;
  private final String site;
  private final int caught;
  private final int catchable;

  private String status;
  private int statusColour = WHITE;
  private boolean copied;
  private volatile String uploadLink;
  private volatile String uploadFailure;
  private volatile boolean uploading;

  private GuiButton copyButton;
  private GuiButton shareButton;
  private final List<String> textLines = new ArrayList<String>();
  private final List<Integer> textColours = new ArrayList<Integer>();
  private final List<String> introLines = new ArrayList<String>();
  private final List<String> footLines = new ArrayList<String>();

  RecoveryScreen(CrashReport report, Throwable error, String reportText, File file, int caught, int catchable, String site) {
    String description = null;
    try {
      description = report.getDescription();
    } catch (Throwable ignored) {
      // keep the screen working with what is known
    }
    this.reason = "Reason: " + (description == null ? "unknown" : description);
    Throwable deepest = error != null ? error : safeCause(report);
    while (deepest != null && deepest.getCause() != null && deepest.getCause() != deepest) deepest = deepest.getCause();
    if (deepest != null) {
      String message = deepest.getMessage();
      this.errorLine = deepest.getClass().getName() + (message == null ? "" : ": " + message);
      StackTraceElement[] trace = deepest.getStackTrace();
      for (int i = 0; i < trace.length && i < 5; i++) stack.add("  at " + trace[i]);
    } else {
      this.errorLine = "No error details were recorded.";
    }
    this.fileLine = file != null ? "Report file: " + file.getAbsolutePath() : "The report could not be written to disk.";
    this.reportText = reportText;
    this.site = site;
    this.caught = caught;
    this.catchable = catchable;
  }

  private static Throwable safeCause(CrashReport report) {
    try {
      return report.getCrashCause();
    } catch (Throwable ignored) {
      return null;
    }
  }

  @Override
  public void initGui() {
    buttonList.clear();
    int x = width / 2 - BUTTON_W / 2;
    int y = height - 4 * BUTTON_STEP - 6;
    buttonList.add(new GuiButton(ID_TITLE, x, y, BUTTON_W, 20, "Return to title screen"));
    copyButton = new GuiButton(ID_COPY, x, y + BUTTON_STEP, BUTTON_W, 20, copied ? "Copied" : "Copy report to clipboard");
    buttonList.add(copyButton);
    shareButton = new GuiButton(ID_SHARE, x, y + 2 * BUTTON_STEP, BUTTON_W, 20, "Share report online (" + site + ")");
    shareButton.enabled = !uploading;
    buttonList.add(shareButton);
    buttonList.add(new GuiButton(ID_QUIT, x, y + 3 * BUTTON_STEP, BUTTON_W, 20, "Close the game"));
    layoutText();
  }

  private void layoutText() {
    int wrap = Math.max(100, width - 40);
    introLines.clear();
    introLines.addAll(wrap("luka stopped the crash so you can save your progress. Going back to the title screen is the safest choice.", wrap));
    textLines.clear();
    textColours.clear();
    addText(reason, WHITE, wrap);
    addText(errorLine, WHITE, wrap);
    for (String line : stack) addText(line, GREY, wrap);
    addText("", WHITE, wrap);
    addText(fileLine, WHITE, wrap);
    if (status != null) addText(status, statusColour, wrap);
    footLines.clear();
    footLines.addAll(wrap("Crashes caught this session: " + caught + " of " + catchable
        + ". At the limit, or after several crashes within a few seconds, the game closes normally.", wrap));
  }

  @SuppressWarnings("unchecked")
  private List<String> wrap(String text, int width) {
    if (text.isEmpty()) {
      List<String> blank = new ArrayList<String>();
      blank.add("");
      return blank;
    }
    return fontRendererObj.listFormattedStringToWidth(text, width);
  }

  private void addText(String text, int colour, int wrap) {
    for (String line : wrap(text, wrap)) {
      textLines.add(line);
      textColours.add(colour);
    }
  }

  @Override
  public void updateScreen() {
    String link = uploadLink;
    if (link != null) {
      uploadLink = null;
      setClipboardString(link);
      status = "Uploaded and copied to the clipboard: " + link;
      statusColour = 0x55FF55;
      if (shareButton != null) shareButton.enabled = true;
      layoutText();
    }
    String failure = uploadFailure;
    if (failure != null) {
      uploadFailure = null;
      status = "The upload failed (" + failure + ").";
      statusColour = 0xFF5555;
      if (shareButton != null) shareButton.enabled = true;
      layoutText();
    }
  }

  @Override
  protected void actionPerformed(GuiButton button) {
    if (button.id == ID_TITLE) {
      backToTitle();
    } else if (button.id == ID_COPY) {
      setClipboardString(reportText);
      copied = true;
      button.displayString = "Copied";
    } else if (button.id == ID_SHARE) {
      share();
    } else if (button.id == ID_QUIT) {
      mc.shutdown();
    }
  }

  private void backToTitle() {
    CrashRecovery.leaveWorld(mc);
    mc.displayGuiScreen(new GuiMainMenu());
  }

  private void share() {
    if (uploading) return;
    uploading = true;
    shareButton.enabled = false;
    status = "Uploading the report to " + site + "...";
    statusColour = 0xFFFF55;
    layoutText();
    final String text = reportText;
    final String target = site;
    Thread upload = new Thread(() -> {
      try {
        uploadLink = PasteSites.upload(target, text);
      } catch (Throwable error) {
        uploadFailure = error.getClass().getSimpleName();
      } finally {
        uploading = false;
      }
    }, "luka crash report upload");
    upload.setDaemon(true);
    upload.start();
  }

  @Override
  protected void keyTyped(char typedChar, int keyCode) {
    if (keyCode == 1) backToTitle();
  }

  @Override
  public void drawScreen(int mouseX, int mouseY, float partialTicks) {
    drawDefaultBackground();
    int center = width / 2;
    drawCenteredString(fontRendererObj, "Something went wrong", center, 16, TITLE);
    int y = 32;
    for (String line : introLines) {
      drawCenteredString(fontRendererObj, line, center, y, GREY);
      y += 10;
    }
    y += 8;
    int buttonsTop = height - 4 * BUTTON_STEP - 6;
    int footTop = buttonsTop - 6 - footLines.size() * 10;
    for (int i = 0; i < textLines.size() && y + 9 <= footTop - 4; i++) {
      drawString(fontRendererObj, textLines.get(i), 20, y, textColours.get(i));
      y += 10;
    }
    int fy = footTop;
    for (String line : footLines) {
      drawCenteredString(fontRendererObj, line, center, fy, DARK_GREY);
      fy += 10;
    }
    super.drawScreen(mouseX, mouseY, partialTicks);
  }
}

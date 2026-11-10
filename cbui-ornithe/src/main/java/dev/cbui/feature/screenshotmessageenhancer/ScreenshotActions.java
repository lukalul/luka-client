package dev.cbui.feature.screenshotmessageenhancer;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageOutputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

public final class ScreenshotActions {
  public static final String PREFIX = "/luka_sme ";
  private static final long DELETE_WINDOW = 10_000L;
  private static final int HISTORY_LIMIT = 100;
  private static final Object LOCK = new Object();
  private static final LinkedHashMap<Integer, File> HISTORY = new LinkedHashMap<>();
  private static final Map<Integer, Long> DELETE_DEADLINES = new LinkedHashMap<>();
  private static final ExecutorService IO = Executors.newSingleThreadExecutor(task -> {
    Thread thread = new Thread(task, "luka-screenshot-io");
    thread.setDaemon(true);
    return thread;
  });
  private static int issuedToken;

  private ScreenshotActions() { }

  /** Encoding the PNG is the slow part of a screenshot, so it happens here instead of on the game thread. Jobs that need the file queue up behind it. */
  public static boolean writeInBackground(java.awt.image.RenderedImage image, String format, File file) throws IOException {
    file.createNewFile(); // claims the name, so a second screenshot in the same second gets its own file
    IO.execute(() -> {
      try {
        ImageIO.write(image, format, file);
      } catch (Throwable failure) {
        notifyUser("Could not save the screenshot: " + failure, EnumChatFormatting.RED);
      }
    });
    return true;
  }

  public static File findSavedFile(IChatComponent vanillaMessage) {
    if (!(vanillaMessage instanceof ChatComponentTranslation)) return null;
    Object[] arguments = ((ChatComponentTranslation) vanillaMessage).getFormatArgs();
    for (Object argument : arguments) {
      if (argument instanceof IChatComponent) {
        ClickEvent click = ((IChatComponent) argument).getChatStyle().getChatClickEvent();
        if (click != null && click.getAction() == ClickEvent.Action.OPEN_FILE) return new File(click.getValue());
      }
    }
    return null;
  }

  public static IChatComponent createMessage(ScreenshotMessageEnhancerModule module, File screenshot) {
    int token = remember(screenshot);
    ChatComponentText root = new ChatComponentText("Saved screenshot");
    if (module.showName.get()) appendName(root, screenshot);
    EnumSet<Action> visible = EnumSet.noneOf(Action.class);
    if (module.showCopyButton.get()) visible.add(Action.COPY);
    if (module.showOpenButton.get()) visible.add(Action.OPEN);
    if (module.showOpenFolderButton.get()) visible.add(Action.FOLDER);
    if (module.showDeleteButton.get()) visible.add(Action.DELETE);
    if (module.showUploadButton.get()) visible.add(Action.UPLOAD);
    for (Action action : Action.values()) if (visible.contains(action)) appendAction(root, action, token, screenshot);

    boolean copyImage = module.autoCopy.get();
    boolean optimize = module.compress.get();
    if (copyImage || optimize) IO.execute(() -> runPostSaveJobs(screenshot, copyImage, optimize));
    return root;
  }

  private static int remember(File file) {
    synchronized (LOCK) {
      int token = ++issuedToken;
      HISTORY.put(token, file);
      while (HISTORY.size() > HISTORY_LIMIT) HISTORY.remove(HISTORY.keySet().iterator().next());
      return token;
    }
  }

  private static void appendName(IChatComponent root, File file) {
    ChatComponentText name = new ChatComponentText(file.getName());
    name.setChatStyle(new ChatStyle().setUnderlined(true).setChatClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, file.getAbsolutePath())));
    root.appendText(" as ").appendSibling(name);
  }

  private static void appendAction(IChatComponent root, Action action, int token, File file) {
    ChatComponentText item = new ChatComponentText(action.label);
    item.setChatStyle(new ChatStyle().setColor(action.color).setBold(true)
        .setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, PREFIX + action.command + " " + token))
        .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ChatComponentText(action.hover(file)))));
    root.appendText(" ").appendSibling(item);
  }

  public static boolean accepts(String chat) { return chat != null && chat.startsWith(PREFIX); }

  public static void execute(String chat) {
    try {
      Request request = Request.parse(chat);
      if (request == null) return;
      ScreenshotMessageEnhancerModule module = ScreenshotMessageEnhancerModule.lookup(false);
      if (module == null) return;
      File file;
      synchronized (LOCK) { file = HISTORY.get(request.token); }
      if (file == null) {
        notifyUser("That screenshot is no longer known to the client.", EnumChatFormatting.GOLD);
        return;
      }
      request.action.perform(module, request.token, file);
    } catch (Throwable ignored) {
      // A local chat action must never interfere with ordinary chat handling.
    }
  }

  private static boolean requireFile(File file) {
    if (file.isFile()) return true;
    notifyUser("Screenshot file not found.", EnumChatFormatting.GOLD);
    return false;
  }

  private static void openTarget(File target) {
    try {
      if (!GraphicsEnvironment.isHeadless() && Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
        Desktop.getDesktop().open(target);
        return;
      }
      String os = System.getProperty("os.name", "").toLowerCase();
      ProcessBuilder command = os.contains("win")
          ? new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", target.getAbsolutePath())
          : os.contains("mac") ? new ProcessBuilder("open", target.getAbsolutePath()) : new ProcessBuilder("xdg-open", target.getAbsolutePath());
      command.start();
    } catch (Throwable failure) { notifyUser("Couldn't open " + target.getName(), EnumChatFormatting.RED); }
  }

  private static void requestDelete(ScreenshotMessageEnhancerModule module, int token, File file) {
    if (module.confirmDelete.get()) {
      long now = System.currentTimeMillis();
      boolean confirmed;
      synchronized (LOCK) {
        Long deadline = DELETE_DEADLINES.get(token);
        confirmed = deadline != null && now <= deadline;
        if (confirmed) DELETE_DEADLINES.remove(token); else DELETE_DEADLINES.put(token, now + DELETE_WINDOW);
      }
      if (!confirmed) { showDeleteConfirmation(token, file); return; }
    }
    IO.execute(() -> {
      boolean deleted = file.isFile() && file.delete();
      notifyUser(deleted ? "Screenshot deleted!" : "Couldn't delete screenshot (File not found)",
          deleted ? EnumChatFormatting.RED : EnumChatFormatting.GOLD);
    });
  }

  private static void showDeleteConfirmation(int token, File file) {
    ChatComponentText line = new ChatComponentText("Click again within 10 seconds to delete " + file.getName() + " ");
    line.getChatStyle().setColor(EnumChatFormatting.GOLD);
    ChatComponentText confirm = new ChatComponentText("[CONFIRM DELETE]");
    confirm.setChatStyle(new ChatStyle().setColor(EnumChatFormatting.RED).setBold(true)
        .setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, PREFIX + "delete " + token)));
    line.appendSibling(confirm);
    post(line);
  }

  private static void runPostSaveJobs(File file, boolean copyImage, boolean optimize) {
    if (copyImage) copyImage(file);
    if (optimize) optimizePng(file);
  }

  private static void copyImage(File file) {
    try {
      BufferedImage image = ImageIO.read(file);
      if (image == null) throw new IOException("unreadable image");
      boolean copied = copyImageOnWindows(file) || copyImageOnLinux(file);
      if (!copied && !GraphicsEnvironment.isHeadless()) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new ImageTransfer(image), null);
        copied = true;
      }
      if (!copied) throw new IOException("no clipboard available");
      notifyUser("Screenshot copied to clipboard!", EnumChatFormatting.GREEN);
    } catch (Throwable failure) { notifyUser("Could not copy the screenshot: " + failure, EnumChatFormatting.RED); }
  }

  private static boolean copyImageOnWindows(File file) {
    if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return false;
    try {
      String script = "Add-Type -AssemblyName System.Windows.Forms,System.Drawing; $p=[System.Drawing.Image]::FromFile($env:LUKA_IMAGE); try {[System.Windows.Forms.Clipboard]::SetImage($p)} finally {$p.Dispose()}";
      ProcessBuilder builder = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-STA", "-WindowStyle", "Hidden", "-Command", script);
      builder.environment().put("LUKA_IMAGE", file.getAbsolutePath());
      builder.redirectErrorStream(true);
      Process process = builder.start();
      process.getOutputStream().close();
      if (!process.waitFor(20, TimeUnit.SECONDS)) { process.destroyForcibly(); return false; }
      return process.exitValue() == 0;
    } catch (Throwable ignored) { return false; }
  }

  /** Linux: wl-copy on Wayland, xclip on X11 (whichever is installed), fed the PNG file. */
  private static boolean copyImageOnLinux(File file) {
    String os = System.getProperty("os.name", "").toLowerCase();
    if (os.contains("win") || os.contains("mac")) return false;
    boolean wayland = System.getenv("WAYLAND_DISPLAY") != null;
    String[][] tools = wayland
        ? new String[][] {{"wl-copy", "--type", "image/png"}, {"xclip", "-selection", "clipboard", "-t", "image/png", "-i"}}
        : new String[][] {{"xclip", "-selection", "clipboard", "-t", "image/png", "-i"}, {"wl-copy", "--type", "image/png"}};
    for (String[] tool : tools) {
      try {
        ProcessBuilder builder = new ProcessBuilder(tool);
        builder.redirectInput(file);
        builder.redirectErrorStream(true);
        builder.redirectOutput(new File("/dev/null"));
        Process process = builder.start();
        // both tools keep running to serve the clipboard: a quick exit with an error means it did not work
        if (process.waitFor(2, TimeUnit.SECONDS) && process.exitValue() != 0) continue;
        return true;
      } catch (Throwable ignored) {
        // not installed: try the next one
      }
    }
    return false;
  }

  private static void optimizePng(File original) {
    File candidate = null;
    ImageWriter writer = null;
    try {
      BufferedImage pixels = ImageIO.read(original);
      if (pixels == null) return;
      Iterator<ImageWriter> available = ImageIO.getImageWritersByFormatName("png");
      if (!available.hasNext()) return;
      writer = available.next();
      candidate = File.createTempFile("luka_png_", ".tmp", original.getParentFile());
      ImageWriteParam options = writer.getDefaultWriteParam();
      if (options.canWriteCompressed()) { options.setCompressionMode(ImageWriteParam.MODE_EXPLICIT); options.setCompressionQuality(.4f); }
      try (FileImageOutputStream output = new FileImageOutputStream(candidate)) {
        writer.setOutput(output);
        writer.write(null, new IIOImage(pixels, null, null), options);
      }
      if (candidate.length() > 0 && candidate.length() < original.length())
        Files.move(candidate.toPath(), original.toPath(), StandardCopyOption.REPLACE_EXISTING);
    } catch (Throwable ignored) { }
    finally { if (writer != null) writer.dispose(); if (candidate != null && candidate.exists()) candidate.delete(); }
  }

  private static void upload(File file) {
    notifyUser("Uploading screenshot...", EnumChatFormatting.YELLOW);
    IO.execute(() -> uploadNow(file));
  }

  private static void uploadNow(File file) {
    HttpURLConnection connection = null;
    try {
      String boundary = "----luka" + Long.toHexString(System.nanoTime());
      connection = (HttpURLConnection) new URL("https://catbox.moe/user/api.php").openConnection();
      connection.setConnectTimeout(10_000); connection.setReadTimeout(60_000); connection.setDoOutput(true);
      connection.setRequestMethod("POST"); connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
      connection.setRequestProperty("User-Agent", "luka-client");
      try (OutputStream output = connection.getOutputStream()) {
        writeUtf8(output, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"reqtype\"\r\n\r\nfileupload\r\n");
        writeUtf8(output, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"fileToUpload\"; filename=\"" + file.getName() + "\"\r\nContent-Type: image/png\r\n\r\n");
        Files.copy(file.toPath(), output);
        writeUtf8(output, "\r\n--" + boundary + "--\r\n");
      }
      String link;
      try (InputStream input = connection.getInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
        byte[] block = new byte[4096];
        for (int count; (count = input.read(block)) != -1;) bytes.write(block, 0, count);
        link = new String(bytes.toByteArray(), StandardCharsets.UTF_8).trim();
      }
      if (!link.startsWith("https://")) throw new IOException("unexpected answer");
      showUploaded(link);
    } catch (Throwable failure) { notifyUser("Failed to upload screenshot!", EnumChatFormatting.RED); }
    finally { if (connection != null) connection.disconnect(); }
  }

  private static void writeUtf8(OutputStream output, String text) throws IOException { output.write(text.getBytes(StandardCharsets.UTF_8)); }

  private static void showUploaded(String link) {
    Minecraft.getMinecraft().addScheduledTask(() -> {
      try { net.minecraft.client.gui.GuiScreen.setClipboardString(link); } catch (Throwable ignored) { }
      ChatComponentText message = new ChatComponentText("Screenshot uploaded + link copied to clipboard! ");
      message.getChatStyle().setColor(EnumChatFormatting.YELLOW);
      ChatComponentText url = new ChatComponentText(link);
      url.setChatStyle(new ChatStyle().setColor(EnumChatFormatting.AQUA).setUnderlined(true).setChatClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, link)));
      message.appendSibling(url);
      print(message);
    });
  }

  private static void notifyUser(String text, EnumChatFormatting color) {
    ChatComponentText message = new ChatComponentText(text);
    message.getChatStyle().setColor(color);
    post(message);
  }

  private static void post(IChatComponent component) {
    try {
      Minecraft game = Minecraft.getMinecraft();
      if (game.isCallingFromMinecraftThread()) print(component); else game.addScheduledTask(() -> print(component));
    } catch (Throwable ignored) { }
  }

  private static void print(IChatComponent component) {
    Minecraft game = Minecraft.getMinecraft();
    if (game.ingameGUI != null) game.ingameGUI.getChatGUI().printChatMessage(component);
  }

  private enum Action {
    COPY("copy", "[COPY]", EnumChatFormatting.BLUE) {
      @Override void perform(ScreenshotMessageEnhancerModule module, int token, File file) { if (requireFile(file)) IO.execute(() -> copyImage(file)); }
      @Override String hover(File file) { return "Copy the screenshot"; }
    },
    OPEN("open", "[OPEN]", EnumChatFormatting.GREEN) {
      @Override void perform(ScreenshotMessageEnhancerModule module, int token, File file) { if (requireFile(file)) openTarget(file); }
      @Override String hover(File file) { return "Open " + file.getName(); }
    },
    FOLDER("folder", "[OPEN FOLDER]", EnumChatFormatting.GOLD) {
      @Override void perform(ScreenshotMessageEnhancerModule module, int token, File file) {
        File parent = file.getParentFile();
        if (parent != null && (parent.isDirectory() || parent.mkdirs())) openTarget(parent);
      }
      @Override String hover(File file) { return file.getParent() == null ? "" : file.getParent(); }
    },
    DELETE("delete", "[DELETE]", EnumChatFormatting.RED) {
      @Override void perform(ScreenshotMessageEnhancerModule module, int token, File file) { requestDelete(module, token, file); }
      @Override String hover(File file) { return "Delete the screenshot"; }
    },
    UPLOAD("upload", "[UPLOAD]", EnumChatFormatting.YELLOW) {
      @Override void perform(ScreenshotMessageEnhancerModule module, int token, File file) { if (requireFile(file)) upload(file); }
      @Override String hover(File file) { return "Upload the screenshot"; }
    };
    private final String command, label;
    private final EnumChatFormatting color;
    Action(String command, String label, EnumChatFormatting color) { this.command = command; this.label = label; this.color = color; }
    abstract void perform(ScreenshotMessageEnhancerModule module, int token, File file);
    abstract String hover(File file);
    static Action named(String command) { for (Action action : values()) if (action.command.equals(command)) return action; return null; }
  }

  private static final class Request {
    private final Action action;
    private final int token;
    private Request(Action action, int token) { this.action = action; this.token = token; }
    private static Request parse(String chat) {
      if (!accepts(chat)) return null;
      String[] fields = chat.substring(PREFIX.length()).trim().split("\\s+");
      if (fields.length != 2) return null;
      Action action = Action.named(fields[0]);
      if (action == null) return null;
      try { return new Request(action, Integer.parseInt(fields[1])); }
      catch (NumberFormatException ignored) { return null; }
    }
  }

  private static final class ImageTransfer implements Transferable {
    private final BufferedImage image;
    private ImageTransfer(BufferedImage image) { this.image = image; }
    @Override public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[] {DataFlavor.imageFlavor}; }
    @Override public boolean isDataFlavorSupported(DataFlavor flavor) { return DataFlavor.imageFlavor.equals(flavor); }
    @Override public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
      if (!isDataFlavorSupported(flavor)) throw new UnsupportedFlavorException(flavor);
      return image;
    }
  }
}

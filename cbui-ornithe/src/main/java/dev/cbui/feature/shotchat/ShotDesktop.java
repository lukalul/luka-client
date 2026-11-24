package dev.cbui.feature.shotchat;

import java.awt.Desktop;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

/**
 * Opening files and folders with the desktop, and putting a picture (not its path) on the system clipboard. Java's
 * own clipboard is unreliable for images from inside the game, so the operating system's tools are used where they
 * exist: PowerShell on Windows, wl-copy or xclip on Linux; Java's clipboard is the last resort. Blocking: call these
 * from a background thread.
 */
final class ShotDesktop {
  /** Puts the PNG named by the environment variable LUKA_SHOT_FILE on the Windows clipboard, as a bitmap and as PNG data. */
  private static final String WINDOWS_COPY =
      "$ErrorActionPreference = 'Stop'\n"
          + "Add-Type -AssemblyName System.Windows.Forms\n"
          + "Add-Type -AssemblyName System.Drawing\n"
          + "$path = $env:LUKA_SHOT_FILE\n"
          + "$bytes = [System.IO.File]::ReadAllBytes($path)\n"
          + "$png = New-Object System.IO.MemoryStream(,$bytes)\n"
          + "$picture = [System.Drawing.Image]::FromStream($png)\n"
          + "$data = New-Object System.Windows.Forms.DataObject\n"
          + "$data.SetImage($picture)\n"
          + "$data.SetData('PNG', $false, (New-Object System.IO.MemoryStream(,$bytes)))\n"
          + "[System.Windows.Forms.Clipboard]::SetDataObject($data, $true)\n"
          + "$picture.Dispose()\n";

  private ShotDesktop() {}

  private static String os() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
  }

  /** Opens a file or folder with the system's default program. */
  static void open(File target) throws IOException {
    String os = os();
    if (os.contains("mac")) {
      new ProcessBuilder("open", target.getAbsolutePath()).start();
      return;
    }
    try {
      if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
        Desktop.getDesktop().open(target);
        return;
      }
    } catch (Throwable desktopFailed) {
      // fall back to the operating system's own opener
    }
    if (os.contains("win")) new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", target.getAbsolutePath()).start();
    else new ProcessBuilder("xdg-open", target.getAbsolutePath()).start();
  }

  /** Copies the picture itself to the clipboard. */
  static void copyImage(File file) throws Exception {
    String os = os();
    if (os.contains("win")) {
      windows(file);
      return;
    }
    if (!os.contains("mac") && linux(file)) return;
    javaClipboard(file);
  }

  private static void windows(File file) throws IOException, InterruptedException {
    String encoded = Base64.getEncoder().encodeToString(WINDOWS_COPY.getBytes(StandardCharsets.UTF_16LE));
    ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-STA",
        "-WindowStyle", "Hidden", "-ExecutionPolicy", "Bypass", "-EncodedCommand", encoded);
    pb.environment().put("LUKA_SHOT_FILE", file.getAbsolutePath());
    pb.redirectErrorStream(true);
    Process process = pb.start();
    process.getOutputStream().close();
    String output = readSome(process.getInputStream());
    if (!process.waitFor(30, TimeUnit.SECONDS)) {
      process.destroy();
      throw new IOException("PowerShell did not finish");
    }
    if (process.exitValue() != 0) throw new IOException(lastLine(output, "PowerShell could not copy the picture"));
  }

  /** wl-copy on Wayland, xclip on X11, each trying the other when one is missing. False when neither worked. */
  private static boolean linux(File file) throws InterruptedException {
    boolean wayland = System.getenv("WAYLAND_DISPLAY") != null;
    for (int attempt = 0; attempt < 2; attempt++) {
      boolean useWayland = (attempt == 0) == wayland;
      ProcessBuilder pb = useWayland
          ? new ProcessBuilder("wl-copy", "--type", "image/png")
          : new ProcessBuilder("xclip", "-selection", "clipboard", "-t", "image/png", "-i", file.getAbsolutePath());
      if (useWayland) pb.redirectInput(file);
      File nowhere = new File("/dev/null");
      pb.redirectOutput(nowhere);
      pb.redirectError(nowhere);
      try {
        Process process = pb.start();
        if (!process.waitFor(10, TimeUnit.SECONDS)) return true; // still running: it is serving the clipboard
        if (process.exitValue() == 0) return true;
      } catch (IOException missing) {
        // this tool is not installed; try the other one
      }
    }
    return false;
  }

  private static void javaClipboard(File file) throws IOException {
    BufferedImage picture = ImageIO.read(file);
    if (picture == null) throw new IOException("not a readable picture");
    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new PictureOnClipboard(picture), null);
  }

  private static String readSome(InputStream in) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buf = new byte[1024];
    for (int n; (n = in.read(buf)) > 0; ) if (out.size() < 16384) out.write(buf, 0, n);
    return new String(out.toByteArray(), StandardCharsets.UTF_8);
  }

  private static String lastLine(String text, String fallback) {
    String[] lines = text.trim().split("\\r?\\n");
    for (int i = lines.length - 1; i >= 0; i--) if (!lines[i].trim().isEmpty()) return lines[i].trim();
    return fallback;
  }

  /** A picture offered to Java's clipboard. */
  private static final class PictureOnClipboard implements Transferable {
    private final Image picture;

    PictureOnClipboard(Image picture) {
      this.picture = picture;
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
      return new DataFlavor[] {DataFlavor.imageFlavor};
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
      return DataFlavor.imageFlavor.equals(flavor);
    }

    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
      if (!isDataFlavorSupported(flavor)) throw new UnsupportedFlavorException(flavor);
      return picture;
    }
  }
}

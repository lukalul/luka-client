package dev.luka.launcher;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.text.DefaultCaret;

/**
 * The luka launcher window. The layout follows a classic game launcher (borderless 1152x648 window, header
 * with logo/tabs/account, big launch button, News/Servers/Settings/About pages, side panels, footer, account and login
 * screens) in luka's purple and black. Everything is painted by hand, so every position below is in window pixels.
 */
final class LauncherFrame extends JFrame {
  static final int W = 1152, H = 648;
  static final Color BG = new Color(0x0F0C14);
  static final Color PANEL = new Color(0x17121F);
  static final Color PANEL_LINE = new Color(0x2A2235);
  static final Color PURPLE = new Color(0x9D4EDD);
  static final Color PURPLE_DARK = new Color(0x6C36A9);
  static final Color LIGHT = new Color(0xD9C2FF);
  static final Color TEXT = new Color(0xEFEFEF);
  static final Color MUTED = new Color(0x8C849A);
  static final Color FAINT = new Color(0x5B5368);

  private static final String[] TABS = {"HOME", "SERVERS", "SETTINGS", "ABOUT"};
  private static final String[][] SERVERS = {
    {"ElevateMC", "play.elevatemc.com", "PotPvP and teamfights"},
    {"MineMen", "eu.minemen.club", "Practice and more"},
    {"Hypixel", "mc.hypixel.net", "The big network"}
  };
  private static final String[] LAUNCH_OPTIONS = {"keep", "hide", "close"};
  private static final String[] LAUNCH_LABELS = {"KEEP OPEN", "HIDE LAUNCHER", "CLOSE LAUNCHER"};
  /** Public source of the Vulkan renderer (AGPL-3.0-only); the About page links to it. */
  static final String VULKAN_SOURCE_URL = "https://github.com/lukalul/luka-vulkan";
  static final String VERSION = "1.0.0"; // package-visible: GameRunner reports it with launch telemetry

  private final File root;
  private final Settings settings;
  private final Accounts accounts;
  private final BufferedImage logo = load("/logo.png"), logoInner = load("/logo_inner.png"), headerImage = load("/header.png");
  private final Font regular, bold;
  private final ServerPing[] pings = new ServerPing[SERVERS.length];

  private int tab;
  private int overlay; // 0 none, 1 accounts, 2 sign in, 3 what's new
  private int hover = -1;
  private volatile String status = "Ready";
  private volatile double fraction;
  private volatile boolean running;
  private volatile GameRunner activeRunner;
  private volatile String updateStatus = "Updates off";
  private volatile String syncStatus = "";
  private String loginMessage = "";
  private String deviceCode = "", deviceUrl = "";
  private volatile long copiedAt;
  private volatile boolean loginCancelled;
  private boolean draggingSlider;
  private Point dragStart;

  private final Stage stage = new Stage();
  private final JTextField nameField = new JTextField();
  private final JTextArea logArea = new JTextArea();
  private final JScrollPane logScroll;
  private boolean showLog;
  /** Log lines from other threads (the game prints thousands), added to the log box in batches by {@link #tick}. */
  private final java.util.concurrent.ConcurrentLinkedQueue<String> pendingLog = new java.util.concurrent.ConcurrentLinkedQueue<>();
  private static final int MAX_LOG_CHARS = 200_000;
  private int ticks;
  /** The fixed part of the window (header picture and gradients), painted once instead of every frame. */
  private BufferedImage background;

  /** True when the game opened this window only to pick or add an account: it closes as soon as that is done. */
  private final boolean accountMode;

  LauncherFrame(File root, boolean accountMode) {
    super("luka");
    this.root = root;
    this.accountMode = accountMode;
    final File logFile = new File(root, "launcher.log");
    MicrosoftAuth.log = line -> {
      try {
        java.nio.file.Files.write(logFile.toPath(), (new java.util.Date() + "  " + line + System.lineSeparator()).getBytes(java.nio.charset.StandardCharsets.UTF_8),
            java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
      } catch (Exception ignored) {
        // logging is optional
      }
    };
    this.settings = new Settings(new File(root, "launcher.properties"));
    this.accounts = new Accounts(new File(root, "accounts.properties"));
    if (accounts.selected() == null) accounts.addOffline(settings.username());
    regular = font("/Roboto-Regular.ttf");
    bold = font("/Roboto-Bold.ttf");
    setUndecorated(true);
    setResizable(false);
    setBackground(new Color(0x0C0910));
    if (logo != null) setIconImage(logo);
    setDefaultCloseOperation(EXIT_ON_CLOSE);
    stage.setPreferredSize(new Dimension(W, H));
    stage.setLayout(null);
    setContentPane(stage);
    pack();
    setShape(new RoundRectangle2D.Float(0, 0, W, H, 8, 8));
    setLocationRelativeTo(null);

    nameField.setBounds(W / 2 - 141, 330, 283, 38);
    styleField(nameField);
    nameField.setVisible(false);
    stage.add(nameField);
    nameField.addActionListener(e -> offlineSignIn());

    logArea.setEditable(false);
    logArea.setBackground(new Color(0x0B0910));
    logArea.setForeground(new Color(0xBFB7CC));
    logArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
    ((DefaultCaret) logArea.getCaret()).setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
    logScroll = new JScrollPane(logArea);
    logScroll.setBorder(BorderFactory.createLineBorder(PANEL_LINE));
    logScroll.setBounds(24, 285, 744, 298);
    logScroll.setVisible(false);
    stage.add(logScroll);

    for (int i = 0; i < pings.length; i++) {
      final int index = i;
      pings[i] = ServerPing.offline();
      new Thread(() -> refreshPing(index), "ping-" + i).start();
    }
    if (accountMode) overlay = 1;
    Thread hw = new Thread(LauncherFrame::loadHardware, "hardware-info");
    hw.setDaemon(true);
    hw.start();
    javax.swing.Timer repaint = new javax.swing.Timer(40, e -> tick());
    repaint.start();
    addWindowFocusListener(new java.awt.event.WindowFocusListener() {
      public void windowGainedFocus(java.awt.event.WindowEvent e) { stage.repaint(); }
      public void windowLostFocus(java.awt.event.WindowEvent e) { stage.repaint(); }
    });
    startSync(false);
    checkUpdates();
  }

  // ---- actions ----

  /** In account mode the game is waiting for the account file: quit as soon as the choice is saved. */
  private void done() {
    if (accountMode) SwingUtilities.invokeLater(() -> System.exit(0));
  }

  private void refreshPing(int index) {
    String address = SERVERS[index][1];
    pings[index] = ServerPing.query(address, 25565);
    stage.repaint();
  }

  private void offlineSignIn() {
    String name = nameField.getText().trim().replaceAll("[^A-Za-z0-9_]", "");
    if (name.length() < 3) {
      loginMessage = "Pick a name of 3 to 16 letters, numbers or _";
      return;
    }
    accounts.addOffline(name.length() > 16 ? name.substring(0, 16) : name);
    nameField.setVisible(false);
    overlay = 0;
    loginMessage = "";
    done();
  }

  /** Opens the sign-in page with the code already in it; Desktop first, the system's own opener if that fails. */
  private void openBrowser(String address) {
    MicrosoftAuth.log.accept("opening " + address);
    try {
      Desktop.getDesktop().browse(new java.net.URI(address));
      return;
    } catch (Throwable first) {
      MicrosoftAuth.log.accept("Desktop.browse failed: " + first);
    }
    try {
      if (Platform.windows()) new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", address).start();
      else new ProcessBuilder("xdg-open", address).start();
    } catch (Throwable second) {
      MicrosoftAuth.log.accept((Platform.windows() ? "rundll32" : "xdg-open") + " failed: " + second);
    }
  }

  private void microsoftSignIn() {
    final MicrosoftAuth auth = new MicrosoftAuth(settings.msClientId(), settings.msLiveFlow());
    if (!auth.configured()) {
      loginMessage = "Microsoft sign-in is not available in this build yet. You can still play with a name.";
      return;
    }
    loginCancelled = false;
    deviceCode = "";
    loginMessage = "Contacting Microsoft";
    new Thread(
            () -> {
              try {
                MicrosoftAuth.Session session =
                    auth.signIn(
                        (code, url) -> {
                          deviceCode = code;
                          deviceUrl = url;
                          loginMessage = "Open " + url + " and enter the code";
                          try {
                            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(code), null);
                          } catch (Exception ignored) {
                            // the COPY button still works
                          }
                          openBrowser(settings.msLiveFlow() ? url + "?otc=" + code : url); // with the official app ID the page opens with the code filled in; our own Azure app is refused that shortcut
                        },
                        text -> loginMessage = text,
                        () -> loginCancelled);
                accounts.addMicrosoft(session.name, session.uuid, session.refreshToken);
                deviceCode = "";
                loginMessage = "";
                overlay = 0;
                done();
              } catch (Throwable e) {
                deviceCode = "";
                MicrosoftAuth.log.accept("sign-in failed: " + e);
                loginMessage = String.valueOf(e.getMessage());
              }
            },
            "ms-login")
        .start();
  }

  private void play(String server) {
    if (running) return;
    final Accounts.Account account = accounts.selected();
    if (account == null) {
      overlay = 1;
      return;
    }
    settings.save();
    running = true;
    logArea.setText("");
    new Thread(
            () -> {
              try {
                String updateUrl = settings.updateUrl();
                if (!updateUrl.isEmpty()) {
                  setStatus("Checking for updates", 0.005);
                  try {
                    Updater.check(root, updateUrl, VERSION, new Updater.Listener() {
                      public void status(String text) { updateStatus = text; }
                      public void clientId(String id) { }
                      public void restart() throws IOException { restartLauncher(); }
                    });
                  } catch (IOException offline) {
                    appendLog("Update check failed: " + offline.getMessage()); // Installer.syncMods decides whether luka may still start
                  }
                }
                GameVersion version = settings.version();
                Installer.retireMods(root, version);
                if ("vulkan".equals(settings.renderer()) && !vulkanDriverPresent()) {
                  settings.set("renderer", "normal");
                  settings.save();
                  appendLog("This PC has no Vulkan driver, using the Normal renderer");
                }
                if (!Installer.isInstalled(root, version)) {
                  Installer.install(root, version, this::setStatus);
                } else {
                  Installer.syncMods(root, version, false, this::setStatus);
                }
                GameRunner.Identity id = new GameRunner.Identity();
                id.name = account.name;
                id.uuid = account.uuid;
                id.server = server == null ? "" : server;
                if (account.microsoft()) {
                  setStatus("Signing in", 0.01);
                  MicrosoftAuth auth = new MicrosoftAuth(settings.msClientId(), settings.msLiveFlow());
                  if (!auth.configured()) throw new IOException("Microsoft sign-in is not available in this build yet");
                  MicrosoftAuth.Session session = auth.refresh(account.refreshToken, text -> setStatus(text, 0.01));
                  id.name = session.name;
                  id.uuid = session.uuid;
                  id.accessToken = session.accessToken;
                  id.userType = "msa";
                  account.name = session.name;
                  account.refreshToken = session.refreshToken;
                  accounts.save();
                }
                GameRunner runner = new GameRunner(root, settings, id, this::appendLog, this::setStatus);
                activeRunner = runner;
                long started = System.currentTimeMillis();
                int code = runner.play();
                if (code != 0 && "vulkan".equals(settings.renderer()) && System.currentTimeMillis() - started < 45000 && !gameGotGoing()) {
                  settings.set("renderer", "normal");
                  settings.save();
                  activeRunner = null;
                  SwingUtilities.invokeLater(() -> setVisible(true));
                  setStatus("Vulkan could not start on this PC, so luka switched to Normal. Press PLAY.", 0);
                  showLog = true;
                  return;
                }
                activeRunner = null;
                SwingUtilities.invokeLater(() -> setVisible(true));
                setStatus(code == 0 ? "Game closed" : "Game exited (code " + code + ") - see the log", 0);
                if (code != 0) showLog = true;
              } catch (Throwable t) {
                appendLog("ERROR: " + t);
                setStatus("Failed: " + t.getMessage(), 0);
                showLog = true;
                SwingUtilities.invokeLater(() -> setVisible(true));
              } finally {
                activeRunner = null;
                running = false;
                try { SettingsBackup.create(root); }
                catch (Exception e) { appendLog("Settings backup failed: " + e.getMessage()); }
                startSync(false);
              }
            },
            "luka-launch")
        .start();
  }

  /** The texture atlas is built after the window exists, so seeing it means Vulkan started and any later failure is something else. */
  private boolean gameGotGoing() {
    try {
      java.io.File log = new java.io.File(settings.version().home(root), "game/logs/latest.log");
      return log.isFile() && new String(java.nio.file.Files.readAllBytes(log.toPath()), java.nio.charset.StandardCharsets.ISO_8859_1).contains("Created: ");
    } catch (Exception unreadable) {
      return true;
    }
  }

  private static boolean vulkanDriverPresent() {
    if (Platform.windows()) {
      String windows = System.getenv("SystemRoot");
      return windows == null || new File(windows, "System32/vulkan-1.dll").isFile();
    }
    // the Vulkan loader, where the common distributions put it; an unknown layout is not held against the player
    String[] folders = {"/usr/lib/x86_64-linux-gnu", "/usr/lib64", "/usr/lib", "/lib/x86_64-linux-gnu", "/lib64", "/run/opengl-driver/lib"};
    boolean anyFolder = false;
    for (String folder : folders) {
      File dir = new File(folder);
      if (!dir.isDirectory()) continue;
      anyFolder = true;
      if (new File(dir, "libvulkan.so.1").exists()) return true;
    }
    return !anyFolder;
  }

  /** 1.8.9 or 1.7.10. The other version's files stay where they are, so switching back needs no download. */
  private void chooseVersion(GameVersion value) {
    if (running || value == settings.version()) return;
    // offered by the last list seen (fetched now or saved earlier), or already installed from an earlier run
    if (!Manifests.offers(value, root) && !new File(value.home(root), "installed.json").isFile()) {
      status = "luka for Minecraft " + value.id + " is not out yet.";
      fraction = 0;
      return;
    }
    settings.set("mcVersion", value.id);
    settings.save();
    status = "Minecraft " + value.id + " selected. Press PLAY to use it.";
    fraction = 0;
  }

  private void chooseRenderer(String value) {
    if (running || value.equals(settings.renderer())) return;
    settings.set("renderer", value);
    settings.save();
    status = "vulkan".equals(value)
        ? "Vulkan selected. Press PLAY to use it. Vulkan source code: see About."
        : "Normal selected. Press PLAY to use it.";
    fraction = 0;
  }

  private void repair() {
    if (running) return;
    running = true;
    logArea.setText("");
    new Thread(() -> {
      try {
        Installer.repair(root, settings.version(), this::setStatus);
      } catch (Throwable t) {
        appendLog("ERROR: " + t);
        setStatus("Repair failed: " + t.getMessage(), 0);
        showLog = true;
      } finally {
        running = false;
      }
    }, "luka-repair").start();
  }

  private void backUpNow() {
    new Thread(() -> {
      try { syncStatus = "Backed up to " + SettingsBackup.create(root).getName(); }
      catch (Exception e) { syncStatus = "Backup failed: " + e.getMessage(); }
    }, "settings-backup").start();
  }

  private void restoreBackup() {
    if (running) { syncStatus = "Close the game before restoring"; return; }
    List<File> backups = SettingsBackup.list(root);
    if (backups.isEmpty()) { syncStatus = "No backups found"; return; }
    Object choice = JOptionPane.showInputDialog(this, "Choose a backup to restore", "Restore settings",
        JOptionPane.PLAIN_MESSAGE, null, backups.toArray(), backups.get(0));
    if (!(choice instanceof File)) return;
    if (JOptionPane.showConfirmDialog(this, "Restore " + ((File) choice).getName() + "? A safety backup will be made first.",
        "Restore settings", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
    new Thread(() -> {
      try { SettingsBackup.create(root); SettingsBackup.restore(root, (File) choice); syncStatus = "Settings restored"; }
      catch (Exception e) { syncStatus = "Restore failed: " + e.getMessage(); }
    }, "settings-restore").start();
  }

  private void chooseSyncFolder() {
    if (running) { syncStatus = "Close the game before changing sync"; return; }
    JFileChooser chooser = new JFileChooser(settings.syncFolder().isEmpty() ? root.getParentFile() : new File(settings.syncFolder()));
    chooser.setDialogTitle("Choose sync folder");
    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      settings.set("syncFolder", chooser.getSelectedFile().getAbsolutePath());
      settings.save();
      startSync(true);
    }
  }

  private void startSync(boolean manual) {
    final String folder = settings.syncFolder();
    if (folder.isEmpty()) { if (manual) syncStatus = "Choose a sync folder first"; return; }
    GameRunner game = activeRunner;
    if (running || (game != null && game.isGameRunning())) { if (manual) syncStatus = "Sync waits until the game closes"; return; }
    new Thread(() -> {
      GameRunner current = activeRunner;
      if (running || (current != null && current.isGameRunning())) return;
      try {
        syncStatus = SettingsSync.sync(root, new File(folder), () -> {
          GameRunner launched = activeRunner;
          return !running && (launched == null || !launched.isGameRunning());
        }).toString();
      } catch (java.io.InterruptedIOException stopped) {
        syncStatus = "Sync stopped while game is running";
      } catch (Exception e) { syncStatus = "Sync failed: " + e.getMessage(); }
    }, "settings-sync").start();
  }

  /** Starts the freshly downloaded launcher and closes this one. The game is left alone if it is running. */
  private void restartLauncher() throws IOException {
    GameRunner game = activeRunner;
    if (game != null && game.isGameRunning()) return;
    String app = System.getProperty("luka.app");
    File dist = app == null ? root.getParentFile() : new File(app);
    List<String> command = new ArrayList<>();
    command.add(Platform.javaw(new File(System.getProperty("java.home"))).getPath());
    command.add("-Dluka.updated=1");
    command.add("-Dsun.java2d.d3d=false");
    command.add("-Dluka.home=" + root.getAbsolutePath());
    if (app != null) command.add("-Dluka.app=" + app);
    String manifest = System.getProperty("luka.manifest");
    if (manifest != null) command.add("-Dluka.manifest=" + manifest);
    command.add("-cp");
    command.add(new File(dist, "luka-boot.jar").getPath());
    command.add("dev.luka.launcher.Boot");
    new ProcessBuilder(command).directory(dist).start();
    System.exit(0);
  }

  private void checkUpdates() {
    final String url = settings.updateUrl();
    if (url.isEmpty()) { updateStatus = "Updates off"; return; }
    new Thread(() -> {
      try {
        Updater.check(root, url, VERSION, new Updater.Listener() {
          public void status(String text) { updateStatus = text; }
          public void clientId(String id) {
            if (settings.msClientId().isEmpty()) { settings.set("msClientId", id); settings.save(); }
          }
          public void restart() throws IOException { restartLauncher(); }
        });
        SwingUtilities.invokeLater(this::showNewRelease);
      } catch (Exception e) {
        updateStatus = "Update failed: " + e.getMessage();
        appendLog("Update failed: " + e);
      }
    }, "launcher-update").start();
  }

  /** Shows the release notes once after an update. A brand new install has nothing to compare, so it just remembers the version. */
  private void showNewRelease() {
    List<List<String>> releases = Manifests.changelog();
    if (releases.isEmpty()) return;
    String newest = releases.get(0).get(0), seen = settings.seenChangelog();
    if (newest.equals(seen)) return;
    settings.set("seenChangelog", newest);
    settings.save();
    if (!seen.isEmpty() && overlay == 0) overlay = 3;
  }

  private void appendLog(String line) {
    pendingLog.add(line);
  }

  /**
   * Every 40 ms on the Swing thread. Status, progress, pings and friends change on other threads, so the window
   * redraws on this clock, but only when someone can look at it: never while hidden or minimised, never while the game
   * runs and another window (the game) has focus, and 4 times a second when unfocused otherwise. Redrawing the whole
   * window 25 times a second while the game ran cost players hundreds of frames per second.
   */
  private void tick() {
    ticks++;
    if (!pendingLog.isEmpty() && (showLog || ticks % 25 == 0)) flushLog();
    if (!isShowing() || (getExtendedState() & JFrame.ICONIFIED) != 0) return;
    if (!isActive()) {
      GameRunner game = activeRunner;
      if (game != null && game.isGameRunning()) return;
      if (ticks % 6 != 0) return;
    }
    stage.repaint();
  }

  private void flushLog() {
    StringBuilder text = new StringBuilder();
    for (String line; (line = pendingLog.poll()) != null; ) text.append(line).append('\n');
    logArea.append(text.toString());
    javax.swing.text.Document doc = logArea.getDocument();
    int extra = doc.getLength() - MAX_LOG_CHARS;
    if (extra > 0) {
      try {
        int cut = logArea.getText(extra, Math.min(4096, doc.getLength() - extra)).indexOf('\n');
        doc.remove(0, extra + Math.max(cut + 1, 0));
      } catch (javax.swing.text.BadLocationException ignored) {
        // the length was just read on this thread; cannot happen
      }
    }
  }

  private void setStatus(String text, double f) {
    status = text;
    fraction = f;
    if ("Playing".equals(text)) {
      String mode = settings.onLaunch();
      if ("close".equals(mode)) {
        System.exit(0);
      } else if ("hide".equals(mode)) {
        SwingUtilities.invokeLater(() -> setVisible(false));
      }
    }
  }

  // ---- layout constants (window pixels) ----

  private static final int CENTER_X = 24, CENTER_Y = 222, CENTER_W = 744, CENTER_H = 361;
  private static final int FRIENDS_X = 792, FRIENDS_W = 231, QUICK_X = 1047, QUICK_W = 81;
  private static final int LAUNCH_X = 207, LAUNCH_Y = 104, LAUNCH_W = 378, LAUNCH_H = 66;

  private int[] tabBounds(int i, FontMetrics fm) {
    int x = 24 + 48 + 8;
    for (int k = 0; k < i; k++) x += 24 + fm.stringWidth(TABS[k]);
    return new int[] {x + 24, 22, fm.stringWidth(TABS[i]), 48};
  }

  // ---- painting and input ----

  private final class Stage extends JPanel {
    private final List<int[]> hits = new ArrayList<>(); // x, y, w, h, id
    private final List<Runnable> actions = new ArrayList<>();

    Stage() {
      setOpaque(false);
      MouseAdapter mouse =
          new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
              int id = hit(e.getX(), e.getY());
              if (id != hover) {
                hover = id;
                setCursor(Cursor.getPredefinedCursor(id >= 0 ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                repaint(); // the clock redraws an unfocused window rarely; hover should still show at once
              }
            }

            @Override
            public void mousePressed(MouseEvent e) {
              dragStart = null; // the window stays where it opens (centred); it used to drag from the top bar
              if (tab == 2 && overlay == 0 && !showLog && onSlider(e.getX(), e.getY())) {
                draggingSlider = true;
                setRam(e.getX());
              }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
              if (draggingSlider) setRam(e.getX());
              // no window dragging
            }

            @Override
            public void mouseReleased(MouseEvent e) {
              if (draggingSlider) {
                draggingSlider = false;
                settings.save();
                return;
              }
              dragStart = null;
            }

            @Override
            public void mouseClicked(MouseEvent e) {
              int id = hit(e.getX(), e.getY());
              if (id >= 0 && id < actions.size()) actions.get(id).run();
            }
          };
      addMouseListener(mouse);
      addMouseMotionListener(mouse);
    }

    private int hit(int x, int y) {
      for (int i = hits.size() - 1; i >= 0; i--) {
        int[] h = hits.get(i);
        if (x >= h[0] && y >= h[1] && x < h[0] + h[2] && y < h[1] + h[3]) return h[4];
      }
      return -1;
    }

    /** Registers a clickable rectangle for this frame. */
    private int area(int x, int y, int w, int h, Runnable action) {
      int id = actions.size();
      actions.add(action);
      hits.add(new int[] {x, y, w, h, id});
      return id;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      hits.clear();
      actions.clear();
      Graphics2D g = (Graphics2D) graphics.create();
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
      drawBackground(g);
      drawHeader(g);
      drawLaunch(g);
      drawRenderer(g);
      if (!showLog) drawPage(g);
      logScroll.setVisible(showLog && overlay == 0);
      drawSide(g);
      drawFooter(g);
      if (overlay != 0) drawOverlay(g);
      nameField.setVisible(overlay == 2);
      g.dispose();
    }

    private void drawBackground(Graphics2D target) {
      // at the screen's own scale (Windows display scaling), so the cached picture stays as sharp as before
      double sx = target.getTransform().getScaleX(), sy = target.getTransform().getScaleY();
      int pw = (int) Math.ceil(W * sx), ph = (int) Math.ceil(H * sy);
      if (background == null || background.getWidth() != pw || background.getHeight() != ph) {
        background = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = background.createGraphics();
        g.setRenderingHints(target.getRenderingHints());
        g.scale(sx, sy);
        paintBackground(g);
        g.dispose();
      }
      target.drawImage(background, 0, 0, W, H, null);
    }

    private void paintBackground(Graphics2D g) {
      g.setColor(BG);
      g.fillRect(0, 0, W, H);
      if (headerImage != null) {
        g.drawImage(headerImage, 0, -330, W, W, null);
      }
      g.setPaint(new GradientPaint(0, 0, new Color(15, 12, 20, 70), 0, 330, new Color(15, 12, 20, 255)));
      g.fillRect(0, 0, W, 330);
      g.setColor(BG);
      g.fillRect(0, 330, W, H - 330);
      g.setPaint(new java.awt.RadialGradientPaint(new java.awt.Point(W - 160, H + 40), 620f, new float[] {0f, 1f},
          new Color[] {new Color(0x9D, 0x4E, 0xDD, 46), new Color(0x9D, 0x4E, 0xDD, 0)}));
      g.fillRect(0, 0, W, H);
    }

    private void drawHeader(Graphics2D g) {
      if (logo != null) g.drawImage(logo, 24, 22, 48, 48, null);
      if (logoInner != null) g.drawImage(logoInner, 24 + 24 - 10, 22 + 24 - 10, 20, 20, null);
      g.setFont(bold.deriveFont(15f));
      FontMetrics fm = g.getFontMetrics();
      for (int i = 0; i < TABS.length; i++) {
        int[] b = tabBounds(i, fm);
        final int tabIndex = i;
        int id = area(b[0] - 12, b[1], b[2] + 24, b[3], () -> {
          tab = tabIndex;
          overlay = 0;
          showLog = false;
        });
        float alpha = tab == i ? 0.95f : id == hover ? 0.9f : 0.5f;
        g.setColor(new Color(1f, 1f, 1f, alpha));
        g.drawString(TABS[i], b[0], 22 + 30);
        if (tab == i) {
          g.setColor(PURPLE);
          g.fillRoundRect(b[0], 22 + 40, b[2], 3, 3, 3);
        }
      }
      // close, minimize, account
      int closeX = W - 24 - 42, minX = closeX - 8 - 42;
      iconButton(g, closeX, 25, 42, true, () -> System.exit(0));
      iconButton(g, minX, 25, 42, false, () -> setState(JFrame.ICONIFIED));
      Accounts.Account account = accounts.selected();
      String name = account == null ? "Sign in" : account.name;
      g.setFont(regular.deriveFont(18f));
      FontMetrics f2 = g.getFontMetrics();
      int w = 24 + 8 + f2.stringWidth(name) + 24;
      int x = minX - 12 - w;
      int id = area(x, 25, w, 42, () -> overlay = 1);
      g.setColor(new Color(1f, 1f, 1f, id == hover ? 0.10f : 0.03f));
      g.fillRoundRect(x, 25, w, 42, 10, 10);
      g.setColor(new Color(1f, 1f, 1f, 0.08f));
      g.drawRoundRect(x, 25, w, 42, 10, 10);
      avatar(g, x + 12, 25 + 9, 24, account == null ? "?" : account.name.substring(0, 1).toUpperCase());
      g.setColor(TEXT);
      g.drawString(name, x + 12 + 24 + 8, 25 + 28);
    }

    private void iconButton(Graphics2D g, int x, int y, int size, boolean close, Runnable action) {
      int id = area(x, y, size, size, action);
      g.setColor(new Color(1f, 1f, 1f, id == hover ? 0.10f : 0.03f));
      g.fillRoundRect(x, y, size, size, 10, 10);
      g.setColor(new Color(1f, 1f, 1f, 0.08f));
      g.drawRoundRect(x, y, size, size, 10, 10);
      g.setColor(new Color(1f, 1f, 1f, 0.55f));
      g.setStroke(new java.awt.BasicStroke(2f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
      int cx = x + size / 2, cy = y + size / 2;
      if (close) {
        g.drawLine(cx - 6, cy - 6, cx + 6, cy + 6);
        g.drawLine(cx - 6, cy + 6, cx + 6, cy - 6);
      } else {
        g.drawLine(cx - 6, cy + 4, cx + 6, cy + 4);
      }
      g.setStroke(new java.awt.BasicStroke(1f));
    }

    private void avatar(Graphics2D g, int x, int y, int size, String letter) {
      g.setPaint(new GradientPaint(x, y, PURPLE, x, y + size, PURPLE_DARK));
      g.fillOval(x, y, size, size);
      g.setColor(Color.WHITE);
      g.setFont(bold.deriveFont(size * 0.55f));
      FontMetrics fm = g.getFontMetrics();
      g.drawString(letter, x + (size - fm.stringWidth(letter)) / 2, y + size / 2 + fm.getAscent() / 2 - 1);
    }

    private void drawLaunch(Graphics2D g) {
      int x = LAUNCH_X, y = LAUNCH_Y, w = LAUNCH_W, h = LAUNCH_H;
      int id = area(x, y, w, h, () -> play(null));
      boolean over = id == hover && !running;
      // glow: stacked translucent rounded rects stand in for the blur
      Color glow = running ? new Color(0xAAAAAA) : PURPLE;
      for (int i = 6; i >= 1; i--) {
        g.setColor(new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), over ? 12 : 7));
        g.fillRoundRect(x - i * 2, y - i + 3, w + i * 4, h + i * 2, 20 + i * 2, 20 + i * 2);
      }
      Color top = running ? new Color(0x3A3346) : over ? new Color(0xB574F0) : new Color(0xA45BE6);
      Color bottom = running ? new Color(0x25202E) : over ? new Color(0x7E3BC4) : new Color(0x6C36A9);
      g.setPaint(new GradientPaint(x, y, top, x, y + h, bottom));
      g.fillRoundRect(x, y, w, h, 16, 16);
      g.setColor(new Color(255, 255, 255, over ? 90 : 55));
      g.drawRoundRect(x, y, w - 1, h - 1, 16, 16);
      boolean inGame = activeRunner != null && activeRunner.isGameRunning();
      String title = !running ? "PLAY" : inGame ? "PLAYING" : "GETTING READY";
      g.setFont(bold.deriveFont(25f));
      FontMetrics fm = g.getFontMetrics();
      int tx = x + (w - fm.stringWidth(title)) / 2;
      g.setColor(new Color(0, 0, 0, 70));
      g.drawString(title, tx, y + 35 + 2);
      g.setColor(Color.WHITE);
      g.drawString(title, tx, y + 35);
      g.setFont(regular.deriveFont(12f));
      fm = g.getFontMetrics();
      String sub = "Minecraft " + settings.version().id + "  -  " + ("vulkan".equals(settings.renderer()) ? "Vulkan" : "Normal");
      g.setColor(new Color(255, 255, 255, 170));
      g.drawString(sub, x + (w - fm.stringWidth(sub)) / 2, y + 54);
      // progress
      if (running || fraction > 0) {
        g.setFont(bold.deriveFont(11f));
        g.setColor(new Color(0xD4D4D4));
        g.drawString(status, x, y + h + 21);
        String percent = Math.round(fraction * 100) + "%";
        g.drawString(percent, x + w - g.getFontMetrics().stringWidth(percent), y + h + 21);
        g.setColor(new Color(0x2B2338));
        g.fillRoundRect(x, y + h + 28, w, 8, 8, 8);
        g.setPaint(new GradientPaint(x, 0, PURPLE, x + w, 0, new Color(0xB57BFF)));
        g.fillRoundRect(x, y + h + 28, Math.max(8, (int) (w * fraction)), 8, 8, 8);
      } else {
        g.setFont(regular.deriveFont(12f));
        g.setColor(FAINT);
        g.drawString(status, x, y + h + 21);
      }
    }

    /** Version and renderer: Normal or Vulkan. Only what the chosen ones need is downloaded, the next time Play is pressed. */
    private void drawRenderer(Graphics2D g) {
      int x = FRIENDS_X, y = LAUNCH_Y, w = QUICK_X + QUICK_W - FRIENDS_X, h = LAUNCH_H;
      panel(g, x, y, w, h);
      int half = (w - 32 - 16) / 2, segW = (half - 6) / 2, segY = y + 28, segH = 30;
      int left = x + 16, right = left + half + 16;
      g.setFont(bold.deriveFont(11f));
      g.setColor(MUTED);
      g.drawString("VERSION", left, y + 19);
      g.drawString("RENDERER", right, y + 19);
      GameVersion version = settings.version();
      boolean vulkan = "vulkan".equals(settings.renderer());
      segmentButton(g, left, segY, segW, segH, "1.8.9", version == GameVersion.V1_8_9, () -> chooseVersion(GameVersion.V1_8_9));
      segmentButton(g, left + segW + 6, segY, segW, segH, "1.7.10", version == GameVersion.V1_7_10, () -> chooseVersion(GameVersion.V1_7_10));
      segmentButton(g, right, segY, segW, segH, "NORMAL", !vulkan, () -> chooseRenderer("normal"));
      segmentButton(g, right + segW + 6, segY, segW, segH, "VULKAN", vulkan, () -> chooseRenderer("vulkan"));
    }

    private void segmentButton(Graphics2D g, int x, int y, int w, int h, String label, boolean on, Runnable action) {
      int id = area(x, y, w, h, action);
      if (on) {
        g.setPaint(new GradientPaint(x, y, new Color(0xA45BE6), x, y + h, new Color(0x6C36A9)));
      } else {
        g.setColor(id == hover && !running ? new Color(0x2C2240) : new Color(0x201A2C));
      }
      g.fillRoundRect(x, y, w, h, 10, 10);
      g.setColor(on ? new Color(255, 255, 255, 60) : PANEL_LINE);
      g.drawRoundRect(x, y, w, h, 10, 10);
      g.setFont(bold.deriveFont(13f));
      g.setColor(on ? Color.WHITE : new Color(0xA79FB5));
      g.drawString(label, x + (w - g.getFontMetrics().stringWidth(label)) / 2, y + h / 2 + 5);
    }

    private void panel(Graphics2D g, int x, int y, int w, int h) {
      g.setColor(new Color(PANEL.getRed(), PANEL.getGreen(), PANEL.getBlue(), 225));
      g.fillRoundRect(x, y, w, h, 14, 14);
      g.setColor(PANEL_LINE);
      g.drawRoundRect(x, y, w, h, 14, 14);
    }

    private void header(Graphics2D g, int x, int y, String text) {
      g.setColor(new Color(1f, 1f, 1f, 0.25f));
      g.fillRoundRect(x, y + 3, 14, 14, 4, 4);
      g.setFont(bold.deriveFont(13f));
      g.setColor(new Color(0xE9DDFB));
      g.drawString(text, x + 24, y + 15);
    }

    private void drawPage(Graphics2D g) {
      panel(g, CENTER_X, CENTER_Y, CENTER_W, CENTER_H);
      if (tab == 0) drawHome(g);
      else if (tab == 1) drawServers(g);
      else if (tab == 2) drawSettings(g);
      else drawAbout(g);
    }

    private int wrapped(Graphics2D g, String text, int x, int y, int width, int lineHeight) {
      FontMetrics fm = g.getFontMetrics();
      StringBuilder line = new StringBuilder();
      for (String word : text.split(" ")) {
        String next = line.length() == 0 ? word : line + " " + word;
        if (fm.stringWidth(next) > width && line.length() > 0) {
          g.drawString(line.toString(), x, y);
          y += lineHeight;
          line = new StringBuilder(word);
        } else line = new StringBuilder(next);
      }
      if (line.length() > 0) g.drawString(line.toString(), x, y);
      return y + lineHeight;
    }

    private void drawHome(Graphics2D g) {
      int x = CENTER_X + 24, y = CENTER_Y + 28;
      g.setFont(bold.deriveFont(22f));
      g.setColor(Color.WHITE);
      g.drawString("Welcome to luka", x, y + 14);
      avatar(g, x, y + 30, 30, "l");
      g.setFont(bold.deriveFont(12f));
      g.setColor(LIGHT);
      g.drawString("luka", x + 40, y + 43);
      g.setFont(regular.deriveFont(11f));
      g.setColor(MUTED);
      g.drawString("Minecraft " + settings.version().id + "  -  client " + VERSION, x + 40, y + 57);
      g.setColor(PANEL_LINE);
      g.fillRect(x, y + 72, CENTER_W - 48, 1);
      g.setFont(regular.deriveFont(13f));
      g.setColor(new Color(0xB9B0C6));
      wrapped(
          g,
          "luka is a 1.8.9 client built for HCF: cooldown timers, potion and armour status, a clean HUD editor, motion blur, "
              + "custom sky packs and a long list of performance options. Press PLAY to start it, pick a server on the SERVERS tab to "
              + "join it straight away, and tune memory under SETTINGS. In game, press Right Shift for the luka menu.",
          x, y + 100, CENTER_W - 48, 22);
    }

    private void drawServers(Graphics2D g) {
      int x = CENTER_X + 24, y = CENTER_Y + 22;
      header(g, x, y, "FEATURED SERVERS");
      int top = y + 34, height = CENTER_H - 22 - 34 - 24;
      serverCard(g, 0, x, top, 466, height, true);
      int rightX = x + 466 + 12, rightW = CENTER_W - 48 - 466 - 12, each = (height - 12) / 2;
      serverCard(g, 1, rightX, top, rightW, each, false);
      serverCard(g, 2, rightX, top + each + 12, rightW, each, false);
    }

    private void serverCard(Graphics2D g, int index, int x, int y, int w, int h, boolean big) {
      final String address = SERVERS[index][1];
      int id = area(x, y, w, h, () -> play(address));
      Color a = Color.getHSBColor((255 + index * 22) / 360f, 0.55f, id == hover ? 0.5f : 0.4f);
      Color b = Color.getHSBColor((262 + index * 22) / 360f, 0.7f, 0.16f);
      g.setPaint(new GradientPaint(x, y, a, x + w, y + h, b));
      g.fillRoundRect(x, y, w, h, 8, 8);
      g.setColor(new Color(255, 255, 255, id == hover ? 60 : 24));
      g.drawRoundRect(x, y, w, h, 8, 8);
      int pad = big ? 20 : 14;
      if (big) {
        g.setColor(new Color(0, 0, 0, 60));
        g.fillRoundRect(x + pad, y + pad, 84, 84, 14, 14);
        g.setColor(Color.WHITE);
        g.setFont(bold.deriveFont(40f));
        String letter = SERVERS[index][0].substring(0, 1).toUpperCase();
        g.drawString(letter, x + pad + (84 - g.getFontMetrics().stringWidth(letter)) / 2, y + pad + 56);
      }
      int tx = big ? x + pad + 84 + 18 : x + pad;
      g.setFont(bold.deriveFont(big ? 24f : 17f));
      g.setColor(Color.WHITE);
      g.drawString(SERVERS[index][0].toUpperCase(), tx, y + pad + (big ? 28 : 18));
      g.setFont(regular.deriveFont(big ? 14f : 12f));
      g.setColor(new Color(255, 255, 255, 190));
      g.drawString(SERVERS[index][2], tx, y + pad + (big ? 52 : 36));
      ServerPing ping = pings[index];
      String text = ping.online ? ping.players + " online  -  " + ping.millis + " ms" : "offline / no answer";
      g.setFont(bold.deriveFont(big ? 13f : 11f));
      g.setColor(ping.online ? new Color(0xC9F5CF) : new Color(0xB8A9C9));
      g.drawString(text, tx, y + h - pad);
    }

    private void drawSettings(Graphics2D g) {
      int x = CENTER_X + 24, y = CENTER_Y + 22;
      header(g, x, y, "SETTINGS");
      g.setFont(bold.deriveFont(12f));
      g.setColor(MUTED);
      g.drawString("RAM ALLOCATION", x, y + 52);
      int sx = x, sy = y + 70, sw = 360;
      g.setColor(new Color(0x2B2338));
      g.fillRoundRect(sx, sy, sw, 6, 6, 6);
      float p = (settings.ramMb() - 1024) / 7168f;
      g.setPaint(new GradientPaint(sx, 0, PURPLE, sx + sw, 0, new Color(0xB57BFF)));
      g.fillRoundRect(sx, sy, (int) (sw * p), 6, 6, 6);
      g.setColor(Color.WHITE);
      g.fillOval(sx + (int) (sw * p) - 8, sy - 5, 16, 16);
      g.setFont(regular.deriveFont(14f));
      g.setColor(new Color(1f, 1f, 1f, 0.6f));
      g.drawString(String.format("%.1f GB", settings.ramMb() / 1024.0), sx + sw + 18, sy + 8);

      g.setFont(bold.deriveFont(12f));
      g.setColor(MUTED);
      g.drawString("ON LAUNCH", x, y + 116);
      for (int i = 0; i < LAUNCH_OPTIONS.length; i++) {
        final String mode = LAUNCH_OPTIONS[i];
        int bx = x + i * 124, by = y + 128, bw = 116, bh = 28;
        area(bx, by, bw, bh, () -> {
          settings.set("onLaunch", mode);
          settings.save();
        });
        boolean on = settings.onLaunch().equals(mode);
        g.setColor(on ? PURPLE : new Color(0x241B33));
        g.fillRoundRect(bx, by, bw, bh, 8, 8);
        g.setFont(bold.deriveFont(10f));
        g.setColor(on ? Color.WHITE : new Color(0xA79FB5));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(LAUNCH_LABELS[i], bx + (bw - fm.stringWidth(LAUNCH_LABELS[i])) / 2, by + 18);
      }
      // system information
      int ix = x + 456, iy = y + 40, iw = CENTER_W - 48 - 456, ih = CENTER_H - 22 - 40 - 24;
      g.setColor(new Color(0x1E1729));
      g.fillRoundRect(ix, iy, iw, ih, 10, 10);
      g.setFont(bold.deriveFont(12f));
      g.setColor(new Color(0xE9DDFB));
      g.drawString("SYSTEM INFORMATION", ix + 16, iy + 26);
      g.setFont(regular.deriveFont(Font.ITALIC, 12f));
      g.setColor(MUTED);
      String cpu = hardware[0].isEmpty() ? System.getenv("PROCESSOR_IDENTIFIER") : hardware[0];
      wrapped(g, cpu == null ? System.getProperty("os.name", "") : cpu, ix + 16, iy + 50, iw - 32, 16);
      g.drawString(System.getProperty("os.arch", "") + "  -  " + System.getProperty("os.name", ""), ix + 16, iy + 90);
      g.drawString(totalMemory() + " installed", ix + 16, iy + 108);
      g.drawString("Java " + System.getProperty("java.version"), ix + 16, iy + 126);
      if (!hardware[1].isEmpty()) wrapped(g, hardware[1], ix + 16, iy + 144, iw - 32, 16);
      // game log toggle
      int lid = area(x, y + 176, 140, 26, () -> showLog = !showLog);
      g.setColor(new Color(1f, 1f, 1f, lid == hover ? 0.12f : 0.06f));
      g.fillRoundRect(x, y + 176, 140, 26, 8, 8);
      g.setFont(bold.deriveFont(10f));
      g.setColor(new Color(0xCFC6DB));
      g.drawString("SHOW GAME LOG", x + 26, y + 193);
      linkButton(g, x + 160, y + 174, 220, "REPAIR GAME FILES", LauncherFrame.this::repair);

      g.setFont(bold.deriveFont(10f));
      g.setColor(MUTED);
      g.drawString("BACKUP AND SYNC", x, y + 220);
      linkButton(g, x, y + 228, 112, "BACK UP NOW", LauncherFrame.this::backUpNow);
      linkButton(g, x + 120, y + 228, 92, "RESTORE...", LauncherFrame.this::restoreBackup);
      linkButton(g, x + 220, y + 228, 92, "SYNC NOW", () -> startSync(true));
      linkButton(g, x + 320, y + 228, 112, "SYNC FOLDER", LauncherFrame.this::chooseSyncFolder);
      g.setFont(regular.deriveFont(10f));
      g.setColor(FAINT);
      String syncLine = syncStatus.isEmpty() ? (settings.syncFolder().isEmpty() ? "Sync folder not set" : settings.syncFolder()) : syncStatus;
      g.drawString(ellipsize(g, syncLine, 432), x, y + 274);

      final boolean nvOn = settings.nvOptimize();
      area(x, y + 286, 432, 34, () -> {
        settings.set("nvOptimize", String.valueOf(!nvOn));
        settings.save();
      });
      g.setColor(nvOn ? PURPLE : new Color(0x241B33));
      g.fillRoundRect(x, y + 290, 16, 16, 5, 5);
      if (nvOn) {
        g.setColor(Color.WHITE);
        g.fillRoundRect(x + 5, y + 295, 6, 6, 3, 3);
      }
      g.setFont(bold.deriveFont(10f));
      g.setColor(new Color(0xCFC6DB));
      g.drawString("OPTIMIZE NVIDIA SETTINGS", x + 24, y + 299);
      g.setFont(regular.deriveFont(10f));
      g.setColor(FAINT);
      g.drawString("Changes Nvidia's present method for luka only. Untick to undo. Applies on the next Play.", x + 24, y + 313);

      g.setFont(regular.deriveFont(10f));
      g.setColor(FAINT);
      g.drawString(ellipsize(g, updateStatus, iw - 32), ix + 16, iy + 174);
      linkButton(g, ix + 16, iy + 182, iw - 32, "CHECK FOR UPDATES", LauncherFrame.this::checkUpdates);
    }

    private String ellipsize(Graphics2D g, String text, int width) {
      if (g.getFontMetrics().stringWidth(text) <= width) return text;
      while (text.length() > 3 && g.getFontMetrics().stringWidth(text + "...") > width) text = text.substring(0, text.length() - 1);
      return text + "...";
    }

    private void drawAbout(Graphics2D g) {
      int x = CENTER_X + 24, y = CENTER_Y + 22;
      header(g, x, y, "ABOUT");
      g.setFont(regular.deriveFont(13f));
      g.setColor(new Color(0xB9B0C6));
      int end = wrapped(
          g,
          "luka launcher starts the luka client for Minecraft 1.8.9 on Fabric (Ornithe). The first press of PLAY downloads Minecraft "
              + "from Mojang, Java from Adoptium, luka's mods from client.luka.wtf, and other mods from Modrinth. Your game, mods "
              + "and settings stay in your own user folder. Sign in with Microsoft or an offline name. luka is free software "
              + "under the GNU GPL v3 or later (github.com/lukalul/luka-client); third-party parts keep their own licences.",
          x + 24, y + 52, CENTER_W - 48 - 48, 20);
      // AGPL source offer for the Vulkan renderer (github.com/lukalul/luka-vulkan); keep it on this page.
      end = wrapped(
          g,
          "luka's Vulkan renderer is free software under the GNU Affero General Public License v3, based on Astatine by "
              + "Ren Karuizawa. Its source code is at " + VULKAN_SOURCE_URL.replace("https://", "") + ".",
          x + 24, end + 8, CENTER_W - 48 - 48, 20);
      int row = Math.max(y + 150, end + 24);
      g.setFont(bold.deriveFont(12f));
      g.setColor(MUTED);
      g.drawString("LAUNCHER VERSION", x, row);
      g.drawString("LOCALE", x, row + 26);
      g.setFont(regular.deriveFont(13f));
      g.setColor(new Color(0xA5A5B5));
      g.drawString(VERSION + "  (newest)", x + 140, row);
      g.drawString(java.util.Locale.getDefault().toLanguageTag(), x + 140, row + 26);
      linkButton(g, x, row + 48, 240, "VULKAN RENDERER SOURCE", () -> openBrowser(VULKAN_SOURCE_URL));
      linkButton(g, x + 430, row - 16, 240, "OPEN GAME FOLDER", () -> open(new File(settings.version().home(root), "game")));
      linkButton(g, x + 430, row + 22, 240, "OPEN LAUNCHER FOLDER", () -> open(root));
      if (!Manifests.changelog().isEmpty()) linkButton(g, x + 430, row + 60, 240, "WHAT'S NEW", () -> overlay = 3);
    }

    private void linkButton(Graphics2D g, int x, int y, int w, String text, Runnable action) {
      int id = area(x, y, w, 30, action);
      g.setColor(id == hover ? PURPLE : PURPLE_DARK);
      g.fillRoundRect(x, y, w, 30, 8, 8);
      g.setFont(bold.deriveFont(11f));
      g.setColor(Color.WHITE);
      g.drawString(text, x + (w - g.getFontMetrics().stringWidth(text)) / 2, y + 19);
    }

    private void drawSide(Graphics2D g) {
      panel(g, FRIENDS_X, CENTER_Y, FRIENDS_W, CENTER_H);
      header(g, FRIENDS_X + 16, CENTER_Y + 18, "YOUR GAME");
      boolean vulkan = "vulkan".equals(settings.renderer());
      boolean installed = new File(settings.version().home(root), "installed.json").isFile();
      sideRow(g, CENTER_Y + 62, "Renderer", vulkan ? "Vulkan" : "Normal");
      sideRow(g, CENTER_Y + 96, "Memory", String.format("%.1f GB", settings.ramMb() / 1024.0));
      sideRow(g, CENTER_Y + 130, "Version", settings.version().id);
      sideRow(g, CENTER_Y + 164, "Status", installed ? "Installed" : "Not installed");
      g.setFont(regular.deriveFont(11f));
      g.setColor(FAINT);
      wrapped(g, installed ? "Updates are downloaded when you press Play." : "The first Play downloads the game, Java and the mods. After that it starts straight away.",
          FRIENDS_X + 16, CENTER_Y + 212, FRIENDS_W - 32, 16);
      panel(g, QUICK_X, CENTER_Y, QUICK_W, CENTER_H);
      g.setFont(bold.deriveFont(11f));
      g.setColor(new Color(0xE9DDFB));
      g.drawString("QUICK", QUICK_X + 20, CENTER_Y + 28);
      g.drawString("CONNECT", QUICK_X + 12, CENTER_Y + 44);
      for (int i = 0; i < SERVERS.length; i++) {
        final String address = SERVERS[i][1];
        int bx = QUICK_X + 20, by = CENTER_Y + 62 + i * 52;
        int id = area(bx, by, 40, 40, () -> play(address));
        g.setPaint(new GradientPaint(bx, by, Color.getHSBColor((255 + i * 22) / 360f, 0.55f, id == hover ? 0.75f : 0.6f), bx, by + 40, PURPLE_DARK));
        g.fillRoundRect(bx, by, 40, 40, 10, 10);
        g.setColor(Color.WHITE);
        g.setFont(bold.deriveFont(18f));
        String letter = SERVERS[i][0].substring(0, 1).toUpperCase();
        g.drawString(letter, bx + (40 - g.getFontMetrics().stringWidth(letter)) / 2, by + 27);
      }
    }

    private void sideRow(Graphics2D g, int y, String label, String value) {
      g.setFont(regular.deriveFont(12f));
      g.setColor(MUTED);
      g.drawString(label, FRIENDS_X + 16, y);
      g.setFont(bold.deriveFont(12f));
      g.setColor(TEXT);
      g.drawString(value, FRIENDS_X + FRIENDS_W - 16 - g.getFontMetrics().stringWidth(value), y);
      g.setColor(PANEL_LINE);
      g.fillRect(FRIENDS_X + 16, y + 11, FRIENDS_W - 32, 1);
    }

    private void drawFooter(Graphics2D g) {
      g.setColor(new Color(0x0A0810));
      g.fillRect(0, H - 41, W, 41);
      if (logo != null) g.drawImage(logo, 24, H - 41 + 14, 12, 12, null);
      g.setFont(regular.deriveFont(15f));
      g.setColor(new Color(1f, 1f, 1f, 0.16f));
      g.drawString("luka  " + VERSION, 44, H - 41 + 26);
      String copy = "Not affiliated with Mojang or Microsoft";
      g.drawString(copy, W - 16 - g.getFontMetrics().stringWidth(copy), H - 41 + 26);
    }

    private void drawOverlay(Graphics2D g) {
      g.setColor(new Color(0x0C0910));
      g.fillRoundRect(0, 0, W, H, 8, 8);
      if (logo != null) g.drawImage(logo, W / 2 - 60, 90, 120, 120, null);
      if (logoInner != null) g.drawImage(logoInner, W / 2 - 25, 125, 50, 50, null);
      iconButton(g, W - 24 - 42, 25, 42, true, () -> System.exit(0));
      iconButton(g, W - 24 - 42 - 8 - 42, 25, 42, false, () -> setState(JFrame.ICONIFIED));
      g.setFont(bold.deriveFont(11f));
      g.setColor(new Color(0xA79FB5));
      if (overlay == 3) {
        String title = "WHAT'S NEW";
        g.drawString(title, (W - g.getFontMetrics().stringWidth(title)) / 2, 242);
        int x = W / 2 - 280, y = 276, bottom = H - 110;
        for (List<String> release : Manifests.changelog()) {
          if (y + 44 > bottom) break;
          g.setFont(bold.deriveFont(14f));
          g.setColor(Color.WHITE);
          g.drawString(release.get(0), x, y);
          y += 24;
          g.setFont(regular.deriveFont(13f));
          g.setColor(new Color(0xB9B0C6));
          for (String note : release.subList(1, release.size())) {
            if (y + 20 > bottom) break;
            y = wrapped(g, "-  " + note, x, y, 560, 20);
          }
          y += 14;
        }
        bigButton(g, W / 2 - 80, H - 86, 160, "CLOSE", () -> overlay = 0);
      } else if (overlay == 1) {
        String title = "SELECT AN ACCOUNT";
        g.drawString(title, (W - g.getFontMetrics().stringWidth(title)) / 2, 242);
        int y = 262;
        for (final Accounts.Account a : accounts.all()) {
          int x = W / 2 - 120;
          int id = area(x, y, 240, 38, () -> {
            accounts.select(a);
            overlay = 0;
            done();
          });
          g.setColor(id == hover ? new Color(0x2A2037) : new Color(0x1B1524));
          g.fillRoundRect(x, y, 240, 38, 10, 10);
          g.setColor(a == accounts.selected() ? PURPLE : PANEL_LINE);
          g.drawRoundRect(x, y, 240, 38, 10, 10);
          avatar(g, x + 8, y + 7, 24, a.name.substring(0, 1).toUpperCase());
          g.setFont(regular.deriveFont(14f));
          g.setColor(new Color(1f, 1f, 1f, 0.85f));
          g.drawString(a.name, x + 42, y + 20);
          g.setFont(regular.deriveFont(10f));
          g.setColor(FAINT);
          g.drawString(a.microsoft() ? "Microsoft account" : "Offline", x + 42, y + 32);
          final Accounts.Account remove = a;
          if (accounts.all().size() > 1) {
            int rid = area(x + 240 - 30, y + 8, 22, 22, () -> accounts.remove(remove));
            g.setColor(rid == hover ? new Color(0xFF6B8A) : FAINT);
            g.setFont(bold.deriveFont(14f));
            g.drawString("x", x + 240 - 23, y + 24);
          }
          y += 46;
        }
        bigButton(g, W / 2 - 120, y + 14, 240, "SIGN IN WITH ANOTHER ACCOUNT", () -> {
          overlay = 2;
          loginMessage = "";
          nameField.setText("");
        });
        backLink(g, accountMode ? "CLOSE" : "BACK", () -> {
          overlay = 0;
          done();
        });
      } else {
        String title = "SIGN IN";
        g.drawString(title, (W - g.getFontMetrics().stringWidth(title)) / 2, 242);
        bigButton(g, W / 2 - 141, 262, 283, "SIGN IN WITH MICROSOFT", this::startMicrosoft);
        g.setFont(bold.deriveFont(10f));
        g.setColor(new Color(0xA79FB5));
        String or = "OR PLAY WITH A NAME (offline, no account check)";
        g.drawString(or, (W - g.getFontMetrics().stringWidth(or)) / 2, 318);
        bigButton(g, W / 2 - 141, 382, 283, "USE THIS NAME", LauncherFrame.this::offlineSignIn);
        if (!deviceCode.isEmpty()) {
          g.setColor(new Color(0x1B1524));
          g.fillRoundRect(W / 2 - 160, 440, 320, 70, 12, 12);
          g.setColor(PURPLE);
          g.drawRoundRect(W / 2 - 160, 440, 320, 70, 12, 12);
          g.setFont(bold.deriveFont(28f));
          g.setColor(Color.WHITE);
          g.drawString(deviceCode, W / 2 - 40 - g.getFontMetrics().stringWidth(deviceCode) / 2, 487);
          final String codeToCopy = deviceCode;
          int cid = area(W / 2 + 92, 462, 56, 26, () -> {
            try {
              Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(codeToCopy), null);
              copiedAt = System.currentTimeMillis();
            } catch (Exception ignored) {
              // the code is on screen
            }
          });
          g.setColor(cid == hover ? new Color(0xB574F0) : PURPLE);
          g.fillRoundRect(W / 2 + 92, 462, 56, 26, 8, 8);
          g.setFont(bold.deriveFont(10f));
          g.setColor(Color.WHITE);
          String copyText = System.currentTimeMillis() - copiedAt < 1500 ? "COPIED" : "COPY";
          g.drawString(copyText, W / 2 + 92 + (56 - g.getFontMetrics().stringWidth(copyText)) / 2, 479);
        }
        g.setFont(regular.deriveFont(12f));
        g.setColor(new Color(0xE0728B));
        if (!loginMessage.isEmpty()) {
          g.setColor(deviceCode.isEmpty() && loginMessage.startsWith("Contacting") ? MUTED : new Color(0xE9DDFB));
          wrapped(g, loginMessage, W / 2 - 200, deviceCode.isEmpty() ? 450 : 530, 400, 16);
        }
        backLink(g, "BACK TO ACCOUNT SELECTOR", () -> {
          loginCancelled = true;
          overlay = 1;
          nameField.setVisible(false);
        });
      }
    }

    private void startMicrosoft() {
      microsoftSignIn();
    }

    private void bigButton(Graphics2D g, int x, int y, int w, String text, Runnable action) {
      int id = area(x, y, w, 38, action);
      g.setPaint(new GradientPaint(x, y, id == hover ? new Color(0xB574F0) : PURPLE, x, y + 38, PURPLE_DARK));
      g.fillRoundRect(x, y, w, 38, 8, 8);
      g.setFont(bold.deriveFont(11f));
      g.setColor(Color.WHITE);
      g.drawString(text, x + (w - g.getFontMetrics().stringWidth(text)) / 2, y + 24);
    }

    private void backLink(Graphics2D g, String text, Runnable action) {
      g.setFont(bold.deriveFont(11f));
      int w = g.getFontMetrics().stringWidth(text);
      int x = (W - w) / 2, y = H - 53;
      int id = area(x - 20, y - 14, w + 40, 24, action);
      g.setColor(id == hover ? new Color(0xE9DDFB) : FAINT);
      g.drawString("<  " + text, x - 12, y);
    }
  }

  private boolean onSlider(int mx, int my) {
    int x = CENTER_X + 24, y = CENTER_Y + 22 + 70;
    return mx >= x - 10 && mx <= x + 370 && my >= y - 12 && my <= y + 20;
  }

  private void setRam(int mx) {
    float p = Math.max(0f, Math.min(1f, (mx - (CENTER_X + 24)) / 360f));
    int mb = 1024 + Math.round(p * 7168 / 512f) * 512;
    settings.set("ramMb", String.valueOf(mb));
  }

  /** {cpu name, graphics card name}, filled in the background (reading them takes a moment). */
  private static final String[] hardware = {"", ""};

  private static String readAll(java.io.InputStream in) throws java.io.IOException {
    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
    byte[] buffer = new byte[4096];
    int n;
    while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
    return new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
  }

  private static void loadHardware() {
    if (!Platform.windows()) {
      loadHardwareLinux();
      return;
    }
    try {
      Process p = new ProcessBuilder("reg", "query", "HKLM\\HARDWARE\\DESCRIPTION\\System\\CentralProcessor\\0", "/v", "ProcessorNameString").redirectErrorStream(true).start();
      String out = readAll(p.getInputStream());
      p.waitFor();
      int at = out.indexOf("REG_SZ");
      if (at >= 0) hardware[0] = out.substring(at + 6).trim().replaceAll("\\s+", " ");
    } catch (Throwable ignored) {
      // the raw processor id is shown instead
    }
    try {
      Process p = new ProcessBuilder("powershell", "-NoProfile", "-Command", "(Get-CimInstance Win32_VideoController).Name -join '|'").redirectErrorStream(true).start();
      String out = readAll(p.getInputStream()).trim();
      p.waitFor();
      for (String name : out.split("\\|")) {
        String n = name.trim();
        if (!n.isEmpty() && !n.toLowerCase().contains("virtual") && !n.toLowerCase().contains("basic") && !n.toLowerCase().contains("parsec")) {
          hardware[1] = n;
          break;
        }
      }
    } catch (Throwable ignored) {
      // no graphics line
    }
  }

  /** Linux: the processor from /proc/cpuinfo, the graphics card from lspci when it is installed. */
  private static void loadHardwareLinux() {
    try {
      for (String line : java.nio.file.Files.readAllLines(new File("/proc/cpuinfo").toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
        if (line.startsWith("model name")) {
          int colon = line.indexOf(':');
          if (colon > 0) hardware[0] = line.substring(colon + 1).trim().replaceAll("\\s+", " ");
          break;
        }
      }
    } catch (Throwable ignored) {
      // the raw processor id is shown instead
    }
    try {
      Process p = new ProcessBuilder("lspci").redirectErrorStream(true).start();
      String out = readAll(p.getInputStream());
      p.waitFor();
      for (String line : out.split("\n")) {
        if (!line.contains("VGA compatible controller") && !line.contains("3D controller") && !line.contains("Display controller")) continue;
        int colon = line.indexOf(": ");
        if (colon < 0) continue;
        String n = line.substring(colon + 2).trim();
        if (!n.isEmpty() && !n.toLowerCase().contains("virtual")) { hardware[1] = n; break; }
      }
    } catch (Throwable ignored) {
      // lspci is not installed: no graphics line
    }
  }

  private static String totalMemory() {
    try {
      java.lang.management.OperatingSystemMXBean os = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
      long bytes = ((com.sun.management.OperatingSystemMXBean) os).getTotalPhysicalMemorySize();
      return String.format("%.1f GB", bytes / 1073741824.0);
    } catch (Throwable t) {
      return "unknown memory";
    }
  }

  private static void open(File folder) {
    try {
      folder.mkdirs();
      Desktop.getDesktop().open(folder);
    } catch (Exception ignored) {
      // nothing to open it with
    }
  }

  private void styleField(JTextField field) {
    field.setBackground(new Color(0x1B1524));
    field.setForeground(Color.WHITE);
    field.setCaretColor(LIGHT);
    field.setFont(regular.deriveFont(15f));
    field.setHorizontalAlignment(JTextField.CENTER);
    field.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0x3A2C52), 2), new EmptyBorder(4, 10, 4, 10)));
  }

  private Font font(String path) {
    try (InputStream in = LauncherFrame.class.getResourceAsStream(path)) {
      return Font.createFont(Font.TRUETYPE_FONT, in);
    } catch (Exception e) {
      return new Font("SansSerif", Font.PLAIN, 12);
    }
  }

  private static BufferedImage load(String path) {
    try (InputStream in = LauncherFrame.class.getResourceAsStream(path)) {
      return ImageIO.read(in);
    } catch (IOException | IllegalArgumentException e) {
      return null;
    }
  }
}

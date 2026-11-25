package dev.luka.launcher;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.zip.GZIPInputStream;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * The replay editor, laid out like a video editor (after interpolini, GPL-3.0): recordings on the left, a large preview,
 * the render settings in a sidebar, and a timeline underneath where the clips sit end to end on one track with picture
 * strips, a ruler and a playhead. Clips are parts of SubFrame recordings (.sfr in the game's subframe folder): trim them
 * by dragging their edges, split them at the playhead, drag them to reorder.
 *
 * The game does all rendering. The launcher starts it with a render job (-Dsubframe.renderjob, SubFrame's RenderJob):
 * the clips become one video (joined with ffmpeg), or with MAKE PREVIEWS small pictures (PREVIEW_FPS a second) that the
 * preview and the picture strips show. The edit is kept in subframe/editor-project.properties.
 */
final class ReplayEditor extends JFrame {
  private static final Color BG = LauncherFrame.BG, PANEL = LauncherFrame.PANEL, LINE = LauncherFrame.PANEL_LINE;
  private static final Color PURPLE = LauncherFrame.PURPLE, PURPLE_DARK = LauncherFrame.PURPLE_DARK, LIGHT = LauncherFrame.LIGHT;
  private static final Color TEXT = LauncherFrame.TEXT, MUTED = LauncherFrame.MUTED, FAINT = LauncherFrame.FAINT;
  private static final Color DEEP = new Color(0x0A080D), TRACK = new Color(0x141019), CLIP = new Color(0x231B2D);
  private static final Color KILL = new Color(0xFF5A5A);
  /** SubFrame's recording format: "SFR1", the version Replay can play, and the frame record type. */
  private static final int MAGIC = 0x53465231, FORMAT = 16, REC_PACKET = 1, REC_FRAME = 3;
  /** The server chat packet's id (S02PacketChat), the same on 1.8.9 and 1.7.10. */
  private static final int CHAT_PACKET = 2;
  /** Clips shorter than this cannot be rendered on their own. */
  private static final double MIN_CLIP = 0.2;
  /**
   * Preview pictures: frames per second of recording, the size the game renders them at (smaller makes its menus huge:
   * the GUI scale cannot go below 1), and the size they are kept at in memory.
   */
  private static final int PREVIEW_FPS = 10, PREVIEW_W = 640, PREVIEW_H = 360, KEEP_W = 384, KEEP_H = 216;

  /** What the editor needs from the launcher (a stand-in in tests). */
  interface Host {
    File gameDir();

    GameVersion gameVersion();

    Font font(boolean bold, float size);

    /** Starts the game with this render job (hidden: on a desktop no one sees); false while a game is already running. */
    boolean startRenderJob(File job, boolean hidden, java.util.function.BiConsumer<Integer, String> onExit);

    java.util.List<java.awt.Image> getIconImages();

    /** Ends the render the editor started (its game). */
    void stopRender();
  }

  /** Run once the window has closed (the editor-only launcher ends its process). */
  Runnable onClosed;

  /** The launcher whose fonts the fields below use: set from the constructor's super() call, before they are made. */
  private static Host fonts;

  private static String title(Host host) {
    fonts = host;
    return "luka replay editor";
  }

  private final Host host;
  private final File gameDir, recordings;
  private final DefaultListModel<Recording> library = new DefaultListModel<>();
  private final JList<Recording> libraryList = new JList<>(library);
  private final List<Clip> clips = new ArrayList<>();
  private int selected = -1;
  /** The playhead, in seconds of the edit (all clips end to end). */
  private double playTime;
  private Timer player;
  private long playLast;
  private final PreviewView preview = new PreviewView();
  private final TimelineView timeline = new TimelineView();
  private final JLabel timeNow = label("0:00.0", true, 13f, TEXT), timeTotal = label("0:00.0", false, 13f, MUTED);
  private final JLabel clipInfo = label("", false, 12f, MUTED);
  private final JButton play = iconButton("play", "Play / pause (SPACE)", this::togglePlay);
  /** The selected clip's speed: below 1 is slow motion. */
  private final Choice speed = new Choice(new String[] {"0.25x", "0.5x", "0.75x", "1x", "1.5x", "2x", "4x"},
      new String[] {"0.25", "0.5", "0.75", "1", "1.5", "2", "4"}, 0);
  private final Choice fps = new Choice(new String[] {"30", "60", "120", "240"}, null, 64);
  private final Choice size = new Choice(new String[] {"720p", "1080p", "1440p", "4K"},
      new String[] {"1280x720", "1920x1080", "2560x1440", "3840x2160"}, 96);
  private final JCheckBox blur = check("Motion blur", true), chests = check("Show opened chests", true);
  /** Windows only: the game renders on a hidden desktop (luka-hidden.exe), so no game window appears. */
  private final JCheckBox hideGame = check("Hide the game window", Platform.windows());
  private final Choice subframes = new Choice(new String[] {"8", "16", "32", "64", "128"}, null, 0);
  private final JTextField output = new JTextField();
  // look of the video (render only; "" = as in the game or the recording)
  private final Choice lookFov = new Choice(new String[] {"As is", "70", "90", "110"}, new String[] {"", "70", "90", "110"}, 48);
  private final Choice lookDistance = new Choice(new String[] {"As is", "8", "12", "16"}, new String[] {"", "8", "12", "16"}, 48);
  private final Choice lookBright = new Choice(new String[] {"As is", "Dark", "Normal", "Bright", "Full"},
      new String[] {"", "0", "50", "100", "full"}, 0);
  private final Choice lookParticles = new Choice(new String[] {"As is", "All", "Fewer", "Minimal"},
      new String[] {"", "all", "decreased", "minimal"}, 0);
  private final Choice lookBobbing = new Choice(new String[] {"As is", "On", "Off"}, new String[] {"", "on", "off"}, 0);
  private final Choice lookTime = new Choice(new String[] {"As is", "Morning", "Noon", "Sunset", "Night"},
      new String[] {"", "1000", "6000", "12500", "18000"}, 0);
  private final Choice lookWeather = new Choice(new String[] {"As is", "Clear"}, new String[] {"", "clear"}, 0);
  private final JComboBox<String> lookPack = new JComboBox<>();
  private final JCheckBox hideHud = check("Whole HUD", false), hideHand = check("Hand", false), hideCrosshair = check("Crosshair", false),
      hideChat = check("Chat", false), hideScoreboard = check("Scoreboard", false), hideNametags = check("Nametags", false);
  /** luka's HUD modules (from the game's config/cbui.json): on or off in the video. */
  private final Map<String, JCheckBox> hudParts = new LinkedHashMap<>();
  /** The luka modules that draw on the HUD, in the order shown. */
  private static final String[] HUD_MODULES = {"FPS", "CPS", "Keystrokes", "Coordinates", "Direction", "Armour status", "Potion status",
      "Potion counter", "Item counter", "Pearl cooldown", "Cooldowns", "Combo", "Reach", "Ping", "Memory", "Clock", "Server address",
      "Saturation", "Toggle sprint", "Resource packs", "Team HUD", "Boss bar", "Alerts"};
  private final JButton render = button("RENDER", true), showFile = button("SHOW VIDEO", false), previews = button("MAKE PREVIEWS", false);
  private final JProgressBar bar = new JProgressBar(0, 1000);
  private final javax.swing.JTextArea status = new javax.swing.JTextArea();
  private Timer poll;
  private File jobDir;
  private boolean rendering;
  /** What was read from recordings (length, player, kills), by path + modified time + size: reading one means going through the whole file. */
  private static final Map<String, Recording> INFO = new HashMap<>();

  /** A recording on disk. */
  static final class Recording {
    final File file;
    double length = -1;
    String player = "", problem;
    /** Preview pictures, PREVIEW_FPS a second from the start; null until loaded. */
    volatile BufferedImage[] frames;
    volatile boolean loadingFrames;
    long previewCheckedMs;
    volatile List<Kill> kills = new ArrayList<>();

    Recording(File file) {
      this.file = file;
    }

    String name() {
      return file.getName().replaceAll("\\.sfr$", "");
    }

    /** "25 Nov, 16:06:58" for SubFrame's rec-20261125-160658 names; the file name otherwise. */
    String title() {
      java.util.regex.Matcher m = java.util.regex.Pattern.compile("rec-(\\d{4})(\\d{2})(\\d{2})-(\\d{2})(\\d{2})(\\d{2})(.*)").matcher(name());
      if (!m.matches()) return name();
      String[] months = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
      int month = Integer.parseInt(m.group(2));
      return Integer.parseInt(m.group(3)) + " " + (month >= 1 && month <= 12 ? months[month - 1] : m.group(2)) + ", "
          + m.group(4) + ":" + m.group(5) + ":" + m.group(6) + m.group(7);
    }

    /** The picture nearest to this time, or null. */
    BufferedImage frameAt(double t) {
      BufferedImage[] f = frames;
      if (f == null || f.length == 0) return null;
      return f[(int) Math.max(0, Math.min(f.length - 1, Math.round(t * PREVIEW_FPS)))];
    }
  }

  /** A part of a recording in the edit, in seconds from the recording's start, played at speed (0.5 = slow motion). */
  static final class Clip {
    final Recording rec;
    double start, end, speed = 1;

    Clip(Recording rec, double start, double end) {
      this.rec = rec;
      this.start = start;
      this.end = end;
    }

    Clip(Recording rec, double start, double end, double speed) {
      this(rec, start, end);
      this.speed = speed;
    }

    /** Recorded seconds the clip covers. */
    double length() {
      return end - start;
    }

    /** Seconds the clip lasts in the video (its length at its speed). */
    double dur() {
      return length() / speed;
    }
  }

  /** A kill the recording player got (from the recorded chat), in seconds from the recording's start. */
  static final class Kill {
    final double t;
    final String victim;

    Kill(double t, String victim) {
      this.t = t;
      this.victim = victim;
    }
  }

  ReplayEditor(Host host) {
    super(title(host));
    this.host = host;
    this.gameDir = host.gameDir();
    this.recordings = new File(gameDir, "subframe");
    setIconImages(host.getIconImages());
    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    setMinimumSize(new Dimension(1100, 680));
    setSize(1320, 820);
    if (host instanceof Component) setLocationRelativeTo((Component) host);
    JPanel work = new JPanel(new BorderLayout(0, 0));
    work.setBackground(BG);
    JPanel top = new JPanel(new BorderLayout(0, 0));
    top.setBackground(BG);
    top.add(libraryPanel(), BorderLayout.WEST);
    top.add(previewPanel(), BorderLayout.CENTER);
    work.add(top, BorderLayout.CENTER);
    work.add(timelinePanel(), BorderLayout.SOUTH);
    JPanel root = new JPanel(new BorderLayout(0, 0));
    root.setBackground(BG);
    root.add(work, BorderLayout.CENTER);
    root.add(sidebar(), BorderLayout.EAST);
    setContentPane(root);
    key("SPACE", "luka-play", this::togglePlay);
    key("S", "luka-split", this::splitSelected);
    key("I", "luka-in", this::markIn);
    key("O", "luka-out", this::markOut);
    key("DELETE", "luka-delete", this::removeSelected);
    key("HOME", "luka-home", () -> seek(0));
    key("END", "luka-end", () -> seek(total()));
    key("LEFT", "luka-back", () -> seek(playTime - 1.0 / PREVIEW_FPS));
    key("RIGHT", "luka-forward", () -> seek(playTime + 1.0 / PREVIEW_FPS));
    key("ctrl Z", "luka-undo", this::undo);
    key("ctrl Y", "luka-redo", this::redo);
    key("ctrl shift Z", "luka-redo2", this::redo);
    key("K", "luka-nextkill", () -> jumpKill(1));
    key("shift K", "luka-prevkill", () -> jumpKill(-1));
    addWindowListener(new WindowAdapter() {
      @Override public void windowClosing(WindowEvent e) {
        if (rendering) {
          int answer = javax.swing.JOptionPane.showConfirmDialog(ReplayEditor.this,
              "A render is still running. Stop it and close the editor?", "luka replay editor", javax.swing.JOptionPane.YES_NO_OPTION);
          if (answer != javax.swing.JOptionPane.YES_OPTION) return;
          host.stopRender();
        }
        dispose();
      }

      @Override public void windowClosed(WindowEvent e) {
        if (poll != null) poll.stop();
        stopPlay();
        saveProject();
        if (onClosed != null) onClosed.run();
      }
    });
    output.setText(new File(recordings, "edit-" + new SimpleDateFormat("yyyyMMdd-HHmm").format(new Date()) + ".mp4").getAbsolutePath());
    refreshLibrary(true);
    updateLabels();
    record();
  }

  // ---- layout ----

  private JComponent libraryPanel() {
    JPanel p = new JPanel(new BorderLayout(0, 8));
    p.setBackground(PANEL);
    p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 1, LINE), BorderFactory.createEmptyBorder(12, 12, 12, 12)));
    p.setPreferredSize(new Dimension(250, 10));
    p.add(label("RECORDINGS  " + host.gameVersion().id, true, 12f, LIGHT), BorderLayout.NORTH);
    libraryList.setBackground(PANEL);
    libraryList.setForeground(TEXT);
    libraryList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    libraryList.setCellRenderer(new DefaultListCellRenderer() {
      @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean sel, boolean focus) {
        Recording r = (Recording) value;
        String detail = r.problem != null ? r.problem : r.length < 0 ? "reading..." : clock(r.length) + (r.player.isEmpty() ? "" : "  -  " + r.player)
            + (r.kills.isEmpty() ? "" : "  -  " + r.kills.size() + (r.kills.size() == 1 ? " kill" : " kills"));
        JLabel l = (JLabel) super.getListCellRendererComponent(list, "<html><b>" + esc(r.title()) + "</b><br><span style='color:#8C849A'>"
            + esc(detail) + "</span></html>", index, sel, false);
        l.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        l.setBackground(sel ? PURPLE_DARK : PANEL);
        l.setForeground(r.problem != null ? FAINT : TEXT);
        l.setFont(fonts.font(false, 12f));
        return l;
      }
    });
    libraryList.addMouseListener(new MouseAdapter() {
      @Override public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2) addSelectedRecording();
      }
    });
    JScrollPane scroll = new JScrollPane(libraryList);
    scroll.setBorder(BorderFactory.createEmptyBorder());
    scroll.getViewport().setBackground(PANEL);
    p.add(scroll, BorderLayout.CENTER);
    JPanel buttons = new JPanel(new java.awt.GridLayout(1, 3, 6, 0));
    buttons.setOpaque(false);
    JButton add = button("ADD", true);
    add.setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 4));
    add.setToolTipText("Add the recording to the end of the edit (or double-click it)");
    add.addActionListener(e -> addSelectedRecording());
    JButton refresh = button("RELOAD", false);
    refresh.addActionListener(e -> refreshLibrary(false));
    JButton folder = button("FOLDER", false);
    folder.setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 4));
    refresh.setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 4));
    folder.addActionListener(e -> open(recordings));
    buttons.add(add);
    buttons.add(refresh);
    buttons.add(folder);
    p.add(buttons, BorderLayout.SOUTH);
    return p;
  }

  private JComponent previewPanel() {
    JPanel p = new JPanel(new BorderLayout(0, 0));
    p.setBackground(DEEP);
    JPanel head = new JPanel(new BorderLayout());
    head.setBackground(BG);
    head.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, LINE), BorderFactory.createEmptyBorder(8, 14, 8, 14)));
    head.add(clipInfo, BorderLayout.WEST);
    // the selected clip's speed (slow motion below 1x)
    JPanel speedRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
    speedRow.setOpaque(false);
    speedRow.add(label("Clip speed", false, 12f, MUTED));
    speed.onPick = () -> {
      Clip c = current();
      if (c == null) return;
      c.speed = parseDouble(speed.value(), 1);
      changed();
    };
    speedRow.add(speed);
    speedRow.add(Box.createHorizontalStrut(10));
    JPanel headRight = new JPanel(new BorderLayout());
    headRight.setOpaque(false);
    headRight.add(speedRow, BorderLayout.CENTER);
    headRight.add(previews, BorderLayout.EAST);
    head.add(headRight, BorderLayout.EAST);
    previews.setToolTipText("Has the game make small pictures of the recordings in the edit, for this preview and the timeline");
    previews.addActionListener(e -> makePreviews());
    p.add(head, BorderLayout.NORTH);
    p.add(preview, BorderLayout.CENTER);
    return p;
  }

  private JComponent timelinePanel() {
    JPanel p = new JPanel(new BorderLayout(0, 0));
    p.setBackground(BG);
    p.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, LINE));
    JPanel bar0 = new JPanel(new BorderLayout());
    bar0.setBackground(BG);
    bar0.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, LINE), BorderFactory.createEmptyBorder(4, 10, 4, 10)));
    JPanel left = row();
    left.add(iconButton("plus", "Add the recording picked on the left", this::addSelectedRecording));
    left.add(iconButton("trash", "Delete the selected clip (DELETE)", this::removeSelected));
    left.add(Box.createHorizontalStrut(14));
    left.add(iconButton("markin", "Start the clip at the playhead (I)", this::markIn));
    left.add(iconButton("markout", "End the clip at the playhead (O)", this::markOut));
    left.add(iconButton("split", "Split at the playhead (S)", this::splitSelected));
    left.add(iconButton("dup", "Duplicate the selected clip", this::duplicateSelected));
    left.add(Box.createHorizontalStrut(14));
    left.add(iconButton("undo", "Undo (CTRL+Z)", this::undo));
    left.add(iconButton("redo", "Redo (CTRL+Y)", this::redo));
    left.add(Box.createHorizontalStrut(14));
    left.add(iconButton("killprev", "Previous kill (SHIFT+K)", () -> jumpKill(-1)));
    left.add(iconButton("killnext", "Next kill (K)", () -> jumpKill(1)));

    JPanel mid = row();
    mid.add(timeNow);
    mid.add(Box.createHorizontalStrut(10));
    mid.add(iconButton("prev", "Previous clip", this::prevClip));
    mid.add(play);
    mid.add(iconButton("next", "Next clip", this::nextClip));
    mid.add(Box.createHorizontalStrut(10));
    mid.add(timeTotal);
    JPanel right = row();
    right.add(iconButton("fit", "Fit the whole edit (CTRL + mouse wheel zooms)", () -> timeline.fit()));
    bar0.add(left, BorderLayout.WEST);
    JPanel center = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
    center.setOpaque(false);
    center.add(mid);
    bar0.add(center, BorderLayout.CENTER);
    bar0.add(right, BorderLayout.EAST);
    p.add(bar0, BorderLayout.NORTH);
    timeline.setPreferredSize(new Dimension(10, 150));
    p.add(timeline, BorderLayout.CENTER);
    return p;
  }

  /** A column that takes the scroll pane's width (no sideways scrolling). */
  private static final class Column extends JPanel implements javax.swing.Scrollable {
    Column() {
      setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    }

    public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    public int getScrollableUnitIncrement(java.awt.Rectangle r, int o, int d) { return 18; }
    public int getScrollableBlockIncrement(java.awt.Rectangle r, int o, int d) { return 120; }
    public boolean getScrollableTracksViewportWidth() { return true; }
    public boolean getScrollableTracksViewportHeight() { return false; }
  }

  private JComponent sidebar() {
    JPanel side = new JPanel(new BorderLayout(0, 10));
    side.setBackground(PANEL);
    side.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, LINE), BorderFactory.createEmptyBorder(6, 16, 14, 10)));
    side.setPreferredSize(new Dimension(330, 10));
    Column p = new Column();
    p.setBackground(PANEL);
    p.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 8));
    JScrollPane scroll = new JScrollPane(p, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    scroll.setBorder(BorderFactory.createEmptyBorder());
    scroll.getViewport().setBackground(PANEL);
    scroll.getVerticalScrollBar().setUnitIncrement(18);
    side.add(scroll, BorderLayout.CENTER);
    JPanel bottom = new JPanel();
    bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
    bottom.setOpaque(false);
    bottom.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6));
    side.add(bottom, BorderLayout.SOUTH);
    p.add(section("VIDEO"));
    p.add(stacked("Frame rate", fps));
    p.add(stacked("Size", size));
    p.add(section("MOTION BLUR"));
    p.add(setting(null, blur));
    p.add(stacked("Subframes per frame", subframes));
    p.add(section("REPLAY"));
    p.add(setting(null, chests));
    if (Platform.windows()) p.add(setting(null, hideGame));
    p.add(section("SAVE AS"));
    style(output);
    JPanel out = new JPanel(new BorderLayout(6, 0));
    out.setOpaque(false);
    out.add(output, BorderLayout.CENTER);
    JButton browse = button("...", false);
    browse.setToolTipText("Choose where to save the video");
    browse.addActionListener(e -> chooseOutput());
    out.add(browse, BorderLayout.EAST);
    out.setAlignmentX(LEFT_ALIGNMENT);
    out.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
    p.add(out);
    p.add(section("LOOK  (this video only)"));
    p.add(stacked("Field of view", lookFov));
    p.add(stacked("Render distance (chunks)", lookDistance));
    p.add(stacked("Brightness", lookBright));
    p.add(stacked("Particles", lookParticles));
    p.add(stacked("View bobbing", lookBobbing));
    p.add(stacked("Time of day", lookTime));
    p.add(stacked("Weather", lookWeather));
    lookPack.setFont(fonts.font(false, 12f));
    lookPack.setBackground(BG);
    lookPack.setForeground(TEXT);
    lookPack.setFocusable(false);
    lookPack.addItem("As in my game");
    File[] packs = new File(gameDir, "resourcepacks").listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".zip") || new File(d, n).isDirectory());
    if (packs != null) {
      Arrays.sort(packs, Comparator.comparing(f -> f.getName().toLowerCase(Locale.ROOT)));
      for (File f : packs) lookPack.addItem(f.getName());
    }
    lookPack.addActionListener(e -> saveProject());
    p.add(stacked("Resource pack", lookPack));
    p.add(section("HIDE"));
    JPanel hides = new JPanel(new java.awt.GridLayout(0, 2, 6, 2));
    hides.setOpaque(false);
    for (JCheckBox c : Arrays.asList(hideHud, hideHand, hideCrosshair, hideChat, hideScoreboard, hideNametags)) {
      c.addActionListener(e -> saveProject());
      hides.add(c);
    }
    hides.setAlignmentX(LEFT_ALIGNMENT);
    hides.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
    p.add(hides);
    p.add(section("LUKA HUD"));
    JPanel parts = new JPanel(new java.awt.GridLayout(0, 2, 6, 2));
    parts.setOpaque(false);
    Map<String, Boolean> inGame = hudModulesInGame();
    for (String name : HUD_MODULES) {
      if (!inGame.containsKey(name)) continue;
      JCheckBox c = check(name, inGame.get(name));
      c.addActionListener(e -> saveProject());
      hudParts.put(name, c);
      parts.add(c);
    }
    if (hudParts.isEmpty()) parts.add(label("No luka HUD settings found yet (play once).", false, 11f, FAINT));
    parts.setAlignmentX(LEFT_ALIGNMENT);
    parts.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24 * (hudParts.size() / 2 + 1)));
    p.add(parts);
    JLabel hudNote = label("Ticked = shown in the video. Starts as in your game.", false, 11f, FAINT);
    hudNote.setAlignmentX(LEFT_ALIGNMENT);
    p.add(hudNote);
    p = null;
    JPanel b = bottom;
    render.setFont(fonts.font(true, 14f));
    render.setAlignmentX(LEFT_ALIGNMENT);
    render.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
    render.addActionListener(e -> startRender());
    b.add(render);
    b.add(Box.createVerticalStrut(10));
    bar.setForeground(PURPLE);
    bar.setBackground(BG);
    bar.setBorderPainted(false);
    bar.setAlignmentX(LEFT_ALIGNMENT);
    bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 6));
    bar.setPreferredSize(new Dimension(10, 6));
    b.add(bar);
    b.add(Box.createVerticalStrut(8));
    status.setAlignmentX(LEFT_ALIGNMENT);
    status.setEditable(false);
    status.setFocusable(false);
    status.setOpaque(false);
    status.setLineWrap(true);
    status.setWrapStyleWord(true);
    status.setFont(fonts.font(false, 12f));
    status.setForeground(MUTED);
    status.setBorder(BorderFactory.createEmptyBorder());
    status.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
    status.setPreferredSize(new Dimension(268, 64));
    b.add(status);
    showFile.setEnabled(false);
    showFile.setAlignmentX(LEFT_ALIGNMENT);
    showFile.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
    showFile.addActionListener(e -> showOutput());
    b.add(showFile);
    fps.set("60");
    size.set("1920x1080");
    subframes.set("16");
    blur.addActionListener(e -> subframes.setEnabled(blur.isSelected()));
    setStatus("Add recordings from the list on the left.");
    return side;
  }

  /** luka's modules and whether each is on, from the game's config/cbui.json (empty when it is not there). */
  @SuppressWarnings("unchecked")
  private Map<String, Boolean> hudModulesInGame() {
    Map<String, Boolean> out = new LinkedHashMap<>();
    try {
      String json = new String(java.nio.file.Files.readAllBytes(new File(gameDir, "config/cbui.json").toPath()), StandardCharsets.UTF_8);
      Object modules = ((Map<String, Object>) Installer.Json.parse(json)).get("modules");
      if (modules instanceof Map) {
        for (Map.Entry<String, Object> e : ((Map<String, Object>) modules).entrySet()) {
          if (e.getValue() instanceof Map) out.put(e.getKey(), Boolean.TRUE.equals(((Map<String, Object>) e.getValue()).get("enabled")));
        }
      }
    } catch (Exception ignored) {
      // not played yet, or unreadable: no HUD choices
    }
    return out;
  }

  /** The look options as render job keys (RenderLook in SubFrame); only what differs from the game. */
  private void putLook(Properties p) {
    putIf(p, "look.fov", lookFov.value());
    putIf(p, "look.renderDistance", lookDistance.value());
    putIf(p, "look.brightness", lookBright.value());
    putIf(p, "look.particles", lookParticles.value());
    putIf(p, "look.bobbing", lookBobbing.value());
    putIf(p, "look.time", lookTime.value());
    putIf(p, "look.weather", lookWeather.value());
    if (lookPack.getSelectedIndex() > 0) p.setProperty("look.resourcePack", String.valueOf(lookPack.getSelectedItem()));
    if (hideHud.isSelected()) p.setProperty("hide.hud", "true");
    if (hideHand.isSelected()) p.setProperty("hide.hand", "true");
    if (hideCrosshair.isSelected()) p.setProperty("hide.crosshair", "true");
    if (hideChat.isSelected()) p.setProperty("hide.chat", "true");
    if (hideScoreboard.isSelected()) p.setProperty("hide.scoreboard", "true");
    if (hideNametags.isSelected()) p.setProperty("hide.nametags", "true");
    for (Map.Entry<String, JCheckBox> e : hudParts.entrySet()) p.setProperty("hud." + e.getKey(), Boolean.toString(e.getValue().isSelected()));
  }

  private static void putIf(Properties p, String key, String value) {
    if (value != null && !value.trim().isEmpty()) p.setProperty(key, value.trim());
  }

  /** Back from the project file. */
  private void getLook(Properties p) {
    lookFov.set(p.getProperty("look.fov", ""));
    lookDistance.set(p.getProperty("look.renderDistance", ""));
    lookBright.set(p.getProperty("look.brightness", ""));
    lookParticles.set(p.getProperty("look.particles", ""));
    lookBobbing.set(p.getProperty("look.bobbing", ""));
    lookTime.set(p.getProperty("look.time", ""));
    lookWeather.set(p.getProperty("look.weather", ""));
    String pack = p.getProperty("look.resourcePack", "");
    lookPack.setSelectedIndex(0);
    for (int i = 1; i < lookPack.getItemCount(); i++) if (lookPack.getItemAt(i).equals(pack)) lookPack.setSelectedIndex(i);
    hideHud.setSelected(Boolean.parseBoolean(p.getProperty("hide.hud", "false")));
    hideHand.setSelected(Boolean.parseBoolean(p.getProperty("hide.hand", "false")));
    hideCrosshair.setSelected(Boolean.parseBoolean(p.getProperty("hide.crosshair", "false")));
    hideChat.setSelected(Boolean.parseBoolean(p.getProperty("hide.chat", "false")));
    hideScoreboard.setSelected(Boolean.parseBoolean(p.getProperty("hide.scoreboard", "false")));
    hideNametags.setSelected(Boolean.parseBoolean(p.getProperty("hide.nametags", "false")));
    for (Map.Entry<String, JCheckBox> e : hudParts.entrySet()) {
      String v = p.getProperty("hud." + e.getKey());
      if (v != null) e.getValue().setSelected(Boolean.parseBoolean(v));
    }
  }

  private JComponent section(String text) {
    JLabel l = label(text, true, 11f, LIGHT);
    l.setBorder(BorderFactory.createEmptyBorder(14, 0, 6, 0));
    l.setAlignmentX(LEFT_ALIGNMENT);
    return l;
  }

  /** A setting with its name above and the control below, both full width. */
  private JComponent stacked(String name, JComponent control) {
    JPanel r = new JPanel(new BorderLayout(0, 5));
    r.setOpaque(false);
    r.setBorder(BorderFactory.createEmptyBorder(3, 0, 6, 0));
    r.add(label(name, false, 12f, MUTED), BorderLayout.NORTH);
    r.add(control, BorderLayout.CENTER);
    r.setAlignmentX(LEFT_ALIGNMENT);
    r.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
    return r;
  }

  /**
   * A row of choice chips (the picked one in purple), and with a box width a text box beside them for any other value
   * (typing there unpicks the chips). values are what the chips stand for (null: their labels).
   */
  private final class Choice extends JPanel {
    private final String[] labels, values;
    private final List<JButton> chips = new ArrayList<>();
    private final JTextField box;
    private String value = "";
    /** Run when the player picks a chip (not when set() is called). */
    Runnable onPick;

    Choice(String[] labels, String[] values, int boxWidth) {
      super(new FlowLayout(FlowLayout.LEFT, 0, 0));
      this.labels = labels;
      this.values = values == null ? labels : values;
      setOpaque(false);
      for (int i = 0; i < labels.length; i++) {
        final String v = this.values[i];
        JButton b = new JButton(labels[i]);
        b.setFont(fonts.font(false, 12f));
        b.setFocusable(false);
        b.setFocusPainted(false);
        b.setContentAreaFilled(false);
        b.setOpaque(true);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addActionListener(e -> {
          set(v);
          if (onPick != null) onPick.run();
          saveProject();
        });
        chips.add(b);
        add(b);
      }
      if (boxWidth > 0) {
        box = new JTextField();
        style(box);
        box.setPreferredSize(new Dimension(boxWidth, 28));
        box.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
          public void insertUpdate(javax.swing.event.DocumentEvent e) { typed(); }
          public void removeUpdate(javax.swing.event.DocumentEvent e) { typed(); }
          public void changedUpdate(javax.swing.event.DocumentEvent e) { typed(); }
        });
        add(Box.createHorizontalStrut(6));
        add(box);
      } else {
        box = null;
      }
      paintChips();
    }

    private boolean setting;

    private void typed() {
      if (setting) return;
      value = box.getText().trim();
      paintChips();
    }

    String value() {
      return value;
    }

    void set(String v) {
      value = v == null ? "" : v.trim();
      if (box != null) {
        setting = true;
        box.setText(value);
        setting = false;
      }
      paintChips();
    }

    @Override public void setEnabled(boolean on) {
      super.setEnabled(on);
      for (JButton b : chips) b.setEnabled(on);
      if (box != null) box.setEnabled(on);
      paintChips();
    }

    private void paintChips() {
      for (int i = 0; i < chips.size(); i++) {
        JButton b = chips.get(i);
        boolean on = values[i].equalsIgnoreCase(value);
        b.setBackground(on ? PURPLE_DARK : BG);
        b.setForeground(!isEnabled() ? FAINT : on ? Color.WHITE : TEXT);
        b.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(on ? PURPLE : LINE),
            BorderFactory.createEmptyBorder(5, 9, 5, 9)));
      }
    }
  }

  private JComponent setting(String name, JComponent control) {
    JPanel r = new JPanel(new BorderLayout(8, 0));
    r.setOpaque(false);
    r.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
    if (name != null) {
      r.add(label(name, false, 12f, TEXT), BorderLayout.WEST);
      JPanel c = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
      c.setOpaque(false);
      c.add(control);
      r.add(c, BorderLayout.EAST);
    } else {
      r.add(control, BorderLayout.WEST);
    }
    r.setAlignmentX(LEFT_ALIGNMENT);
    r.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
    return r;
  }

  private void setStatus(String text) {
    status.setText(text);
    statusText = text;
  }

  private String statusText = "";

  // ---- library ----

  private void refreshLibrary(boolean loadProject) {
    File[] files = recordings.listFiles((d, n) -> n.endsWith(".sfr"));
    library.clear();
    if (files == null || files.length == 0) {
      setStatus("No recordings yet. In game, F6 starts recording and F7 stops it.");
      if (loadProject) loadProject();
      return;
    }
    Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
    List<Recording> found = new ArrayList<>();
    for (File f : files) {
      Recording r = new Recording(f);
      library.addElement(r);
      found.add(r);
    }
    if (loadProject) loadProject();
    new Thread(() -> {
      for (Recording r : found) {
        readInfo(r);
        SwingUtilities.invokeLater(() -> {
          libraryList.repaint();
          timeline.repaint();
          updateLabels();
        });
      }
    }, "luka-replay-info").start();
  }

  private Recording recordingFor(File f) {
    for (int i = 0; i < library.size(); i++) {
      if (library.get(i).file.equals(f)) return library.get(i);
    }
    return null;
  }

  /** Reads the player name and the length (first to last rendered frame) without loading the whole recording. */
  private static void readInfo(Recording r) {
    String key = r.file.getAbsolutePath() + "|" + r.file.lastModified() + "|" + r.file.length();
    synchronized (INFO) {
      Recording known = INFO.get(key);
      if (known != null) {
        r.length = known.length;
        r.player = known.player;
        r.kills = known.kills;
        r.problem = known.problem;
        return;
      }
    }
    try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(r.file), 1 << 16), 1 << 16))) {
      if (in.readInt() != MAGIC) {
        r.problem = "not a recording";
        return;
      }
      int version = in.readInt();
      if (version != FORMAT) {
        r.problem = "made by an older luka (format " + version + "), cannot be played";
        return;
      }
      r.player = in.readUTF();
      in.readUTF(); // uuid
      in.readFloat(); // fov
      in.readInt(); // render distance
      in.readBoolean(); // view bobbing
      in.readLong(); // seed
      long first = Long.MIN_VALUE, last = Long.MIN_VALUE;
      byte[] skip = new byte[1 << 16];
      List<long[]> killTimes = new ArrayList<>();
      List<String> killNames = new ArrayList<>();
      try {
        while (true) {
          int len = in.readInt();
          if (len <= 0) continue;
          int type = in.readUnsignedByte();
          int rest = len - 1;
          if (type == REC_FRAME && rest >= 8) {
            long t = in.readLong();
            rest -= 8;
            if (first == Long.MIN_VALUE) first = t;
            last = t;
          } else if (type == REC_PACKET && rest >= 20) {
            long t = in.readLong();
            in.readInt(); // tick
            int id = in.readInt(), dlen = in.readInt();
            rest -= 20;
            if (id == CHAT_PACKET && dlen > 0 && dlen <= rest && dlen < (1 << 20)) {
              byte[] data = new byte[dlen];
              in.readFully(data);
              rest -= dlen;
              String victim = killIn(chatText(data), r.player);
              if (victim != null) {
                killTimes.add(new long[] {t});
                killNames.add(victim);
              }
            }
          }
          while (rest > 0) {
            int n = in.read(skip, 0, Math.min(rest, skip.length));
            if (n < 0) throw new EOFException();
            rest -= n;
          }
        }
      } catch (EOFException end) {
        // end of the recording
      } catch (java.util.zip.ZipException truncated) {
        // a recording cut off (the game was killed): what was read still plays
      }
      if (first == Long.MIN_VALUE || last <= first) {
        r.problem = "has no frames";
        return;
      }
      r.length = (last - first) / 1e9;
      List<Kill> kills = new ArrayList<>();
      long lastKill = Long.MIN_VALUE;
      for (int i = 0; i < killTimes.size(); i++) {
        long t = killTimes.get(i)[0];
        if (t < first) continue;
        // the same death announced twice
        if (t - lastKill < 100_000_000L && !kills.isEmpty() && kills.get(kills.size() - 1).victim.equalsIgnoreCase(killNames.get(i))) continue;
        lastKill = t;
        kills.add(new Kill((t - first) / 1e9, killNames.get(i)));
      }
      r.kills = kills;
      synchronized (INFO) {
        INFO.put(key, r);
      }
    } catch (IOException e) {
      r.problem = "could not be read";
    }
  }

  // ---- kills from the recorded chat (as SubFrame's KillMarks finds them in the game) ----

  private static final String VERBS = "slain|shot|killed|blown up|fireballed|pummeled|squashed|impaled|speared|stung|doomed|"
      + "finished off|knocked|burned|burnt|struck|obliterated|cooked|hit|slaughtered|eliminated|murdered|destroyed|executed";
  /** English texts of the vanilla death messages a client would show for these translation keys. */
  private static final Map<String, String> DEATHS = new HashMap<>();

  static {
    DEATHS.put("death.attack.player", "%1$s was slain by %2$s");
    DEATHS.put("death.attack.player.item", "%1$s was slain by %2$s using %3$s");
    DEATHS.put("death.attack.mob", "%1$s was slain by %2$s");
    DEATHS.put("death.attack.arrow", "%1$s was shot by %2$s");
    DEATHS.put("death.attack.arrow.item", "%1$s was shot by %2$s using %3$s");
    DEATHS.put("death.attack.thrown", "%1$s was pummeled by %2$s");
    DEATHS.put("death.attack.thrown.item", "%1$s was pummeled by %2$s using %3$s");
    DEATHS.put("death.attack.fireball", "%1$s was fireballed by %2$s");
    DEATHS.put("death.attack.fireball.item", "%1$s was fireballed by %2$s using %3$s");
    DEATHS.put("death.attack.indirectMagic", "%1$s was killed by %2$s using magic");
    DEATHS.put("death.attack.indirectMagic.item", "%1$s was killed by %2$s using %3$s");
    DEATHS.put("death.attack.explosion.player", "%1$s was blown up by %2$s");
    DEATHS.put("death.fell.killer", "%1$s was doomed to fall");
    DEATHS.put("death.fell.assist", "%1$s was doomed to fall by %2$s");
    DEATHS.put("death.fell.assist.item", "%1$s was doomed to fall by %2$s using %3$s");
    DEATHS.put("death.fell.finish", "%1$s fell too far and was finished by %2$s");
    DEATHS.put("death.fell.finish.item", "%1$s fell too far and was finished by %2$s using %3$s");
  }

  /** The text of a recorded chat packet: its JSON message, flattened the way the game shows it (colour codes removed). */
  private static String chatText(byte[] data) {
    try {
      int len = 0, shift = 0, i = 0;
      while (i < data.length && i < 5) {
        int b = data[i++] & 0xFF;
        len |= (b & 0x7F) << shift;
        shift += 7;
        if ((b & 0x80) == 0) break;
      }
      if (len <= 0 || i + len > data.length) return "";
      String json = new String(data, i, len, StandardCharsets.UTF_8);
      return flatten(Installer.Json.parse(json)).replaceAll("\u00a7.", "").trim();
    } catch (Exception e) {
      return "";
    }
  }

  @SuppressWarnings("unchecked")
  private static String flatten(Object o) {
    if (o == null) return "";
    if (o instanceof String) return (String) o;
    if (o instanceof List) {
      StringBuilder sb = new StringBuilder();
      for (Object x : (List<Object>) o) sb.append(flatten(x));
      return sb.toString();
    }
    if (!(o instanceof Map)) return String.valueOf(o);
    Map<String, Object> m = (Map<String, Object>) o;
    StringBuilder sb = new StringBuilder();
    if (m.get("text") != null) sb.append(flatten(m.get("text")));
    if (m.get("translate") != null) {
      List<Object> with = m.get("with") instanceof List ? (List<Object>) m.get("with") : new ArrayList<>();
      String template = DEATHS.get(String.valueOf(m.get("translate")));
      if (template == null) {
        for (Object w : with) sb.append(flatten(w)).append(' ');
      } else {
        String out = template;
        for (int k = 0; k < with.size(); k++) out = out.replace("%" + (k + 1) + "$s", flatten(with.get(k)));
        sb.append(out);
      }
    }
    if (m.get("extra") instanceof List) for (Object x : (List<Object>) m.get("extra")) sb.append(flatten(x));
    return sb.toString();
  }

  /** The victim when this chat line says the player killed someone ("Bob was slain by me", "me killed Bob"), else null. */
  static String killIn(String text, String me) {
    if (text == null || text.isEmpty() || me == null || me.isEmpty()) return null;
    java.util.regex.Matcher m = java.util.regex.Pattern.compile("^\\W*(?:\\[[^\\]]*\\]\\s*)*(\\w{3,16}) (?:was|were|got) (?:" + VERBS
        + ")(?: \\w+)? by " + java.util.regex.Pattern.quote(me) + "\\b.*", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(text);
    if (!m.matches()) {
      m = java.util.regex.Pattern.compile("^\\W*(?:\\[[^\\]]*\\]\\s*)*" + java.util.regex.Pattern.quote(me) + " (?:" + VERBS
          + "|slew|slayed|took down|ended) (\\w{3,16})\\b.*", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(text);
      if (!m.matches()) return null;
    }
    String victim = m.group(1);
    return victim.equalsIgnoreCase(me) ? null : victim;
  }

  private void addSelectedRecording() {
    Recording r = libraryList.getSelectedValue();
    if (r == null) {
      setStatus("Pick a recording in the list on the left first.");
      return;
    }
    if (r.problem != null) {
      setStatus(r.name() + " " + r.problem + ".");
      return;
    }
    if (r.length < 0) readInfo(r);
    if (r.length < MIN_CLIP) {
      setStatus(r.name() + " is too short to use.");
      return;
    }
    clips.add(new Clip(r, 0, r.length));
    select(clips.size() - 1);
    seek(seqStart(selected));
    changed();
  }

  // ---- the edit: clips end to end ----

  private Clip current() {
    return selected >= 0 && selected < clips.size() ? clips.get(selected) : null;
  }

  private double total() {
    double t = 0;
    for (Clip c : clips) t += c.dur();
    return t;
  }

  /** Where clip i starts in the edit. */
  private double seqStart(int i) {
    double t = 0;
    for (int k = 0; k < i && k < clips.size(); k++) t += clips.get(k).dur();
    return t;
  }

  /** The clip under this moment of the edit (the last one at the very end); -1 with no clips. */
  private int clipAt(double t) {
    double at = 0;
    for (int i = 0; i < clips.size(); i++) {
      double len = clips.get(i).dur();
      if (t < at + len) return i;
      at += len;
    }
    return clips.size() - 1;
  }

  /** The moment in the recording for this moment of the edit. */
  private double recTime(int i, double t) {
    Clip c = clips.get(i);
    return clamp(c.start + (t - seqStart(i)) * c.speed, c.start, c.end);
  }

  private void select(int i) {
    selected = clips.isEmpty() ? -1 : Math.max(0, Math.min(clips.size() - 1, i));
    timeline.repaint();
    updateLabels();
  }

  private void seek(double t) {
    playTime = clamp(t, 0, total());
    int i = clipAt(playTime);
    if (i >= 0 && player == null) selected = i;
    timeline.repaint();
    preview.repaint();
    updateLabels();
  }

  private void changed() {
    playTime = clamp(playTime, 0, total());
    timeline.repaint();
    preview.repaint();
    updateLabels();
    saveProject();
    record();
  }

  private void updateLabels() {
    timeNow.setText(clock(playTime));
    timeTotal.setText(clock(total()));
    Clip c = current();
    clipInfo.setText(c == null ? "No clips yet: double-click a recording on the left."
        : "Clip " + (selected + 1) + " of " + clips.size() + ":  " + clock(c.start) + " - " + clock(c.end));
    clipInfo.setToolTipText(c == null ? null : c.rec.title() + ", " + clock(c.length()) + " of recording"
        + (c.speed != 1 ? " at " + speedLabel(c.speed) + " = " + clock(c.dur()) : ""));
    speed.set(c == null ? "1" : speedValue(c.speed));
    if (!rendering && !clips.isEmpty() && (statusText.startsWith("Add ") || statusText.startsWith("Ready"))) {
      setStatus("Ready: RENDER makes one video of the " + (clips.size() == 1 ? "clip" : clips.size() + " clips") + " (" + clock(total()) + ").");
    }
  }

  /** S: cuts the clip under the playhead in two there. */
  private void splitSelected() {
    if (clips.isEmpty()) return;
    int i = clipAt(playTime);
    Clip c = clips.get(i);
    double t = recTime(i, playTime);
    if (t < c.start + MIN_CLIP || t > c.end - MIN_CLIP) {
      setStatus("Move the playhead inside a clip (at least " + MIN_CLIP + " s from its ends) to split it there.");
      return;
    }
    stopPlay();
    Clip second = new Clip(c.rec, t, c.end, c.speed);
    c.end = t;
    clips.add(i + 1, second);
    select(i + 1);
    changed();
    setStatus("Split at " + clock(t) + " of the recording: clip " + (i + 1) + " and clip " + (i + 2) + ".");
  }

  /** I: the clip under the playhead starts there. */
  private void markIn() {
    if (clips.isEmpty()) return;
    int i = clipAt(playTime);
    Clip c = clips.get(i);
    c.start = clamp(recTime(i, playTime), 0, c.end - MIN_CLIP);
    select(i);
    playTime = seqStart(i);
    changed();
  }

  /** O: the clip under the playhead ends there. */
  private void markOut() {
    if (clips.isEmpty()) return;
    int i = clipAt(playTime);
    Clip c = clips.get(i);
    c.end = clamp(recTime(i, playTime), c.start + MIN_CLIP, c.rec.length);
    select(i);
    changed();
  }

  private void duplicateSelected() {
    Clip c = current();
    if (c == null) return;
    clips.add(selected + 1, new Clip(c.rec, c.start, c.end, c.speed));
    select(selected + 1);
    changed();
  }

  private void removeSelected() {
    if (current() == null) return;
    stopPlay();
    clips.remove(selected);
    select(Math.min(selected, clips.size() - 1));
    changed();
  }

  // ---- undo / redo: a snapshot of the clips after every change ----

  private static final class Snap {
    final List<Clip> clips = new ArrayList<>();
    final int selected;

    Snap(List<Clip> from, int selected) {
      for (Clip c : from) clips.add(new Clip(c.rec, c.start, c.end, c.speed));
      this.selected = selected;
    }

    boolean same(Snap o) {
      if (o.clips.size() != clips.size()) return false;
      for (int i = 0; i < clips.size(); i++) {
        Clip a = clips.get(i), b = o.clips.get(i);
        if (a.rec != b.rec || a.start != b.start || a.end != b.end || a.speed != b.speed) return false;
      }
      return true;
    }
  }

  private final List<Snap> history = new ArrayList<>();
  private int historyAt = -1;
  private boolean restoring;

  /** Called after each change of the clips (changed()): one undo step. */
  private void record() {
    if (restoring) return;
    Snap now = new Snap(clips, selected);
    if (historyAt >= 0 && history.get(historyAt).same(now)) return;
    while (history.size() > historyAt + 1) history.remove(history.size() - 1);
    history.add(now);
    if (history.size() > 300) history.remove(0);
    historyAt = history.size() - 1;
  }

  private void undo() {
    if (historyAt <= 0) {
      setStatus("Nothing to undo.");
      return;
    }
    restore(history.get(--historyAt));
    setStatus("Undone (" + historyAt + " more step" + (historyAt == 1 ? "" : "s") + " back).");
  }

  private void redo() {
    if (historyAt >= history.size() - 1) {
      setStatus("Nothing to redo.");
      return;
    }
    restore(history.get(++historyAt));
    setStatus("Redone.");
  }

  private void restore(Snap s) {
    stopPlay();
    restoring = true;
    try {
      clips.clear();
      for (Clip c : s.clips) clips.add(new Clip(c.rec, c.start, c.end, c.speed));
      select(s.selected);
      changed();
    } finally {
      restoring = false;
    }
  }

  /** K / SHIFT+K: the playhead to the next or previous kill in the edit. */
  private void jumpKill(int dir) {
    double best = Double.NaN, at = 0;
    for (Clip c : clips) {
      for (Kill k : c.rec.kills) {
        if (k.t < c.start || k.t > c.end) continue;
        double t = at + (k.t - c.start) / c.speed;
        if (dir > 0 && t > playTime + 0.02 && (Double.isNaN(best) || t < best)) best = t;
        if (dir < 0 && t < playTime - 0.02 && (Double.isNaN(best) || t > best)) best = t;
      }
      at += c.dur();
    }
    if (Double.isNaN(best)) {
      setStatus(dir > 0 ? "No more kills after the playhead." : "No kills before the playhead.");
      return;
    }
    stopPlay();
    seek(best);
  }

  /** "0.5", "1", "1.5": the speed chips' values. */
  private static String speedValue(double s) {
    return s == Math.rint(s) ? String.valueOf((int) s) : String.valueOf(s);
  }

  private static String speedLabel(double s) {
    return (s == Math.rint(s) ? String.valueOf((int) s) : String.valueOf(s)) + "x";
  }

  private void prevClip() {
    if (clips.isEmpty()) return;
    int i = clipAt(playTime);
    double s = seqStart(i);
    seek(playTime - s > 0.15 || i == 0 ? s : seqStart(i - 1));
  }

  private void nextClip() {
    if (clips.isEmpty()) return;
    int i = clipAt(playTime);
    seek(i + 1 < clips.size() ? seqStart(i + 1) : total());
  }

  /** SPACE: plays the edit's preview pictures from the playhead, in real time, across the clips. */
  private void togglePlay() {
    if (clips.isEmpty()) return;
    if (player != null) {
      stopPlay();
      return;
    }
    if (playTime >= total() - 0.05) playTime = 0;
    boolean any = false;
    for (Clip c : clips) any |= c.rec.frames != null;
    if (!any) setStatus("No pictures to play yet: press MAKE PREVIEWS first. The playhead still runs.");
    playLast = System.nanoTime();
    player = new Timer(15, e -> {
      long now = System.nanoTime();
      playTime += (now - playLast) / 1e9;
      playLast = now;
      if (playTime >= total()) {
        playTime = total();
        stopPlay();
      }
      selected = Math.max(0, clipAt(playTime));
      timeline.follow();
      timeline.repaint();
      preview.repaint();
      updateLabels();
    });
    player.start();
    play.setIcon(new Ico("pause"));
  }

  private void stopPlay() {
    if (player != null) player.stop();
    player = null;
    play.setIcon(new Ico("play"));
    timeline.repaint();
    preview.repaint();
  }

  // ---- preview ----

  /** The picture at the playhead, as large as fits, 16:9. */
  private final class PreviewView extends JComponent {
    @Override protected void paintComponent(Graphics g0) {
      Graphics2D g = (Graphics2D) g0;
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g.setColor(DEEP);
      g.fillRect(0, 0, getWidth(), getHeight());
      int pad = 16, w = getWidth() - 2 * pad, h = getHeight() - 2 * pad;
      if (w * 9 > h * 16) w = h * 16 / 9;
      else h = w * 9 / 16;
      int x = (getWidth() - w) / 2, y = (getHeight() - h) / 2;
      g.setColor(Color.BLACK);
      g.fillRect(x, y, w, h);
      g.setFont(fonts.font(false, 13f));
      if (clips.isEmpty()) {
        center(g, "Double-click a recording on the left to start an edit.", x, y, w, h, FAINT);
        return;
      }
      int i = clipAt(playTime);
      Clip c = clips.get(i);
      ensureFrames(c.rec);
      BufferedImage img = c.rec.frameAt(recTime(i, playTime));
      if (img != null) g.drawImage(img, x, y, w, h, null);
      else center(g, c.rec.loadingFrames ? "Loading the preview..." : "No preview yet: press MAKE PREVIEWS (top right).", x, y, w, h, FAINT);
    }

    private void center(Graphics2D g, String s, int x, int y, int w, int h, Color color) {
      g.setColor(color);
      FontMetrics fm = g.getFontMetrics();
      g.drawString(s, x + (w - fm.stringWidth(s)) / 2, y + h / 2 + fm.getAscent() / 2 - 2);
    }
  }

  // ---- timeline ----

  /**
   * The ruler and one track with the clips end to end, each with its picture strip and name; the playhead crosses both.
   * Ruler: click or drag to move the playhead. Clip edges: drag to trim. Clip body: click to select and move the playhead,
   * drag to reorder. Right-click: split, start, end, delete. Wheel scrolls, CTRL + wheel zooms.
   */
  private final class TimelineView extends JComponent {
    private static final int HEAD = 56, RULER = 26, TRACK_TOP = RULER + 10, TRACK_H = 96, EDGE = 7;
    private double pps = 40, viewStart;
    private boolean autoFit = true;
    private int mode; // 0 none, 1 scrub, 2 trim start, 3 trim end, 4 press/move a clip
    private int pressClip = -1, pressX, dropAt = -1;
    private double pressStart, pressEnd, pressPps;
    private boolean moved;

    TimelineView() {
      MouseAdapter m = new MouseAdapter() {
        @Override public void mousePressed(MouseEvent e) {
          requestFocusInWindow();
          if (clips.isEmpty()) return;
          if (SwingUtilities.isRightMouseButton(e)) {
            int i = clipIndexAt(e.getX());
            if (i >= 0) menu(i, e.getX(), e.getY());
            return;
          }
          pressX = e.getX();
          pressPps = pps;
          moved = false;
          if (e.getY() < TRACK_TOP || e.getX() < HEAD) {
            stopPlay();
            mode = 1;
            seek(time(e.getX()));
            return;
          }
          int i = clipIndexAt(e.getX());
          if (i < 0) {
            mode = 1;
            seek(time(e.getX()));
            return;
          }
          Clip c = clips.get(i);
          pressClip = i;
          pressStart = c.start;
          pressEnd = c.end;
          int xs = x(seqStart(i)), xe = x(seqStart(i) + c.dur());
          mode = e.getX() - xs <= EDGE ? 2 : xe - e.getX() <= EDGE ? 3 : 4;
          select(i);
        }

        @Override public void mouseDragged(MouseEvent e) {
          if (mode == 1) {
            seek(time(e.getX()));
            return;
          }
          if (pressClip < 0) return;
          Clip c = clips.get(pressClip);
          double dt = (e.getX() - pressX) / pressPps * c.speed;
          if (mode == 2) {
            c.start = clamp(pressStart + dt, 0, c.end - MIN_CLIP);
            playTime = seqStart(pressClip);
          } else if (mode == 3) {
            c.end = clamp(pressEnd + dt, c.start + MIN_CLIP, c.rec.length);
            playTime = seqStart(pressClip) + c.dur() - 0.001;
          } else if (mode == 4 && Math.abs(e.getX() - pressX) > 5) {
            moved = true;
            dropAt = gapAt(e.getX());
          }
          repaint();
          preview.repaint();
          updateLabels();
        }

        @Override public void mouseReleased(MouseEvent e) {
          if (mode == 2 || mode == 3) {
            changed();
          } else if (mode == 4) {
            if (moved && dropAt >= 0 && dropAt != pressClip && dropAt != pressClip + 1) {
              Clip c = clips.remove(pressClip);
              int to = dropAt > pressClip ? dropAt - 1 : dropAt;
              clips.add(to, c);
              select(to);
              playTime = seqStart(to);
              changed();
            } else if (!moved) {
              seek(time(e.getX()));
            }
          }
          mode = 0;
          pressClip = dropAt = -1;
          repaint();
        }

        @Override public void mouseMoved(MouseEvent e) {
          String tip = null;
          double at = 0;
          for (Clip c : clips) {
            for (Kill k : c.rec.kills) {
              if (k.t < c.start || k.t > c.end) continue;
              if (Math.abs(x(at + (k.t - c.start) / c.speed) - e.getX()) <= 5) tip = "Kill: " + k.victim + "  (" + clock(at + (k.t - c.start) / c.speed) + ")";
            }
            at += c.dur();
          }
          setToolTipText(tip);
          int i = e.getY() >= TRACK_TOP ? clipIndexAt(e.getX()) : -1;
          int cursor = Cursor.DEFAULT_CURSOR;
          if (i >= 0) {
            int xs = x(seqStart(i)), xe = x(seqStart(i) + clips.get(i).dur());
            cursor = e.getX() - xs <= EDGE || xe - e.getX() <= EDGE ? Cursor.E_RESIZE_CURSOR : Cursor.HAND_CURSOR;
          }
          setCursor(Cursor.getPredefinedCursor(cursor));
        }

        @Override public void mouseWheelMoved(MouseWheelEvent e) {
          if (e.isControlDown()) {
            double at = time(e.getX());
            pps = clamp(pps * Math.pow(1.15, -e.getWheelRotation()), 2, 2000);
            autoFit = false;
            viewStart = Math.max(0, at - (e.getX() - HEAD) / pps);
          } else {
            viewStart = clamp(viewStart + e.getWheelRotation() * 60 / pps, 0, Math.max(0, total() - 1));
          }
          repaint();
        }
      };
      addMouseListener(m);
      addMouseMotionListener(m);
      addMouseWheelListener(m);
    }

    void fit() {
      autoFit = true;
      viewStart = 0;
      repaint();
    }

    /** While playing, keep the playhead in view. */
    void follow() {
      if (autoFit) return;
      double span = (getWidth() - HEAD) / pps;
      if (playTime > viewStart + span * 0.9 || playTime < viewStart) viewStart = Math.max(0, playTime - span * 0.1);
    }

    private int x(double t) {
      return HEAD + (int) Math.round((t - viewStart) * pps);
    }

    private double time(int x) {
      return clamp(viewStart + (x - HEAD) / pps, 0, total());
    }

    private int clipIndexAt(int x) {
      double t = viewStart + (x - HEAD) / pps;
      if (t < 0 || t > total()) return -1;
      return clipAt(t);
    }

    private int gapAt(int x) {
      double t = viewStart + (x - HEAD) / pps;
      for (int i = 0; i < clips.size(); i++) {
        if (t < seqStart(i) + clips.get(i).dur() / 2) return i;
      }
      return clips.size();
    }

    private void menu(int i, int x, int y) {
      select(i);
      Clip c = clips.get(i);
      double seqT = time(x), rt = recTime(i, seqT);
      javax.swing.JPopupMenu m = new javax.swing.JPopupMenu();
      javax.swing.JMenuItem split = new javax.swing.JMenuItem("Split here");
      split.setEnabled(rt >= c.start + MIN_CLIP && rt <= c.end - MIN_CLIP);
      split.addActionListener(e -> {
        playTime = seqT;
        splitSelected();
      });
      javax.swing.JMenuItem start = new javax.swing.JMenuItem("Start the clip here");
      start.addActionListener(e -> {
        c.start = clamp(rt, 0, c.end - MIN_CLIP);
        playTime = seqStart(i);
        changed();
      });
      javax.swing.JMenuItem end = new javax.swing.JMenuItem("End the clip here");
      end.addActionListener(e -> {
        c.end = clamp(rt, c.start + MIN_CLIP, c.rec.length);
        changed();
      });
      javax.swing.JMenuItem dup = new javax.swing.JMenuItem("Duplicate");
      dup.addActionListener(e -> duplicateSelected());
      javax.swing.JMenuItem del = new javax.swing.JMenuItem("Delete");
      del.addActionListener(e -> removeSelected());
      m.add(split);
      m.add(start);
      m.add(end);
      m.addSeparator();
      m.add(dup);
      m.add(del);
      m.show(this, x, y);
    }

    @Override protected void paintComponent(Graphics g0) {
      Graphics2D g = (Graphics2D) g0;
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      int w = getWidth(), h = getHeight();
      double total = total();
      if (autoFit && mode == 0) pps = Math.max(2, (w - HEAD - 24) / Math.max(5.0, total));
      g.setColor(BG);
      g.fillRect(0, 0, w, h);
      // track header
      g.setColor(TRACK);
      g.fillRect(0, TRACK_TOP, w, TRACK_H);
      g.setColor(LINE);
      g.fillRect(HEAD - 1, 0, 1, h);
      g.setFont(fonts.font(true, 12f));
      g.setColor(MUTED);
      g.drawString("V1", 18, TRACK_TOP + TRACK_H / 2 + 5);
      Shape oldClip = g.getClip();
      g.clipRect(HEAD, 0, w - HEAD, h);
      // ruler
      double[] steps = {0.5, 1, 2, 5, 10, 15, 30, 60, 120, 300, 600};
      double step = steps[steps.length - 1];
      for (double s : steps) {
        if (s * pps >= 80) {
          step = s;
          break;
        }
      }
      g.setFont(fonts.font(false, 11f));
      double first = Math.floor(viewStart / step) * step;
      for (double t = first; x(t) < w; t += step) {
        int x = x(t);
        g.setColor(LINE);
        g.fillRect(x, RULER - 8, 1, 8);
        for (int k = 1; k < 5; k++) g.fillRect(x + (int) (k * step * pps / 5), RULER - 4, 1, 4);
        g.setColor(MUTED);
        g.drawString(clockShort(t), x + 4, RULER - 10);
      }
      g.setColor(LINE);
      g.fillRect(HEAD, RULER, w, 1);
      if (clips.isEmpty()) {
        g.setColor(FAINT);
        g.setFont(fonts.font(false, 12f));
        g.drawString("Your clips go here, one after another.", HEAD + 16, TRACK_TOP + TRACK_H / 2 + 4);
      }
      // clips
      double at = 0;
      for (int i = 0; i < clips.size(); i++) {
        Clip c = clips.get(i);
        int xs = x(at), xe = x(at + c.dur());
        int cw = Math.max(2, xe - xs - 2);
        int y = TRACK_TOP + 4, ch = TRACK_H - 8;
        g.setColor(CLIP);
        g.fillRoundRect(xs + 1, y, cw, ch, 6, 6);
        ensureFrames(c.rec);
        Shape before = g.getClip();
        g.clipRect(xs + 1, y + 20, cw, ch - 20);
        if (c.rec.frames != null) {
          int th = ch - 20, tw = Math.max(8, th * 16 / 9);
          for (int x = xs + 1; x < xs + 1 + cw; x += tw) {
            double t = c.start + (x + tw / 2.0 - xs) / pps * c.speed;
            BufferedImage img = c.rec.frameAt(Math.min(t, c.end));
            if (img != null) g.drawImage(img, x, y + 20, tw, th, null);
          }
        }
        g.setClip(before);
        Shape lab = g.getClip();
        g.clipRect(xs + 1, y, cw, 20);
        g.setFont(fonts.font(true, 11f));
        g.setColor(TEXT);
        g.drawString((i + 1) + ".  " + c.rec.title(), xs + 8, y + 14);
        g.setFont(fonts.font(false, 11f));
        g.setColor(MUTED);
        String len = (c.speed != 1 ? speedLabel(c.speed) + "  " : "") + clock(c.dur());
        g.drawString(len, xe - 8 - g.getFontMetrics().stringWidth(len), y + 14);
        g.setClip(lab);
        boolean sel = i == selected;
        g.setColor(sel ? PURPLE : new Color(0x3A3046));
        g.setStroke(new BasicStroke(sel ? 2f : 1f));
        g.drawRoundRect(xs + 1, y, cw, ch, 6, 6);
        if (sel) {
          // trim grips at both ends
          g.setColor(LIGHT);
          g.fillRoundRect(xs + 2, y + ch / 2 - 12, 4, 24, 3, 3);
          g.fillRoundRect(xs + cw - 4, y + ch / 2 - 12, 4, 24, 3, 3);
        }
        if (dropAt == i) {
          g.setColor(LIGHT);
          g.fillRect(xs - 2, TRACK_TOP - 2, 3, TRACK_H + 4);
        }
        at += c.dur();
      }
      // kill markers: a red mark on the ruler and a line down the clip
      at = 0;
      for (int i = 0; i < clips.size(); i++) {
        Clip c = clips.get(i);
        for (Kill k : c.rec.kills) {
          if (k.t < c.start || k.t > c.end) continue;
          int kx = x(at + (k.t - c.start) / c.speed);
          g.setColor(KILL);
          g.fillPolygon(new int[] {kx - 5, kx, kx + 5, kx}, new int[] {RULER - 6, RULER - 11, RULER - 6, RULER - 1}, 4);
          g.fillRect(kx - 1, TRACK_TOP + 4, 2, TRACK_H - 8);
        }
        at += c.dur();
      }
      if (dropAt == clips.size()) {
        g.setColor(LIGHT);
        g.fillRect(x(at) - 2, TRACK_TOP - 2, 3, TRACK_H + 4);
      }
      // playhead across the ruler and the track
      if (!clips.isEmpty()) {
        int px = x(playTime);
        g.setColor(PURPLE);
        g.fillRect(px - 1, RULER - 6, 2, h);
        Path2D head = new Path2D.Double();
        head.moveTo(px - 7, 2);
        head.lineTo(px + 7, 2);
        head.lineTo(px + 7, RULER - 12);
        head.lineTo(px, RULER - 4);
        head.lineTo(px - 7, RULER - 12);
        head.closePath();
        g.fill(head);
      }
      g.setClip(oldClip);
    }
  }

  // ---- previews ----

  private File previewDir() {
    return new File(recordings, ".previews");
  }

  /** The name the game renders a recording's preview to (its frames go to the same name with -frames). */
  private File previewVideo(Recording r) {
    return new File(previewDir(), r.name() + ".mp4");
  }

  private File previewFrames(Recording r) {
    return new File(previewDir(), r.name() + "-frames");
  }

  /** Written after a preview is made: the recording's time and size, so a changed recording gets a new one. */
  private File previewStamp(Recording r) {
    return new File(previewDir(), r.name() + ".stamp");
  }

  private static String stampOf(Recording r) {
    return r.file.lastModified() + " " + r.file.length() + " " + PREVIEW_FPS + "fps " + PREVIEW_W + "x" + PREVIEW_H;
  }

  private boolean previewFresh(Recording r) {
    try {
      return previewFrames(r).isDirectory()
          && stampOf(r).equals(new String(java.nio.file.Files.readAllBytes(previewStamp(r).toPath()), StandardCharsets.UTF_8).trim());
    } catch (IOException e) {
      return false;
    }
  }

  /** Loads a recording's preview pictures on a thread, once (if it has a fresh preview). Called while painting. */
  private void ensureFrames(Recording r) {
    if (r.frames != null || r.loadingFrames) return;
    long now = System.currentTimeMillis();
    if (now - r.previewCheckedMs < 2000) return;
    r.previewCheckedMs = now;
    if (!previewFresh(r)) return;
    r.loadingFrames = true;
    new Thread(() -> {
      try {
        File[] files = previewFrames(r).listFiles((d, n) -> n.endsWith(".png"));
        if (files == null) return;
        Arrays.sort(files, Comparator.comparing(File::getName));
        BufferedImage[] imgs = new BufferedImage[files.length];
        for (int i = 0; i < files.length; i++) {
          BufferedImage full = javax.imageio.ImageIO.read(files[i]);
          BufferedImage small = new BufferedImage(KEEP_W, KEEP_H, BufferedImage.TYPE_INT_RGB);
          Graphics2D g = small.createGraphics();
          g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
          g.drawImage(full, 0, 0, KEEP_W, KEEP_H, null);
          g.dispose();
          imgs[i] = small;
        }
        r.frames = imgs;
      } catch (IOException e) {
        // no pictures; the timeline stays plain
      } finally {
        r.loadingFrames = false;
        SwingUtilities.invokeLater(() -> {
          timeline.repaint();
          preview.repaint();
        });
      }
    }, "luka-replay-preview").start();
  }

  /** Has the game render small pictures of every recording in the edit (and the one picked on the left) that has none. */
  private void makePreviews() {
    if (rendering) return;
    List<Recording> todo = new ArrayList<>();
    for (Clip c : clips) {
      if (!todo.contains(c.rec) && !previewFresh(c.rec)) todo.add(c.rec);
    }
    Recording picked = libraryList.getSelectedValue();
    if (picked != null && picked.problem == null && !todo.contains(picked) && !previewFresh(picked)) todo.add(picked);
    if (todo.isEmpty()) {
      setStatus("Every recording in the edit already has a preview.");
      return;
    }
    Properties p = new Properties();
    p.setProperty("preview", "true");
    p.setProperty("fps", Integer.toString(PREVIEW_FPS));
    p.setProperty("width", Integer.toString(PREVIEW_W));
    p.setProperty("height", Integer.toString(PREVIEW_H));
    p.setProperty("blend", "false");
    p.setProperty("showContainers", Boolean.toString(chests.isSelected()));
    p.setProperty("clips", Integer.toString(todo.size()));
    for (int i = 0; i < todo.size(); i++) {
      Recording r = todo.get(i);
      p.setProperty("clip." + i + ".file", r.file.getAbsolutePath());
      p.setProperty("clip." + i + ".start", "0");
      p.setProperty("clip." + i + ".end", num(Math.max(MIN_CLIP, r.length)));
      p.setProperty("clip." + i + ".out", previewVideo(r).getAbsolutePath());
    }
    previewDir().mkdirs();
    pendingPreviews = todo;
    runJob(p, "Making previews");
  }

  private List<Recording> pendingPreviews;

  // ---- project file ----

  private File projectFile() {
    return new File(recordings, "editor-project.properties");
  }

  private void saveProject() {
    Properties p = new Properties();
    p.setProperty("clips", Integer.toString(clips.size()));
    for (int i = 0; i < clips.size(); i++) {
      Clip c = clips.get(i);
      p.setProperty("clip." + i + ".file", c.rec.file.getName());
      p.setProperty("clip." + i + ".start", num(c.start));
      p.setProperty("clip." + i + ".end", num(c.end));
      p.setProperty("clip." + i + ".speed", num(c.speed));
    }
    putLook(p);
    p.setProperty("fps", fps.value());
    p.setProperty("size", size.value());
    p.setProperty("blend", Boolean.toString(blur.isSelected()));
    p.setProperty("subframes", subframes.value());
    p.setProperty("showContainers", Boolean.toString(chests.isSelected()));
    p.setProperty("hideGame", Boolean.toString(hideGame.isSelected()));
    try {
      recordings.mkdirs();
      try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(projectFile()), StandardCharsets.UTF_8)) {
        p.store(w, "luka replay editor");
      }
    } catch (IOException ignored) {
      // the edit just is not remembered
    }
  }

  private void loadProject() {
    File f = projectFile();
    if (!f.isFile()) return;
    Properties p = new Properties();
    try (InputStream in = new FileInputStream(f)) {
      p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
    } catch (IOException e) {
      return;
    }
    int n = parseInt(p.getProperty("clips"), 0);
    for (int i = 0; i < n; i++) {
      Recording r = recordingFor(new File(recordings, p.getProperty("clip." + i + ".file", "")));
      if (r == null) continue;
      clips.add(new Clip(r, parseDouble(p.getProperty("clip." + i + ".start"), 0), parseDouble(p.getProperty("clip." + i + ".end"), 0),
          clamp(parseDouble(p.getProperty("clip." + i + ".speed"), 1), 0.05, 20)));
    }
    getLook(p);
    if (p.getProperty("fps") != null) fps.set(p.getProperty("fps"));
    if (p.getProperty("size") != null) size.set(p.getProperty("size"));
    blur.setSelected(Boolean.parseBoolean(p.getProperty("blend", "true")));
    subframes.setEnabled(blur.isSelected());
    if (p.getProperty("subframes") != null) subframes.set(p.getProperty("subframes"));
    chests.setSelected(Boolean.parseBoolean(p.getProperty("showContainers", "true")));
    hideGame.setSelected(Platform.windows() && Boolean.parseBoolean(p.getProperty("hideGame", "true")));
    select(0);
    changed();
  }

  // ---- render ----

  private void chooseOutput() {
    JFileChooser ch = new JFileChooser(new File(output.getText()).getParentFile());
    ch.setSelectedFile(new File(output.getText()));
    ch.setFileFilter(new FileNameExtensionFilter("MP4 video", "mp4"));
    if (ch.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
      String path = ch.getSelectedFile().getAbsolutePath();
      output.setText(path.toLowerCase(Locale.ROOT).endsWith(".mp4") ? path : path + ".mp4");
    }
  }

  private void startRender() {
    if (rendering) return;
    if (clips.isEmpty()) {
      setStatus("Add at least one clip first.");
      return;
    }
    for (Clip c : clips) {
      if (c.rec.length <= 0 || c.length() < MIN_CLIP) {
        setStatus("Clip " + c.rec.name() + " is not ready (still reading, or too short).");
        return;
      }
    }
    int fpsValue = parseInt(fps.value(), -1);
    if (fpsValue < 1 || fpsValue > 1_000_000) {
      setStatus("Type a frame rate between 1 and 1,000,000.");
      return;
    }
    int[] wh = parseSize(size.value());
    if (wh == null) {
      setStatus("Type the size as width x height, for example 1920x1080.");
      return;
    }
    File out = new File(output.getText().trim());
    if (out.getParentFile() == null || !out.getParentFile().isDirectory()) {
      setStatus("Choose where to save the video.");
      return;
    }
    String ffmpegProblem = ffmpegProblem();
    if (ffmpegProblem != null && !askForFfmpeg(ffmpegProblem)) {
      setStatus(ffmpegProblem);
      return;
    }
    Properties p = new Properties();
    p.setProperty("output", out.getAbsolutePath());
    p.setProperty("fps", Integer.toString(fpsValue));
    p.setProperty("width", Integer.toString(wh[0]));
    p.setProperty("height", Integer.toString(wh[1]));
    p.setProperty("blend", Boolean.toString(blur.isSelected()));
    p.setProperty("subframes", subframes.value());
    p.setProperty("showContainers", Boolean.toString(chests.isSelected()));
    p.setProperty("clips", Integer.toString(clips.size()));
    for (int i = 0; i < clips.size(); i++) {
      Clip c = clips.get(i);
      p.setProperty("clip." + i + ".file", c.rec.file.getAbsolutePath());
      p.setProperty("clip." + i + ".start", num(c.start));
      p.setProperty("clip." + i + ".end", num(c.end));
      p.setProperty("clip." + i + ".speed", num(c.speed));
    }
    putLook(p);
    saveProject();
    pendingPreviews = null;
    runJob(p, "Rendering");
  }

  /**
   * Videos need ffmpeg, which luka does not ship: SubFrame uses ffmpegPath from the game's config/subframe.properties, or
   * "ffmpeg" found on PATH. Null when it is there, else what to do (previews do not need it: they are PNG pictures).
   */
  private String ffmpegProblem() {
    String path = "ffmpeg";
    Properties p = new Properties();
    try (InputStream in = new FileInputStream(new File(gameDir, "config/subframe.properties"))) {
      p.load(new InputStreamReader(in, StandardCharsets.ISO_8859_1));
      path = p.getProperty("ffmpegPath", path).trim();
    } catch (IOException ignored) {
      // no settings yet: the default
    }
    String fix = " Get it from ffmpeg.org, then press RENDER and pick ffmpeg.exe.";
    if (path.contains("/") || path.contains("\\")) {
      return new File(path).isFile() ? null : "ffmpeg was not found at " + path + "." + fix;
    }
    String pathVar = System.getenv("PATH");
    if (pathVar != null) {
      for (String d : pathVar.split(File.pathSeparator)) {
        if (d.isEmpty()) continue;
        if (new File(d, path).isFile() || new File(d, path + ".exe").isFile()) return null;
      }
    }
    return "Rendering needs ffmpeg." + fix;
  }

  /** Lets the player pick ffmpeg; saves it as ffmpegPath in the game's SubFrame settings. True when one was picked. */
  private boolean askForFfmpeg(String problem) {
    setStatus(problem);
    JFileChooser ch = new JFileChooser();
    ch.setDialogTitle("luka needs ffmpeg to make videos: pick ffmpeg" + (Platform.windows() ? ".exe" : ""));
    if (ch.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return false;
    File f = ch.getSelectedFile();
    if (!f.isFile() || !f.getName().toLowerCase(Locale.ROOT).startsWith("ffmpeg")) {
      setStatus("That is not ffmpeg. " + problem);
      return false;
    }
    File config = new File(gameDir, "config/subframe.properties");
    Properties p = new Properties();
    try {
      if (config.isFile()) {
        try (InputStream in = new FileInputStream(config)) {
          p.load(in);
        }
      }
      p.setProperty("ffmpegPath", f.getAbsolutePath().replace('\\', '/'));
      config.getParentFile().mkdirs();
      try (FileOutputStream out = new FileOutputStream(config)) {
        p.store(out, "SubFrame");
      }
      return true;
    } catch (IOException e) {
      setStatus("Could not save the ffmpeg path: " + e.getMessage());
      return false;
    }
  }

  /** What the status line calls the running job ("Rendering", "Making previews"). */
  private String jobWhat = "Rendering";

  private void runJob(Properties p, String what) {
    jobDir = new File(recordings, ".editor-job");
    jobDir.mkdirs();
    File job = new File(jobDir, "job.properties"), statusFile = new File(jobDir, "status.properties");
    statusFile.delete();
    p.setProperty("status", statusFile.getAbsolutePath());
    try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(job), StandardCharsets.UTF_8)) {
      p.store(w, "luka replay editor render job");
    } catch (IOException e) {
      setStatus("Could not write the render job: " + e.getMessage());
      return;
    }
    // previews always render out of sight; a full render follows the box
    boolean hidden = Platform.windows() && (!"Rendering".equals(what) || hideGame.isSelected());
    if (!host.startRenderJob(job, hidden, this::renderEnded)) {
      setStatus("Close the game first: the editor starts its own game.");
      return;
    }
    jobWhat = what;
    rendering = true;
    render.setEnabled(false);
    previews.setEnabled(false);
    showFile.setEnabled(false);
    bar.setValue(0);
    setStatus("Starting the game (checks files first)...");
    poll = new Timer(500, e -> readStatus(statusFile));
    poll.start();
  }

  private Properties lastStatus;

  private void readStatus(File statusFile) {
    if (!statusFile.isFile()) return;
    Properties s = new Properties();
    try (InputStream in = new FileInputStream(statusFile)) {
      s.load(new InputStreamReader(in, StandardCharsets.UTF_8));
    } catch (IOException e) {
      return; // being replaced; next time
    }
    lastStatus = s;
    String state = s.getProperty("state", "");
    int clip = parseInt(s.getProperty("clip"), 1), count = Math.max(1, parseInt(s.getProperty("clips"), 1));
    double progress = parseDouble(s.getProperty("progress"), 0);
    if ("rendering".equals(state) || "starting".equals(state)) {
      double all = ((clip - 1) + progress) / count;
      bar.setValue((int) (all * 1000));
      setStatus(jobWhat + ": clip " + clip + " of " + count + ",  " + (int) (progress * 100) + " %");
    } else if ("joining".equals(state)) {
      bar.setValue(1000);
      setStatus("Joining the clips...");
    }
  }

  private void renderEnded(int code, String error) {
    if (poll != null) {
      poll.stop();
      readStatus(new File(jobDir, "status.properties"));
    }
    rendering = false;
    render.setEnabled(true);
    previews.setEnabled(true);
    String state = lastStatus == null ? "" : lastStatus.getProperty("state", "");
    List<Recording> previewsMade = pendingPreviews;
    pendingPreviews = null;
    if ("done".equals(state) && previewsMade != null) {
      bar.setValue(1000);
      for (Recording r : previewsMade) {
        try {
          java.nio.file.Files.write(previewStamp(r).toPath(), stampOf(r).getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
          // made again next time
        }
        r.frames = null;
        r.previewCheckedMs = 0;
        new File(previewFrames(r), "README.txt").delete();
      }
      File[] logs = new File(previewDir(), "logs").listFiles();
      if (logs != null) for (File f : logs) f.delete();
      new File(previewDir(), "logs").delete();
      setStatus("Previews ready: SPACE plays the edit, click the ruler to move the playhead.");
      timeline.repaint();
      preview.repaint();
    } else if ("done".equals(state)) {
      bar.setValue(1000);
      File out = new File(lastStatus.getProperty("output", output.getText()));
      setStatus("Done: " + out.getName() + " (" + String.format(Locale.ROOT, "%.1f MB", out.length() / 1048576.0) + ")");
      showFile.setEnabled(out.isFile());
      output.setText(nextFreeName(out).getAbsolutePath());
      lastOutput = out;
    } else if ("error".equals(state)) {
      setStatus("Failed: " + lastStatus.getProperty("message", "see the launcher log"));
    } else if (error != null) {
      setStatus("Could not start the game: " + error);
    } else {
      setStatus(lastStatus == null
          ? "The game closed before rendering. It may be an older luka: press PLAY once to update, then try again."
          : "The game closed before the render finished (exit code " + code + "). See the launcher log.");
    }
    lastStatus = null;
  }

  private File lastOutput;

  private void showOutput() {
    if (lastOutput == null || !lastOutput.isFile()) return;
    try {
      if (Platform.windows()) new ProcessBuilder("explorer.exe", "/select," + lastOutput.getAbsolutePath()).start();
      else open(lastOutput.getParentFile());
    } catch (IOException e) {
      open(lastOutput.getParentFile());
    }
  }

  /** So the next render does not overwrite this one: edit.mp4, then edit-2.mp4, ... */
  private static File nextFreeName(File f) {
    String base = f.getName().replaceAll("(-\\d+)?\\.mp4$", "");
    File dir = f.getParentFile();
    File n = f;
    for (int i = 2; n.exists(); i++) n = new File(dir, base + "-" + i + ".mp4");
    return n;
  }

  private void open(File dir) {
    try {
      dir.mkdirs();
      Desktop.getDesktop().open(dir);
    } catch (Exception e) {
      setStatus("Could not open " + dir);
    }
  }

  // ---- small helpers ----

  static int[] parseSize(String text) {
    String[] parts = text.trim().toLowerCase(Locale.ROOT).split("\\s*[x*]\\s*");
    if (parts.length != 2) return null;
    int w = parseInt(parts[0], -1) / 2 * 2, h = parseInt(parts[1], -1) / 2 * 2;
    return w >= 16 && h >= 16 && w <= 7680 && h <= 4320 ? new int[] {w, h} : null;
  }

  static String clock(double seconds) {
    if (seconds < 0) seconds = 0;
    int m = (int) (seconds / 60);
    double s = seconds - m * 60;
    return String.format(Locale.ROOT, "%d:%04.1f", m, s);
  }

  /** Ruler labels: 0:05, 1:30, 0:02.5 */
  private static String clockShort(double seconds) {
    int m = (int) (seconds / 60);
    double s = seconds - m * 60;
    return Math.abs(s - Math.round(s)) < 1e-6 ? String.format(Locale.ROOT, "%d:%02d", m, Math.round(s)) : String.format(Locale.ROOT, "%d:%04.1f", m, s);
  }

  private static String num(double d) {
    return String.format(Locale.ROOT, "%.3f", d);
  }

  private static double clamp(double v, double lo, double hi) {
    return Math.max(lo, Math.min(hi, v));
  }

  private static int parseInt(String s, int fallback) {
    try {
      return Integer.parseInt(s.trim());
    } catch (Exception e) {
      return fallback;
    }
  }

  private static double parseDouble(String s, double fallback) {
    try {
      return Double.parseDouble(s.trim());
    } catch (Exception e) {
      return fallback;
    }
  }

  private static String esc(String s) {
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  private static JPanel row() {
    JPanel p = new JPanel();
    p.setLayout(new FlowLayout(FlowLayout.LEFT, 4, 0));
    p.setOpaque(false);
    return p;
  }

  private JLabel label(String text, boolean boldFace, float size, Color color) {
    JLabel l = new JLabel(text);
    l.setFont(fonts.font(boldFace, size));
    l.setForeground(color);
    return l;
  }

  /** A window-wide key, except while typing in a text box or moving through the recordings list. */
  private void key(String stroke, String name, Runnable action) {
    getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(javax.swing.KeyStroke.getKeyStroke(stroke), name);
    getRootPane().getActionMap().put(name, new javax.swing.AbstractAction() {
      @Override public void actionPerformed(java.awt.event.ActionEvent e) {
        Component f = getFocusOwner();
        if (f instanceof javax.swing.text.JTextComponent) return;
        if (f == libraryList && (stroke.equals("LEFT") || stroke.equals("RIGHT") || stroke.equals("HOME") || stroke.equals("END"))) return;
        action.run();
      }
    });
  }

  private JButton button(String text, boolean primary) {
    JButton b = new JButton(text);
    b.setFont(fonts.font(true, 11f));
    b.setForeground(Color.WHITE);
    b.setBackground(primary ? PURPLE_DARK : LINE);
    b.setFocusPainted(false);
    b.setFocusable(false); // SPACE plays the preview, it must not press the last clicked button
    b.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));
    b.setOpaque(true);
    b.setContentAreaFilled(true);
    b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    b.addMouseListener(new MouseAdapter() {
      @Override public void mouseEntered(MouseEvent e) {
        if (b.isEnabled()) b.setBackground(primary ? PURPLE : PURPLE_DARK);
      }

      @Override public void mouseExited(MouseEvent e) {
        b.setBackground(primary ? PURPLE_DARK : LINE);
      }
    });
    return b;
  }

  /** A flat square button with a drawn icon (Ico), for the timeline toolbar. */
  private JButton iconButton(String kind, String tip, Runnable action) {
    JButton b = new JButton(new Ico(kind));
    b.setToolTipText(tip);
    b.setFocusable(false);
    b.setFocusPainted(false);
    b.setBorder(BorderFactory.createEmptyBorder(6, 7, 6, 7));
    b.setContentAreaFilled(false);
    b.setOpaque(true);
    b.setBackground(BG);
    b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    b.addActionListener(e -> action.run());
    b.addMouseListener(new MouseAdapter() {
      @Override public void mouseEntered(MouseEvent e) {
        b.setBackground(LINE);
      }

      @Override public void mouseExited(MouseEvent e) {
        b.setBackground(BG);
      }
    });
    return b;
  }

  /** The toolbar's line icons, drawn (no image files): play, pause, prev, next, plus, trash, markin, markout, split, dup, fit. */
  private static final class Ico implements Icon {
    private final String kind;

    Ico(String kind) {
      this.kind = kind;
    }

    @Override public int getIconWidth() {
      return 18;
    }

    @Override public int getIconHeight() {
      return 18;
    }

    @Override public void paintIcon(Component c, Graphics g0, int x, int y) {
      Graphics2D g = (Graphics2D) g0.create();
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.translate(x, y);
      g.setColor(c.isEnabled() ? TEXT : FAINT);
      g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      switch (kind) {
        case "play":
          g.fill(poly(5, 3, 15, 9, 5, 15));
          break;
        case "pause":
          g.fillRect(5, 4, 3, 11);
          g.fillRect(11, 4, 3, 11);
          break;
        case "prev":
          g.drawLine(4, 4, 4, 14);
          g.draw(poly(14, 4, 7, 9, 14, 14));
          break;
        case "next":
          g.drawLine(14, 4, 14, 14);
          g.draw(poly(4, 4, 11, 9, 4, 14));
          break;
        case "plus":
          g.drawLine(9, 3, 9, 15);
          g.drawLine(3, 9, 15, 9);
          break;
        case "trash":
          g.drawLine(3, 5, 15, 5);
          g.drawLine(7, 5, 7, 3);
          g.drawLine(7, 3, 11, 3);
          g.drawLine(11, 3, 11, 5);
          g.drawRoundRect(5, 5, 8, 10, 2, 2);
          break;
        case "markin":
          g.drawLine(4, 3, 4, 15);
          g.drawLine(4, 3, 7, 3);
          g.drawLine(4, 15, 7, 15);
          g.drawLine(8, 9, 15, 9);
          g.drawLine(12, 6, 15, 9);
          g.drawLine(12, 12, 15, 9);
          break;
        case "markout":
          g.drawLine(14, 3, 14, 15);
          g.drawLine(11, 3, 14, 3);
          g.drawLine(11, 15, 14, 15);
          g.drawLine(3, 9, 10, 9);
          g.drawLine(6, 6, 3, 9);
          g.drawLine(6, 12, 3, 9);
          break;
        case "split":
          g.drawLine(9, 2, 9, 16);
          g.draw(poly(7, 6, 3, 9, 7, 12));
          g.draw(poly(11, 6, 15, 9, 11, 12));
          break;
        case "dup":
          g.drawRoundRect(3, 6, 9, 9, 2, 2);
          g.drawRoundRect(6, 3, 9, 9, 2, 2);
          break;
        case "undo":
          g.draw(new java.awt.geom.Arc2D.Double(4, 4, 11, 10, 200, -230, java.awt.geom.Arc2D.OPEN));
          g.drawLine(4, 6, 4, 10);
          g.drawLine(4, 10, 8, 10);
          break;
        case "redo":
          g.draw(new java.awt.geom.Arc2D.Double(3, 4, 11, 10, -20, 230, java.awt.geom.Arc2D.OPEN));
          g.drawLine(14, 6, 14, 10);
          g.drawLine(14, 10, 10, 10);
          break;
        case "killprev":
        case "killnext":
          g.setColor(c.isEnabled() ? KILL : FAINT);
          g.fill(poly(9, 4, 14, 9, 9, 14, 4, 9));
          g.setColor(c.isEnabled() ? TEXT : FAINT);
          if (kind.equals("killprev")) g.drawLine(1, 4, 1, 14);
          else g.drawLine(17, 4, 17, 14);
          break;
        case "fit":
          g.drawLine(2, 9, 16, 9);
          g.draw(poly(5, 6, 2, 9, 5, 12));
          g.draw(poly(13, 6, 16, 9, 13, 12));
          g.drawLine(2, 3, 2, 15);
          g.drawLine(16, 3, 16, 15);
          break;
        default:
          break;
      }
      g.dispose();
    }

    private static Path2D poly(double... xy) {
      Path2D p = new Path2D.Double();
      p.moveTo(xy[0], xy[1]);
      for (int i = 2; i < xy.length; i += 2) p.lineTo(xy[i], xy[i + 1]);
      p.closePath();
      return p;
    }
  }

  private JCheckBox check(String text, boolean on) {
    JCheckBox c = new JCheckBox(text, on);
    c.setOpaque(false);
    c.setForeground(TEXT);
    c.setFont(fonts.font(false, 12f));
    c.setFocusPainted(false);
    c.setFocusable(false);
    return c;
  }

  private void style(JTextField f) {
    f.setFont(fonts.font(false, 12f));
    f.setBackground(BG);
    f.setForeground(TEXT);
    f.setCaretColor(TEXT);
    f.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(LINE), BorderFactory.createEmptyBorder(5, 8, 5, 8)));
  }
}

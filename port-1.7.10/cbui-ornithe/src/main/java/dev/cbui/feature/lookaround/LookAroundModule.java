package dev.cbui.feature.lookaround;

import dev.cbui.Cbui;
import dev.cbui.feature.FeatureModule;
import dev.cbui.feature.Features;
import dev.cbui.hud.Setting;
import dev.cbui.server.ServerRulesBridge;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

/**
 * Card "Freelook": while active, the mouse turns a separate camera and the player keeps facing (and walking) where it
 * did. Everything that runs every tick, also while the card is off (key registration, connection watch, the server
 * announcement), lives in {@link #sync()}.
 */
public final class LookAroundModule extends FeatureModule implements Features.Synced {
  private static final String[] VIEWS = {"Own eyes", "Behind you", "Facing you"};
  private static final String HELD = "While held", TAP_OR_HOLD = "Tap or hold", TOGGLE = "Press on / press off";
  private static final String F5_KEEP_LOOKING = "Switch view, keep looking", F5_END_KEEP = "End and keep new view",
      F5_IGNORE = "Ignore the view key", F5_END_RESTORE = "End and restore old view";
  private static final String QUIT_CHANNEL = "freelook:disable";
  private static final String HELLO_CHANNEL = "freelook:handshake";
  /** Seconds the camera takes to glide out to its normal distance at the start. */
  private static final double GLIDE_SECONDS = 0.66;

  private final Setting.Choice view = grouped("Starting", add(Setting.tip(
      new Setting.Choice("Camera view while looking", VIEWS[1], VIEWS), "View used while freelook is on")));
  private final Setting.Choice keyStyle = grouped("Starting", add(Setting.tip(
      new Setting.Choice("Key behaviour", HELD, HELD, TAP_OR_HOLD, TOGGLE), "How the key starts and ends it")));
  private final Setting.Slider longPress = grouped("Starting", add(new Setting.Slider("Long-press time (ms)", 300, 50, 1000, 50,
      "Tap or hold: a press longer than this ends on release")));
  private final Setting.Choice viewKey = grouped("Starting", add(Setting.tip(
      new Setting.Choice("When F5 is pressed", F5_END_KEEP, F5_KEEP_LOOKING, F5_END_KEEP, F5_IGNORE, F5_END_RESTORE),
      "What the view key does while looking around")));

  private final Setting.Toggle glide = grouped("Camera feel", add(new Setting.Toggle("Glide out at start", true,
      "The camera eases out instead of jumping back")));
  private final Setting.Slider speed = grouped("Camera feel", add(new Setting.Slider("Mouse speed (%)", 100, 10, 200, 5,
      "100 matches the normal mouse speed")));
  private final Setting.Slider smoothing = grouped("Camera feel", add(new Setting.Slider("Cinematic smoothing (%)", 0, 0, 100, 5,
      "0 is off; 100 is vanilla's cinematic camera")));

  private final Setting.Toggle sideways = grouped("Turning", add(new Setting.Toggle("Turn sideways", true,
      "Mouse can turn the camera left and right")));
  private final Setting.Toggle flipSideways = grouped("Turning", add(new Setting.Toggle("Reverse sideways", false, "Swap left and right")));
  private final Setting.Toggle upDown = grouped("Turning", add(new Setting.Toggle("Turn up and down", true, "Mouse can tilt the camera")));
  private final Setting.Toggle flipUpDown = grouped("Turning", add(new Setting.Toggle("Reverse up and down", false, "Swap up and down")));
  private final Setting.Toggle stopAtVertical = grouped("Turning", add(new Setting.Toggle("Stop at straight up/down", true,
      "The camera cannot tilt past vertical")));

  /** Deliberately without the card's name in it: the key row below already shows it on the card. */
  private final KeyBinding key = new KeyBinding("Look around without turning", 0, "luka");
  private final Setting.Key keyRow = grouped("Key", add(new Setting.Key("Look-around key", 0,
      "Click, then press a key; Escape clears it")));
  private boolean keyListed;

  private boolean looking;
  private int viewBefore, viewNow;
  private float yaw, pitch;
  private long glideFrom;
  private long pressedAt;
  private boolean mayEndOnRelease;
  private boolean turned;
  private int lastSeenView = -1;
  private Object connection;
  private boolean serverSaidNo;

  public LookAroundModule() {
    super("Freelook", true);
    keyRow.bind(new Setting.Key.Binding() {
      @Override public int get() {
        return key.getKeyCode();
      }

      @Override public void set(int code) {
        key.setKeyCode(code);
        KeyBinding.resetKeyBindingArrayAndHash();
        if (keyListed && mc.gameSettings != null) mc.gameSettings.saveOptions();
      }
    });
  }

  @Override
  public String category() {
    return "UTILITY";
  }

  /** The module whether or not it is switched on, or null when not registered. */
  public static LookAroundModule instance() {
    return Features.get(LookAroundModule.class);
  }

  @Override
  public void enabled(boolean value) {
    super.enabled(value);
    if (!value && looking) finish(true);
  }

  // ---- tick ----

  @Override
  public void sync() {
    try {
      // ticks run outside rendering: angles still lent here mean a camera method threw before giving them back
      if (lentTo != null || lendDepth != 0) giveBack();
      listKey();
      watchConnection();
      if (!usable()) {
        if (looking) finish(true);
        while (key.isPressed()) { /* presses made while unusable are dropped */ }
        return;
      }
      readKey();
      if (looking) {
        GameSettings settings = mc.gameSettings;
        if (settings.thirdPersonView != viewNow) viewChanged(settings.thirdPersonView, true);
      }
      int shown = mc.gameSettings.thirdPersonView;
      if (turned || (looking && shown != lastSeenView)) refreshEntities();
      lastSeenView = shown;
      turned = false;
    } catch (Throwable error) {
      Cbui.reportOnce("freelook tick", error);
    }
  }

  private void listKey() throws Exception {
    if (keyListed || mc.gameSettings == null) return;
    keyListed = true;
    File options = new File(mc.mcDataDir, "options.txt");
    String prefix = "key_" + key.getKeyDescription() + ":";
    if (options.isFile()) {
      for (String line : Files.readAllLines(options.toPath(), StandardCharsets.UTF_8)) {
        if (!line.startsWith(prefix)) continue;
        try {
          key.setKeyCode(Integer.parseInt(line.substring(prefix.length()).trim()));
        } catch (NumberFormatException ignored) {
          // keep the key unbound
        }
      }
    }
    KeyBinding[] old = mc.gameSettings.keyBindings;
    KeyBinding[] all = Arrays.copyOf(old, old.length + 1);
    all[old.length] = key;
    mc.gameSettings.keyBindings = all;
    KeyBinding.resetKeyBindingArrayAndHash();
  }

  private boolean usable() {
    return enabled() && mc.thePlayer != null && mc.theWorld != null && mc.gameSettings != null && !serverForbids();
  }

  private boolean serverForbids() {
    if (serverSaidNo) return true;
    String host = host();
    if (host.equals("hypixel.net") || host.endsWith(".hypixel.net")) return true;
    return ServerRulesBridge.modDisabled("freelook");
  }

  private void readKey() {
    boolean down = key.getIsKeyPressed();
    int presses = 0;
    while (key.isPressed()) presses++;
    String style = keyStyle.get();
    if (HELD.equals(style)) {
      if (down && !looking) begin();
      else if (!down && looking) finish(true);
    } else if (TOGGLE.equals(style)) {
      for (int i = 0; i < presses; i++) {
        if (looking) finish(true);
        else begin();
      }
    } else {
      if (presses > 0) {
        if (looking) {
          finish(true);
          mayEndOnRelease = false;
        } else {
          begin();
          pressedAt = System.currentTimeMillis();
          mayEndOnRelease = looking;
        }
      }
      if (looking && mayEndOnRelease && !down) {
        mayEndOnRelease = false;
        if (System.currentTimeMillis() - pressedAt > longPress.get()) finish(true);
      }
    }
  }

  private void begin() {
    if (mc.thePlayer == null || mc.renderViewEntity != mc.thePlayer) return;
    GameSettings settings = mc.gameSettings;
    viewBefore = settings.thirdPersonView;
    viewNow = chosenView();
    settings.thirdPersonView = viewNow;
    yaw = mc.thePlayer.rotationYaw;
    pitch = mc.thePlayer.rotationPitch;
    glideFrom = System.nanoTime();
    looking = true;
    refreshEntities();
  }

  private void finish(boolean restoreView) {
    looking = false;
    mayEndOnRelease = false;
    if (restoreView && mc.gameSettings != null) mc.gameSettings.thirdPersonView = viewBefore;
    refreshEntities();
  }

  private int chosenView() {
    int index = Arrays.asList(VIEWS).indexOf(view.get());
    return index < 0 ? 1 : index;
  }

  /** Applies the card's F5 choice to a view change; returns false when the change must not happen. */
  private boolean viewChanged(int wanted, boolean alreadyWritten) {
    String choice = viewKey.get();
    if (F5_KEEP_LOOKING.equals(choice)) {
      if (viewNow == 0 && wanted != 0) glideFrom = System.nanoTime();
      viewNow = wanted;
      return true;
    }
    if (F5_END_KEEP.equals(choice)) {
      finish(false);
      return true;
    }
    if (F5_IGNORE.equals(choice)) {
      if (alreadyWritten) mc.gameSettings.thirdPersonView = viewNow;
      return false;
    }
    finish(true);
    return false;
  }

  private void refreshEntities() {
    if (mc.renderGlobal != null && mc.theWorld != null) mc.renderGlobal.rebuildDisplayListEntities();
  }

  // ---- connection and server rules ----

  private void watchConnection() {
    NetHandlerPlayClient now = mc.getNetHandler();
    if (now == connection) return;
    connection = now;
    serverSaidNo = false;
    if (looking) finish(true);
    if (now != null) announce(now);
  }

  /** Tells the server this client understands the community freelook switch (Shotbow is left alone). */
  private void announce(NetHandlerPlayClient net) {
    String host = host();
    if (host.equals("shotbow.com") || host.endsWith(".shotbow.com") || host.equals("shotbow.net") || host.endsWith(".shotbow.net")) return;
    net.addToSendQueue(new C17PacketCustomPayload("REGISTER", QUIT_CHANNEL.getBytes(StandardCharsets.UTF_8)));
    net.addToSendQueue(new C17PacketCustomPayload(HELLO_CHANNEL, new byte[0]));
  }

  /** Lower-case host of the current server without port, or "" in single player. */
  private String host() {
    ServerData server = mc.getCurrentServerData();
    if (server == null || server.serverIP == null) return "";
    String host = server.serverIP.trim().toLowerCase(Locale.ROOT);
    int colon = host.lastIndexOf(':');
    if (colon > 0 && host.indexOf(':') == colon) host = host.substring(0, colon);
    while (host.endsWith(".")) host = host.substring(0, host.length() - 1);
    return host;
  }

  /** A plugin message arrived on the main thread. */
  public void serverMessage(String channel) {
    if (!QUIT_CHANNEL.equals(channel)) return;
    boolean first = !serverSaidNo;
    serverSaidNo = true;
    if (looking) finish(true);
    if (first && mc.thePlayer != null) {
      mc.thePlayer.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "This server has switched Freelook off until you reconnect."));
    }
  }

  // ---- render-time questions (pure reads) ----

  /** True while the camera, not the player, follows the mouse. */
  public boolean steering() {
    return looking && mc.thePlayer != null && mc.renderViewEntity == mc.thePlayer;
  }

  /** Mouse movement for {@code entity}; true when the camera took it and the entity must not turn. */
  public boolean turn(Entity entity, float dx, float dy) {
    if (!steering() || entity != mc.thePlayer) return false;
    float scale = 0.15f * speed.get() / 100f;
    if (sideways.get()) yaw += dx * scale * (flipSideways.get() ? -1f : 1f);
    if (upDown.get()) pitch -= dy * scale * (flipUpDown.get() ? -1f : 1f);
    if (stopAtVertical.get()) pitch = Math.max(-90f, Math.min(90f, pitch));
    else if (pitch > 180f) pitch -= 360f;
    else if (pitch < -180f) pitch += 360f;
    turned = true;
    return true;
  }

  public float cameraYaw(float vanilla) {
    return steering() ? yaw : vanilla;
  }

  public float cameraPitch(float vanilla) {
    return steering() ? pitch : vanilla;
  }

  // ---- lending the camera angles to the player during camera maths ----
  // Hooking single field reads is not enough: the mapping step limits a field target to its first read in a method,
  // so the camera setup and billboards would keep most of the player's real angles. Instead the player carries the
  // camera angles for the length of those methods and gets its own back right after.

  private int lendDepth;
  private Entity lentTo;
  private float ownYaw, ownPrevYaw, ownPitch, ownPrevPitch;

  /** Called at the start of a camera-maths method; pair every call with {@link #returnAngles()}. */
  public void lendAngles() {
    if (lendDepth++ > 0) return;
    if (!steering()) return;
    Entity player = mc.thePlayer;
    lentTo = player;
    ownYaw = player.rotationYaw;
    ownPrevYaw = player.prevRotationYaw;
    ownPitch = player.rotationPitch;
    ownPrevPitch = player.prevRotationPitch;
    player.rotationYaw = player.prevRotationYaw = yaw;
    player.rotationPitch = player.prevRotationPitch = pitch;
  }

  /** Called at every return of a camera-maths method. */
  public void returnAngles() {
    if (lendDepth > 0 && --lendDepth > 0) return;
    giveBack();
  }

  private void giveBack() {
    lendDepth = 0;
    Entity player = lentTo;
    if (player == null) return;
    lentTo = null;
    player.rotationYaw = ownYaw;
    player.prevRotationYaw = ownPrevYaw;
    player.rotationPitch = ownPitch;
    player.prevRotationPitch = ownPrevPitch;
  }

  /** Multiplier for the third-person orbit distance (the glide at the start). */
  public float orbitScale() {
    if (!steering() || !glide.get() || viewNow == 0) return 1f;
    double t = (System.nanoTime() - glideFrom) / (GLIDE_SECONDS * 1.0E9);
    if (t >= 1) return 1f;
    if (t <= 0) return 0f;
    double rest = 1 - t;
    return (float) (1 - rest * rest * rest);
  }

  public boolean forcesSmoothCamera() {
    return steering() && smoothing.get() > 0f;
  }

  /** Mouse-filter step: 100 % keeps vanilla's, lower values follow the mouse faster. */
  public float smoothingStep(float vanilla) {
    if (!forcesSmoothCamera() || !(vanilla > 0f) || vanilla >= 1f) return vanilla;
    return (float) Math.pow(vanilla, smoothing.get() / 100f);
  }

  /** The vanilla view key wants to write {@code value}; returns what to write, or -1 to leave the view alone. */
  public int viewKeyWrite(int value) {
    if (!looking) return value;
    int wanted = value > 2 ? 0 : value;
    if (viewChanged(wanted, false)) return value;
    return -1;
  }
}

package dev.cbui.feature.freelook;

import dev.cbui.feature.FeatureModule;
import dev.cbui.feature.Features;
import dev.cbui.hud.Setting;
import dev.cbui.server.ServerRulesBridge;
import io.netty.buffer.Unpooled;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import net.minecraft.util.ChatComponentText;
import org.lwjgl.input.Keyboard;

/**
 * Lets the camera turn independently of the player: while active, mouse movement steers a detached camera and the player keeps
 * facing where they were. Supports hold / quick-press / toggle activation and several ways of reacting to the vanilla F5 key.
 */
public final class FreelookModule extends FeatureModule {
  /** Name this feature goes by in server-side restriction lists. */
  private static final String RULE_NAME = "freelook";
  /** Plugin channels of the community protocol: servers send the first to switch Freelook off; we announce ourselves on the second. */
  private static final String DISABLE_CHANNEL = "freelook:disable";
  private static final String HANDSHAKE_CHANNEL = "freelook:handshake";
  private static final String KEY_LABEL = "Activate Freelook";
  /** Length of the ease-out when the camera swings out at the start of a freelook. */
  private static final long EASE_MILLIS = 650L;

  public final Setting.Choice perspectiveType = grouped("Activation", add(Setting.tip(new Setting.Choice(
      "Perspective Type", "Third Person (Back)", "First Person", "Third Person (Back)", "Third Person (Front)"),
      "Which camera view freelook switches to while it is on.")));
  public final Setting.Choice pressMode = grouped("Activation", add(Setting.tip(new Setting.Choice(
      "Press Mode", "Hold", "Hold", "Quick Press", "Toggle"),
      "Hold: active while the key is down. Toggle: each press switches it. Quick Press: a tap switches it, a long hold ends it on release.")));
  public final Setting.Slider holdThreshold = grouped("Activation", add(new Setting.Slider(
      "Hold Threshold", 300f, 50f, 1000f, 50f, "How many milliseconds count as a long hold in Quick Press mode.")));
  public final Setting.Choice onCycleChange = grouped("Activation", add(Setting.tip(new Setting.Choice(
      "On Cycle Change", "Stop Freelook", "Cycle and Freelook", "Stop Freelook", "Block Camera Cycle", "Exit Freelook"),
      "What pressing the normal camera-view key does while freelook is on.")));

  public final Setting.Toggle smoothCamera = grouped("Camera", add(new Setting.Toggle(
      "Smooth Camera", true, "The third-person camera glides out instead of snapping when freelook starts.")));
  public final Setting.Slider sensitivity = grouped("Camera", add(new Setting.Slider(
      "Sensitivity (%)", 100f, 10f, 200f, 5f, "How fast the mouse turns the camera while freelooking.")));
  public final Setting.Slider cinematicCamera = grouped("Camera", add(new Setting.Slider(
      "Cinematic Camera (%)", 0f, 0f, 100f, 5f, "Smooths mouse movement while freelooking, like the vanilla cinematic camera.")));

  public final Setting.Toggle yawEnabled = grouped("Yaw", add(new Setting.Toggle(
      "Yaw Enabled", true, "The camera can turn left and right.")));
  public final Setting.Toggle yawInvert = grouped("Yaw", add(new Setting.Toggle(
      "Yaw Invert", false, "Flip the left/right direction.")));
  public final Setting.Toggle pitchEnabled = grouped("Pitch", add(new Setting.Toggle(
      "Pitch Enabled", true, "The camera can look up and down.")));
  public final Setting.Toggle pitchInvert = grouped("Pitch", add(new Setting.Toggle(
      "Pitch Invert", false, "Flip the up/down direction.")));
  public final Setting.Toggle pitchLock = grouped("Pitch", add(new Setting.Toggle(
      "Pitch Lock", true, "Stops the camera at straight up and straight down (-90 to 90 degrees).")));

  public final Setting.Key keySetting = grouped("Key", add(new Setting.Key(
      "Freelook key", Keyboard.KEY_NONE, "Hold or press this key to look around. Click, then press a key. Escape clears it.")));

  private KeyBinding activationKey;
  private boolean running;
  private boolean keyHeldLastTick;
  private long quickPressStartedAt;
  private int perspectiveBefore;
  private int perspectiveWanted;
  private float viewYaw;
  private float viewPitch;
  private long easeStartedAt;
  private final ServerLink link = new ServerLink();

  public FreelookModule() { super("Freelook", true); }

  @Override public String category() { return "UTILITY"; }

  /** The module instance, or null when unavailable. */
  public static FreelookModule instance() {
    try {
      return Features.get(FreelookModule.class);
    } catch (Throwable failure) {
      return null;
    }
  }

  /** The yaw camera code should use: the detached one while freelooking, otherwise {@code vanilla}. */
  public static float viewYawOr(float vanilla) {
    try {
      FreelookModule module = instance();
      return module == null ? vanilla : module.cameraYaw(vanilla);
    } catch (Throwable failure) {
      return vanilla;
    }
  }

  /** The pitch camera code should use: the detached one while freelooking, otherwise {@code vanilla}. */
  public static float viewPitchOr(float vanilla) {
    try {
      FreelookModule module = instance();
      return module == null ? vanilla : module.cameraPitch(vanilla);
    } catch (Throwable failure) {
      return vanilla;
    }
  }

  // ---------------------------------------------------------------------------------------------------- ticking

  @Override public void tick() {
    backgroundTick();
    if (link.blocked()) {
      stop();
      return;
    }
    boolean down = activationKey != null && activationKey.getIsKeyPressed();
    boolean fresh = down && !keyHeldLastTick;
    keyHeldLastTick = down;
    applyPressMode(down, fresh);
    if (running && mc.gameSettings.thirdPersonView != perspectiveWanted) reactToPerspectiveChange();
  }

  /** Runs from the Minecraft hook as well, since a disabled module gets no normal HUD ticks but its key must stay registered. */
  public void backgroundTick() {
    registerKey();
    link.refresh();
  }

  private void applyPressMode(boolean down, boolean fresh) {
    String mode = pressMode.get();
    if ("Toggle".equals(mode)) {
      if (fresh) flip();
    } else if ("Quick Press".equals(mode)) {
      handleQuickPress(down, fresh);
    } else if (down) {
      start();
    } else {
      stop();
    }
  }

  private void handleQuickPress(boolean down, boolean fresh) {
    long now = System.currentTimeMillis();
    if (fresh) {
      quickPressStartedAt = now;
      flip();
    }
    if (!down && quickPressStartedAt != 0L) {
      long heldFor = now - quickPressStartedAt;
      quickPressStartedAt = 0L;
      if (heldFor > (long) holdThreshold.get().floatValue()) stop();
    }
  }

  @Override public void enabled(boolean value) {
    if (!value) stop();
    super.enabled(value);
  }

  // ---------------------------------------------------------------------------------------------------- state

  /** True while the detached camera is really in effect (running, allowed by the server, and not switched off). */
  public boolean active() { return running && enabled() && !link.blocked(); }

  private void flip() {
    if (running) stop();
    else start();
  }

  private void start() {
    if (running || mc.thePlayer == null || mc.gameSettings == null || ServerRulesBridge.modDisabled(RULE_NAME)) return;
    perspectiveBefore = mc.gameSettings.thirdPersonView;
    perspectiveWanted = chosenPerspective();
    mc.gameSettings.thirdPersonView = perspectiveWanted;
    viewYaw = mc.thePlayer.rotationYaw;
    viewPitch = mc.thePlayer.rotationPitch;
    easeStartedAt = System.currentTimeMillis();
    running = true;
    refreshEntityLists();
  }

  public void stop() {
    if (!running) return;
    running = false;
    if (mc.gameSettings != null) mc.gameSettings.thirdPersonView = perspectiveBefore;
    refreshEntityLists();
  }

  private int chosenPerspective() {
    String choice = perspectiveType.get();
    if ("First Person".equals(choice)) return 0;
    return "Third Person (Front)".equals(choice) ? 2 : 1;
  }

  private void refreshEntityLists() {
    try {
      if (mc.renderGlobal != null) mc.renderGlobal.rebuildDisplayListEntities();
    } catch (Throwable failure) {
      // cosmetic only
    }
  }

  /** The vanilla view key changed the camera behind our back (as seen from tick); react according to the chosen policy. */
  private void reactToPerspectiveChange() {
    String policy = onCycleChange.get();
    if ("Cycle and Freelook".equals(policy)) {
      perspectiveWanted = mc.gameSettings.thirdPersonView;
    } else if ("Block Camera Cycle".equals(policy)) {
      mc.gameSettings.thirdPersonView = perspectiveWanted;
    } else if ("Stop Freelook".equals(policy)) {
      int current = mc.gameSettings.thirdPersonView;
      stop();
      mc.gameSettings.thirdPersonView = current;
    } else {
      stop();
    }
  }

  /** Decides whether a write to the camera-view setting (the vanilla key) goes through. */
  public boolean allowPerspectiveWrite(int requested) {
    if (!active()) return true;
    String policy = onCycleChange.get();
    if ("Block Camera Cycle".equals(policy)) return false;
    if ("Exit Freelook".equals(policy)) {
      stop();
      return false;
    }
    if ("Stop Freelook".equals(policy)) {
      stop();
      return true;
    }
    perspectiveWanted = requested;
    return true;
  }

  // ---------------------------------------------------------------------------------------------------- camera

  /** Only the local player's own camera is replaced; spectating someone else leaves everything alone. */
  private boolean watchingSelf() { return mc.renderViewEntity == mc.thePlayer; }

  public float cameraYaw(float vanilla) { return active() && watchingSelf() ? viewYaw : vanilla; }

  public float cameraPitch(float vanilla) { return active() && watchingSelf() ? viewPitch : vanilla; }

  /**
   * While the camera is set up (orientCamera) the camera entity must carry the detached angles. Patching each individual field read
   * did not reach the third-person orbit or the final rotation with this mod stack, so the angles are swapped in for the whole call
   * and restored right afterwards. Returns what to restore, or null when nothing was swapped.
   */
  public float[] swapInCameraAngles(Entity entity) {
    if (entity == null || !active() || !watchingSelf()) return null;
    float[] original = {entity.rotationYaw, entity.prevRotationYaw, entity.rotationPitch, entity.prevRotationPitch};
    entity.rotationYaw = entity.prevRotationYaw = viewYaw;
    entity.rotationPitch = entity.prevRotationPitch = viewPitch;
    return original;
  }

  public void swapOutCameraAngles(Entity entity, float[] original) {
    if (entity == null || original == null) return;
    entity.rotationYaw = original[0];
    entity.prevRotationYaw = original[1];
    entity.rotationPitch = original[2];
    entity.prevRotationPitch = original[3];
  }

  /** Takes a mouse movement for the detached camera. Returns true when the player's own rotation must be skipped. */
  public boolean rotateCamera(float yawDelta, float pitchDelta) {
    if (!active()) return false;
    float step = 0.15f * sensitivity.get() / 100f;
    if (yawEnabled.get()) viewYaw += yawDelta * step * (yawInvert.get() ? -1f : 1f);
    if (pitchEnabled.get()) {
      viewPitch += -pitchDelta * step * (pitchInvert.get() ? -1f : 1f);
      if (pitchLock.get()) viewPitch = Math.max(-90f, Math.min(90f, viewPitch));
    }
    refreshEntityLists();
    return true;
  }

  /** The third-person orbit distance, shortened right after freelook starts so the camera glides out. */
  public float smoothCameraDistance(float distance) {
    if (!active() || !smoothCamera.get()) return distance;
    long elapsed = System.currentTimeMillis() - easeStartedAt;
    if (elapsed >= EASE_MILLIS) return distance;
    float progress = Math.max(0f, Math.min(1f, elapsed / (float) EASE_MILLIS));
    float eased = (float) (1d - Math.pow(2d, -10d * progress));
    return distance * (0.125f + eased * 0.875f);
  }

  public boolean cinematic() { return active() && cinematicCamera.get() > 0f; }

  public float cinematicFactor(float vanilla) {
    if (!cinematic()) return vanilla;
    return (float) Math.max(1d, Math.min(vanilla, vanilla * 100d / cinematicCamera.get()));
  }

  // ---------------------------------------------------------------------------------------------------- server link

  /** Called when a server sends the disable payload. */
  public void serverDisabled() {
    link.allowed = false;
    stop();
    try {
      if (mc.thePlayer != null) mc.thePlayer.addChatMessage(new ChatComponentText("§cThis server has disabled Freelook."));
    } catch (Throwable failure) {
      // the chat line is a courtesy only
    }
  }

  /** Tracks which server we are connected to: who may use Freelook there, and the announcement we send on joining. */
  private final class ServerLink {
    boolean allowed = true;
    boolean noFreelookHost;
    private Object lastHandler;

    boolean blocked() { return !allowed || noFreelookHost || ServerRulesBridge.modDisabled(RULE_NAME); }

    void refresh() {
      Object handler = mc.getNetHandler();
      if (handler == lastHandler) return;
      lastHandler = handler;
      allowed = true;
      noFreelookHost = hostIn("hypixel.net");
      stop();
      if (handler == null || hostIn("shotbow.com") || hostIn("shotbow.net")) return;
      announce();
    }

    private void announce() {
      try {
        send("REGISTER", DISABLE_CHANNEL.getBytes(StandardCharsets.UTF_8));
        send(HANDSHAKE_CHANNEL, new byte[0]);
      } catch (Throwable failure) {
        // servers that ignore the protocol do not need it
      }
    }

    private void send(String channel, byte[] payload) {
      PacketBuffer buffer = new PacketBuffer(Unpooled.buffer(payload.length));
      buffer.writeBytes(payload);
      mc.getNetHandler().addToSendQueue(new C17PacketCustomPayload(channel, buffer));
    }

    /** True if the current server's address is {@code domain} or one of its subdomains. */
    private boolean hostIn(String domain) {
      ServerData server = mc.getCurrentServerData();
      if (server == null || server.serverIP == null) return false;
      String host = server.serverIP.toLowerCase(Locale.ROOT);
      int portAt = host.lastIndexOf(':');
      if (portAt >= 0) host = host.substring(0, portAt);
      while (host.endsWith(".")) host = host.substring(0, host.length() - 1);
      return host.equals(domain) || host.endsWith("." + domain);
    }
  }

  // ---------------------------------------------------------------------------------------------------- keybinding

  /** Registers the "Activate Freelook" key once, restoring any key already saved for it in options.txt. */
  public void registerKey() {
    if (activationKey != null) return;
    try {
      if (mc.gameSettings == null) return;
      KeyBinding key = new KeyBinding(KEY_LABEL, Keyboard.KEY_NONE, "luka");
      Integer saved = savedKeyCode(new File(mc.mcDataDir, "options.txt"), "key_" + KEY_LABEL + ":");
      if (saved != null) key.setKeyCode(saved);
      KeyBinding[] before = mc.gameSettings.keyBindings;
      KeyBinding[] after = Arrays.copyOf(before, before.length + 1);
      after[before.length] = key;
      mc.gameSettings.keyBindings = after;
      KeyBinding.resetKeyBindingArrayAndHash();
      activationKey = key;
      keySetting.bind(new Setting.Key.Binding() {
        @Override public int get() { return key.getKeyCode(); }
        @Override public void set(int code) {
          key.setKeyCode(code);
          KeyBinding.resetKeyBindingArrayAndHash();
          mc.gameSettings.saveOptions();
        }
      });
    } catch (Throwable failure) {
      activationKey = null;
    }
  }

  private static Integer savedKeyCode(File options, String prefix) throws java.io.IOException {
    if (!options.isFile()) return null;
    Integer found = null;
    List<String> lines = Files.readAllLines(options.toPath(), StandardCharsets.UTF_8);
    for (String line : lines) {
      if (!line.startsWith(prefix)) continue;
      try {
        found = Integer.valueOf(line.substring(prefix.length()).trim());
      } catch (NumberFormatException ignored) {
        // keep the previous valid value
      }
    }
    return found;
  }
}

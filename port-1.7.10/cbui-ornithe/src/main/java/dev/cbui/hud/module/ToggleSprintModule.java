package dev.cbui.hud.module;

import dev.cbui.hud.Anchor;
import dev.cbui.hud.Setting;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Toggle sprint and toggle sneak in one module, with one label on screen (2026-11-13: this replaced the separate "Always
 * sprint" and "Toggle sneak" modules; their behaviour is unchanged).
 * <ul>
 *   <li>Sprint "Toggle": tap the sprint key once to keep sprinting, tap again to stop. "Always": sprints whenever you move
 *       forward, nothing to press, and the sprint key cannot switch it off by accident. "Off": vanilla sprinting. Both modes
 *       hold the sprint key for you and let the game decide when to sprint, as with the key held by hand (see SprintKey).</li>
 *   <li>Toggle sneak: tap the sneak key once to sneak until you tap it again (holding it does nothing). "Sneak in containers"
 *       keeps you sneaking while a chest, furnace, crafting table or inventory is open, and goes back to the toggled state
 *       when it closes. The sneak itself is applied in {@code MovementInputMixin}.</li>
 *   <li>The status text: one editable line per state. The player writes the brackets in the line itself (the Brackets
 *       option of other text modules does not apply here); an empty line shows nothing for that state.</li>
 * </ul>
 */
public final class ToggleSprintModule extends LabelModule {
  public static final String TOGGLE = "Toggle", ALWAYS = "Always", OFF = "Off";
  /** Placeholder for the fly boost in the flying lines. luka has no fly boost, so it is always replaced by nothing. */
  private static final String BOOST = "{boost}";
  private static volatile ToggleSprintModule instance;

  private final Setting.Choice sprintMode = grouped("Sprint", add(Setting.tip(
      new Setting.Choice("Sprint", TOGGLE, TOGGLE, ALWAYS, OFF),
      "Toggle: tap the sprint key to keep sprinting. Always: sprints whenever you move forward, nothing to press. Off: vanilla sprinting")));
  private final Setting.Toggle toggleSneak = grouped("Sneak", add(new Setting.Toggle(
      "Toggle sneak", true, "Tap the sneak key to keep sneaking, tap again to stop")));
  private final Setting.Toggle inContainers = grouped("Sneak", add(new Setting.Toggle(
      "Sneak in containers", true, "Sneak automatically while a chest, furnace, crafting table or inventory is open")));
  private final Setting.Toggle showLabel = grouped("Display", add(new Setting.Toggle(
      "Show label", true, "Show the status text, like [Sprinting (Toggled)] (off: still works, nothing is drawn)")));
  private final Setting.Text sprintToggledText = line("Sprinting (toggled)", "[Sprinting (Toggled)]", "Shown while toggle sprint is on");
  private final Setting.Text sprintAlwaysText = line("Sprinting (always)", "[Sprinting (Always)]", "Shown while sprinting with Sprint set to Always");
  private final Setting.Text sprintHeldText = line("Sprinting (held)", "[Sprinting (Key Held)]", "Shown while you hold the sprint key");
  private final Setting.Text sprintVanillaText = line("Sprinting (vanilla)", "[Sprinting (Vanilla)]", "Shown while sprinting by double-tapping forward");
  private final Setting.Text sneakToggledText = line("Sneaking (toggled)", "[Sneaking (Toggled)]", "Shown while toggle sneak is on");
  private final Setting.Text sneakHeldText = line("Sneaking (held)", "[Sneaking (Key Held)]", "Shown while you hold the sneak key");
  private final Setting.Text sneakContainerText = line("Sneaking (container)", "[Sneaking (Container)]", "Shown while sneaking in an open container");
  private final Setting.Text flyingText = line("Flying", "[Flying]", "Shown while flying. {boost} is the fly boost, which stays empty for now");
  private final Setting.Text descendingText = line("Descending", "[Descending]", "Shown while flying down with the sneak key. {boost} works here too");
  private final Setting.Text ridingText = line("Riding", "[Riding]", "Shown while riding a horse, boat or minecart");
  private final Setting.Text dismountingText = line("Dismounting", "[Dismounting]", "Shown while pressing the sneak key to get off");

  private boolean sprintToggled, lastSprintKey;
  private boolean sneakToggled, lastSneakKey, sneaking;
  /** Nothing to show this tick (not sprinting or sneaking): the label is not drawn, outside the HUD editor. */
  private boolean idle = true;
  /** The last line text that had {boost} in it, and what it became (so flying with {boost} does not build a string per tick). */
  private String boostRaw, boostResolved;

  public ToggleSprintModule() {
    super("Toggle sprint", false, Anchor.BOTTOM_CENTER, 0, -50, "[Sprinting (Toggled)]", false);
    background.set(false);
    instance = this;
  }

  private Setting.Text line(String name, String text, String tip) {
    return grouped("Status text", adv(new Setting.Text(name, text, tip + ". Empty: nothing is shown")));
  }

  /** True when this module decides the sneak state this tick (the module and its toggle sneak are on). */
  public static boolean overriding() {
    ToggleSprintModule m = instance;
    return m != null && m.enabled() && m.toggleSneak.get();
  }

  /** The sneak state to use while {@link #overriding()}. */
  public static boolean sneaking() {
    ToggleSprintModule m = instance;
    return m != null && m.sneaking;
  }

  @Override
  public void tick() {
    EntityPlayer player = mc.thePlayer;
    String mode = sprintMode.get();

    // sprint: the physical key toggles; while toggled (or always) the sprint key is held for the player and the game sprints as in vanilla
    boolean key = SprintKey.physicallyDown(mc);
    if (TOGGLE.equals(mode)) {
      if (key && !lastSprintKey) sprintToggled = !sprintToggled;
      if (player != null && sprintToggled && mc.currentScreen == null) SprintKey.hold(mc, true);
    } else {
      sprintToggled = false;
      if (ALWAYS.equals(mode) && player != null && mc.currentScreen == null) SprintKey.hold(mc, true);
    }
    lastSprintKey = key;

    // sneak: only a tap in the world toggles, since shift in an inventory means shift-click
    boolean container = false;
    KeyBinding bind = mc.gameSettings.keyBindSneak;
    int code = bind.getKeyCode();
    boolean down = code < 0 ? Mouse.isButtonDown(code + 100) : code > 0 && Keyboard.isKeyDown(code);
    if (toggleSneak.get()) {
      if (mc.currentScreen == null && down && !lastSneakKey) sneakToggled = !sneakToggled;
      lastSneakKey = down;
      container = inContainers.get() && mc.currentScreen instanceof GuiContainer;
      sneaking = sneakToggled || container;
    } else {
      sneakToggled = sneaking = false;
    }

    // the status text (display only: nothing below changes how sprint or sneak behave)
    boolean sprinting = player != null && player.isSprinting();
    boolean sneakDown = down && mc.currentScreen == null || sneakToggled;
    Setting.Text line;
    if (player != null && player.isRiding()) line = sneakDown ? dismountingText : ridingText;
    else if (player != null && player.capabilities.isFlying) line = sneakDown ? descendingText : flyingText;
    else if (container) line = sneakContainerText;
    else if (sneaking) line = sneakToggledText;
    else if (player != null && player.isSneaking()) line = sneakHeldText;
    else if (ALWAYS.equals(mode)) line = sprinting ? sprintAlwaysText : null;
    else if (sprintToggled) line = sprintToggledText;
    else if (sprinting && key) line = sprintHeldText;
    else if (sprinting) line = sprintVanillaText;
    else line = null;
    String text = line == null ? "" : resolve(line.get());
    idle = text.isEmpty();
    if (!idle) label(text);
    if (!showLabel.get()) size(0, 0);
    else if (contentWidth() <= 0) resize();
  }

  /** The line with {boost} filled in: luka has no fly boost, so it becomes nothing. Lines without it are used as they are. */
  private String resolve(String raw) {
    if (raw.indexOf(BOOST) < 0) return raw;
    if (!raw.equals(boostRaw)) {
      boostRaw = raw;
      boostResolved = raw.replace(BOOST, "");
    }
    return boostResolved;
  }

  @Override
  public void enabled(boolean on) {
    super.enabled(on);
    if (!on) {
      sprintToggled = false;
      sneakToggled = false;
      sneaking = false;
      idle = true;
    }
  }

  @Override
  protected void draw(float partialTicks, boolean editor) {
    if (showLabel.get() && (editor || !idle)) super.draw(partialTicks, editor);
  }
}

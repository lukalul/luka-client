package dev.cbui.hud;

/**
 * One draggable box in the CBUI HUD layout editor, whoever owns its placement: a native CBUI module
 * (anchor + offset) or any OneConfig {@code Hud} (CBUI's own modules registered with OneConfig, or
 * another mod's HUD such as EvergreenHUD's). All coordinates are GUI units, top-left origin, and
 * already include the element's scale.
 */
public interface HudElement {
  String name();

  boolean enabled();

  void enabled(boolean on);

  float x();

  float y();

  float width();

  float height();

  /** Moves the scaled box's top-left corner to (x, y). */
  void moveTo(float x, float y);

  float scale();

  void scale(float value);

  /** The scale the box's content is actually drawn at (OneConfig's effective scale for its HUDs). */
  float drawScale();

  boolean locked();

  void dragStart();

  /** Called once when a drag (or scale/toggle edit) ends; persists the owner's placement. */
  void dragEnd();

  /** Restores the default placement if the owner has one; may do nothing. */
  void resetLayout();

  /** The CBUI module drawn in this box, or null for another mod's OneConfig HUD. */
  AbstractHudModule module();

  /** False while the box is hidden from the HUD (the module stays on). */
  default boolean hudVisible() { return true; }

  default void hudVisible(boolean visible) { }

  /** True when OneConfig owns this element's placement (rather than CBUI's anchor/offset store). */
  boolean oneConfig();
}

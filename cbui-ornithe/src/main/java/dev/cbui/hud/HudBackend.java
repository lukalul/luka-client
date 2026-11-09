package dev.cbui.hud;

import dev.cbui.oneconfig.ConfigSnapshot;
import java.util.List;
import java.util.Set;

/**
 * The optional placement/rendering host for HUD modules. The only implementation links against
 * OneConfig's HUD API ({@code dev.cbui.oneconfig.hud.OneConfigHudBackend}) and is loaded reflectively,
 * so this interface keeps every OneConfig type out of the rest of CBUI. When it is missing or
 * fails, CBUI renders and places every module natively.
 */
public interface HudBackend {
  /** Registers each module that has a HUD box as a real OneConfig HUD. */
  void register(List<AbstractHudModule> modules, Set<String> placed);

  /** Client tick: two-way sync of enabled/scale and first-time position migration. */
  void tick();

  /** True while a live, working OneConfig HUD instance renders and places this module. */
  boolean manages(AbstractHudModule module);

  /** Every live OneConfig HUD instance (CBUI's and other mods'), as editor elements. */
  List<HudElement> elements();

  /** Settings of another mod's HUD as CBUI rows, or null when none are exposed. */
  ConfigSnapshot.Mod options(HudElement element);

  /** Config tree ids of every live OneConfig HUD (so the MODS grid does not list them twice). */
  Set<String> treeIds();

  /** The CBUI HUD editor opened or closed: show other mods' HUDs under it, draw our own ourselves. */
  void editorOpen(boolean open);

  /** False once the backend failed; CBUI then falls back to native rendering for everything. */
  boolean healthy();
}

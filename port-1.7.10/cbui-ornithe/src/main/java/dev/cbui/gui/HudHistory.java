package dev.cbui.gui;

import dev.cbui.hud.AbstractHudModule;
import dev.cbui.hud.Anchor;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Undo/redo for the HUD editor: whole-layout snapshots (anchor, offset, scale, on/off, visible), taken before each change. */
final class HudHistory {
  static final class Entry {
    final AbstractHudModule module;
    final Anchor anchor;
    final float x, y, scale;
    final boolean enabled, visible;

    Entry(AbstractHudModule m) {
      module = m;
      anchor = m.anchor();
      x = m.offsetX();
      y = m.offsetY();
      scale = m.scale();
      enabled = m.userEnabled();
      visible = m.hudVisible();
    }

    void restore() {
      module.anchor(anchor);
      module.offset(x, y);
      module.scale(scale);
      module.enabled(enabled);
      module.hudVisible(visible);
    }
  }

  private static final int LIMIT = 60;
  private final ArrayDeque<List<Entry>> undo = new ArrayDeque<>();
  private final ArrayDeque<List<Entry>> redo = new ArrayDeque<>();

  static List<Entry> take(List<AbstractHudModule> modules) {
    List<Entry> out = new ArrayList<>(modules.size());
    for (AbstractHudModule m : modules) out.add(new Entry(m));
    return out;
  }

  /** Remember the layout as it is now, before a change. */
  void push(List<AbstractHudModule> modules) {
    pushSnapshot(take(modules));
  }

  void pushSnapshot(List<Entry> snapshot) {
    undo.push(snapshot);
    if (undo.size() > LIMIT) undo.removeLast();
    redo.clear();
  }

  boolean undo(List<AbstractHudModule> modules) {
    if (undo.isEmpty()) return false;
    redo.push(take(modules));
    for (Entry e : undo.pop()) e.restore();
    return true;
  }

  boolean redo(List<AbstractHudModule> modules) {
    if (redo.isEmpty()) return false;
    undo.push(take(modules));
    for (Entry e : redo.pop()) e.restore();
    return true;
  }

  void clear() {
    undo.clear();
    redo.clear();
  }
}

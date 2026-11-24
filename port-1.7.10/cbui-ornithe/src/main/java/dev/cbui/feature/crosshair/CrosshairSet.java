package dev.cbui.feature.crosshair;

import dev.cbui.hud.Setting;

/** The settings of one crosshair profile (Default, Ally or Foe). The module creates the rows in menu order. */
final class CrosshairSet {
  static final String IMPORTED = "Imported art";
  static final String[] SHAPES = {"Cross", "Diagonal cross", "Dot", "Circle", "Box", IMPORTED};
  static final String[] CANVASES = {"Small", "Medium", "Large", "Huge"};

  /** 0 = Default, 1 = Ally, 2 = Foe; also the index of the imported art file. */
  final int slot;
  final String title;

  Setting.Toggle on;
  Setting.Choice shape;
  Setting.Color colour;
  Setting.Slider reach;
  Setting.Slider lineWidth;
  Setting.Slider gap;
  Setting.Toggle keepMiddle;
  Setting.Choice canvas;
  Setting.Slider turn;
  Setting.Toggle border;
  Setting.Slider borderWidth;
  Setting.Color borderColour;
  Setting.Toggle invert;
  Setting.Color invertTint;
  Setting.Toggle lowHealth;
  Setting.Slider lowHealthAt;
  Setting.Color lowHealthColour;
  Setting.Toggle bowSpread;
  Setting.Slider spread;
  Setting.Toggle ownSize;
  Setting.Slider size;
  Setting.Toggle exact;
  Setting.Toggle thirdPerson;
  /** Null on versions without spectator mode. */
  Setting.Toggle spectator;

  CrosshairSet(int slot, String title) {
    this.slot = slot;
    this.title = title;
  }

  /** Number of cells along one side of the drawing area of a built-in shape. */
  int canvasCells() {
    String c = canvas.get();
    if ("Small".equals(c)) return 7;
    if ("Large".equals(c)) return 31;
    if ("Huge".equals(c)) return 63;
    return 15;
  }

  int shapeIndex() {
    String s = shape.get();
    for (int i = 0; i < SHAPES.length; i++) if (SHAPES[i].equals(s)) return i;
    return 0;
  }
}

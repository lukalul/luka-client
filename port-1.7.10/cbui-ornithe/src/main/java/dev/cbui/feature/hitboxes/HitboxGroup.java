package dev.cbui.feature.hitboxes;

import dev.cbui.hud.Setting;

/** The settings of one entity category. Default holds the base values the others fall back to. */
final class HitboxGroup {
  static final String ALWAYS = "Always";
  static final String WHEN_TOGGLED = "When toggled";
  static final String WHEN_AIMED = "When aimed at";
  static final String NEVER = "Never";
  static final String[] VISIBILITY = {ALWAYS, WHEN_TOGGLED, WHEN_AIMED, NEVER};
  static final String SOLID = "Solid";
  static final String DASHED = "Dashed";
  static final String[] PATTERNS = {SOLID, DASHED};

  final String title;

  /** Null on Default. */
  Setting.Toggle ownVisibility;
  Setting.Choice visibility;
  /** Only on the arrow category. */
  Setting.Toggle stuckArrows;
  /** Null on Default. */
  Setting.Toggle ownLook;
  Setting.Choice pattern;
  Setting.Slider dash;
  Setting.Toggle throughModel;
  Setting.Toggle aimedColours;
  Setting.Toggle hitColours;
  Setting.Toggle fill;
  Setting.Color fillColour;
  Setting.Color fillAimed;
  Setting.Color fillHit;
  Setting.Toggle edges;
  Setting.Color edgeColour;
  Setting.Color edgeAimed;
  Setting.Color edgeHit;
  Setting.Slider edgeWidth;
  Setting.Toggle eye;
  Setting.Color eyeColour;
  Setting.Color eyeAimed;
  Setting.Color eyeHit;
  Setting.Slider eyeWidth;
  Setting.Toggle look;
  Setting.Color lookColour;
  Setting.Color lookAimed;
  Setting.Color lookHit;
  Setting.Slider lookWidth;

  HitboxGroup(String title) {
    this.title = title;
  }

  boolean anyPart() {
    return fill.get() || edges.get() || eye.get() || look.get();
  }

  /** Colour set to use: 0 = normal, 1 = aimed at, 2 = hit cooldown. */
  int pick(int normal, int aimed, int hit, int state) {
    return state == 2 ? hit : state == 1 ? aimed : normal;
  }
}

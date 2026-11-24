package dev.cbui;

import dev.cbui.feature.hotbarwheel.WheelStep;
import dev.cbui.feature.leveldigits.LevelText;

/** Plain-Java checks for the hotbar wheel arithmetic (Scroll Tweaks) and the level digits (Numerical Enchantments). */
final class WheelAndLevelChecks {
  private WheelAndLevelChecks() {}

  static void run() {
    wheel();
    levels();
  }

  private static void wheel() {
    int[] slots = {0, 4, 8};
    int[] sizes = {1, 120};
    for (int mask = 0; mask < 8; mask++) {
      boolean locked = (mask & 1) != 0;
      boolean flipped = (mask & 2) != 0;
      boolean clamped = (mask & 4) != 0;
      for (int slot : slots) {
        for (int size : sizes) {
          for (int sign = -1; sign <= 1; sign += 2) {
            int delta = sign * size;
            int got = WheelStep.apply(slot, delta, locked, flipped, clamped);
            int want = expected(slot, delta, locked, flipped, clamped);
            if (got != want) {
              throw new AssertionError("wheel: slot " + slot + " delta " + delta + " locked=" + locked + " flipped="
                  + flipped + " clamped=" + clamped + " gave " + got + ", expected " + want);
            }
            if (got < 0 || got > 8) throw new AssertionError("wheel left the hotbar: " + got);
            if (Math.abs(got - slot) > 1 && !(Math.abs(got - slot) == 8 && !clamped)) {
              throw new AssertionError("wheel moved more than one slot: " + slot + " -> " + got);
            }
          }
        }
      }
    }
    // spot checks written out by hand
    same(3, WheelStep.apply(4, 1, false, false, false), "wheel up moves towards slot 1");
    same(5, WheelStep.apply(4, -120, false, false, false), "wheel down moves towards slot 9, one slot per event");
    same(8, WheelStep.apply(0, 1, false, false, false), "wrap from the first slot to the last");
    same(0, WheelStep.apply(8, -1, false, false, false), "wrap from the last slot to the first");
    same(0, WheelStep.apply(0, 120, false, false, true), "stop at the first slot");
    same(8, WheelStep.apply(8, -120, false, false, true), "stop at the last slot");
    same(5, WheelStep.apply(4, 1, false, true, false), "flipped wheel up moves towards slot 9");
    same(8, WheelStep.apply(8, 1, false, true, true), "flipped and stopped at the last slot");
    same(0, WheelStep.apply(0, -1, false, true, true), "flipped and stopped at the first slot");
    same(4, WheelStep.apply(4, 1, true, true, true), "locked wheel leaves the slot");
    same(4, WheelStep.apply(4, 0, false, false, false), "no delta leaves the slot");
  }

  /** The spec's rules written a second, independent way. */
  private static int expected(int slot, int delta, boolean locked, boolean flipped, boolean clamped) {
    if (locked) return slot;
    boolean towardsFirst = delta > 0;
    if (flipped) towardsFirst = !towardsFirst;
    if (towardsFirst) {
      if (slot == 0) return clamped ? 0 : 8;
      return slot - 1;
    }
    if (slot == 8) return clamped ? 8 : 0;
    return slot + 1;
  }

  private static void levels() {
    same("5", LevelText.enchantmentLevel("enchantment.level.5"), "enchantment level 5");
    same("1", LevelText.enchantmentLevel("enchantment.level.1"), "enchantment level 1");
    same("32", LevelText.enchantmentLevel("enchantment.level.32"), "enchantment level above 10");
    same(null, LevelText.enchantmentLevel("enchantment.damage.all"), "an enchantment name is not a level");
    same(null, LevelText.enchantmentLevel("enchantment.level."), "an empty level is left alone");
    same(null, LevelText.enchantmentLevel("enchantment.level.x"), "a non-number level is left alone");
    same("2", LevelText.potionStrength("potion.potency.1"), "potion amplifier 1 is strength 2");
    same("3", LevelText.potionStrength("potion.potency.2"), "potion amplifier 2 is strength 3");
    same(null, LevelText.potionStrength("potion.moveSpeed"), "an effect name is not a strength");
    same(null, LevelText.potionStrength("attribute.name.generic.attackDamage"), "attribute lines are left alone");
    same(null, LevelText.potionStrength("enchantment.level.2"), "potion lookups ignore enchantment keys");
  }

  private static void same(Object want, Object got, String what) {
    if (want == null ? got != null : !want.equals(got)) {
      throw new AssertionError(what + ": expected " + want + ", got " + got);
    }
  }
}

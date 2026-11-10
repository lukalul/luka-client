package dev.cbui.feature.damagetint;

/** Small state mixed directly into living entities; avoids maps and render-loop allocation. */
public interface DamageTintState {
  int OTHER = 0, MELEE = 1, RANGED = 2, MAGIC = 3, CRIT = 4, MACE = 5, EXPLOSION = 6, BLOCK = 7;

  int cbui$getDamageVariant();
  void cbui$setDamageVariant(int variant);
  int cbui$getHurtTick();
  void cbui$setHurtTick(int tick);
}

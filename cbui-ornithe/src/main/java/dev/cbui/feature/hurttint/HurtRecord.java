package dev.cbui.feature.hurttint;

/**
 * Memory that a mixin adds to every living entity: the kind of the entity's latest hurt and when it, and the latest
 * critical-hit sparkle, arrived. Stamps hold {@code ticksExisted + 1} so that 0 can mean "never" (fields start at 0).
 */
public interface HurtRecord {
  int cbui$hurtKind();

  void cbui$setHurtKind(int kind);

  int cbui$hurtStamp();

  void cbui$setHurtStamp(int stamp);

  int cbui$critStamp();

  void cbui$setCritStamp(int stamp);
}

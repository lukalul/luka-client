package dev.cbui.feature.damagetint;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemStack;

/** Remembers, per entity, how its latest hit looked, using the little state object mixed into every living entity. */
public final class DamageTracking {
  /** Entity-status packet code meaning "this entity was hurt". */
  public static final int STATUS_HURT = 2;
  /** Animation packet code for the critical-hit sparkle. */
  public static final int ANIMATION_CRIT = 4;
  /** How soon after a hit the "critical" animation packet may arrive and still belong to that hit. */
  private static final int CRIT_WINDOW_TICKS = 1;

  private DamageTracking() {}

  /** Call when the server says an entity was hurt. */
  public static void recordHurt(Entity entity) {
    if (!(entity instanceof EntityLivingBase) || !(entity instanceof DamageTintState)) return;
    EntityLivingBase living = (EntityLivingBase) entity;
    DamageTintState state = (DamageTintState) entity;
    state.cbui$setDamageVariant(isBlocking(living) ? DamageTintState.BLOCK : DamageTintState.OTHER);
    state.cbui$setHurtTick(living.ticksExisted);
  }

  /** Call when the server plays the critical-hit animation on an entity; upgrades the hit if it just happened. */
  public static void recordCritAnimation(Entity entity) {
    if (!(entity instanceof EntityLivingBase) || !(entity instanceof DamageTintState)) return;
    EntityLivingBase living = (EntityLivingBase) entity;
    DamageTintState state = (DamageTintState) entity;
    int variant = state.cbui$getDamageVariant();
    boolean freshHit = living.ticksExisted - state.cbui$getHurtTick() <= CRIT_WINDOW_TICKS && living.hurtTime > 0;
    boolean upgradable = variant != DamageTintState.MACE && variant != DamageTintState.BLOCK;
    if (freshHit && upgradable) state.cbui$setDamageVariant(DamageTintState.CRIT);
  }

  private static boolean isBlocking(EntityLivingBase living) {
    if (!(living instanceof EntityPlayer)) return false;
    EntityPlayer player = (EntityPlayer) living;
    if (player.isBlocking()) return true;
    ItemStack held = player.getHeldItem();
    return player.isUsingItem() && held != null && held.getItemUseAction() == EnumAction.BLOCK;
  }
}

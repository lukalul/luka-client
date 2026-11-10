package dev.cbui.mixin;

import dev.cbui.feature.damagetint.DamageTintState;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Gives every living entity two ints of storage for the tint: how its last hit looked and when it happened. */
@Mixin(EntityLivingBase.class)
public abstract class DamageTintLivingEntityMixin implements DamageTintState {
  @Unique private int cbui$lastHitKind = DamageTintState.OTHER;
  @Unique private int cbui$lastHitAtTick;

  @Override public int cbui$getDamageVariant() { return cbui$lastHitKind; }

  @Override public void cbui$setDamageVariant(int variant) { cbui$lastHitKind = variant; }

  @Override public int cbui$getHurtTick() { return cbui$lastHitAtTick; }

  @Override public void cbui$setHurtTick(int tick) { cbui$lastHitAtTick = tick; }
}

package dev.cbui.mixin;

import dev.cbui.feature.hurttint.HurtRecord;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Damage Tint: gives every living entity a small memory of how its last hit landed. Zero means "regular, never". */
@Mixin(EntityLivingBase.class)
public abstract class HurtTintLivingEntityMixin implements HurtRecord {
  @Unique private int cbui$kind;
  @Unique private int cbui$hurtAt;
  @Unique private int cbui$critAt;

  @Override public int cbui$hurtKind() { return cbui$kind; }

  @Override public void cbui$setHurtKind(int kind) { cbui$kind = kind; }

  @Override public int cbui$hurtStamp() { return cbui$hurtAt; }

  @Override public void cbui$setHurtStamp(int stamp) { cbui$hurtAt = stamp; }

  @Override public int cbui$critStamp() { return cbui$critAt; }

  @Override public void cbui$setCritStamp(int stamp) { cbui$critAt = stamp; }
}

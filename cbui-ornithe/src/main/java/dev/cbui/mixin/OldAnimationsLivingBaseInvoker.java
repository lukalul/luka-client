package dev.cbui.mixin;

import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** {@code EntityLivingBase.getArmSwingAnimationEnd} is private in 1.8.9; the swing logic of Old Animations needs it. */
@Mixin(EntityLivingBase.class)
public interface OldAnimationsLivingBaseInvoker {
  @Invoker("getArmSwingAnimationEnd")
  int cbui$armSwingEnd();
}

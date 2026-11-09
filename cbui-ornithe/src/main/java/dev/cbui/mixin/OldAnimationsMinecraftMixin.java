package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.cbui.feature.oldanimations.OldAnimationsHooks;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Old Animations: block hitting (use items while mining) and always swing. */
@Mixin(Minecraft.class)
public abstract class OldAnimationsMinecraftMixin {
  @Shadow private int leftClickCounter;

  @ModifyExpressionValue(method = "rightClickMouse",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/PlayerControllerMP;getIsHittingBlock()Z"), require = 0)
  private boolean cbui$oldUseWhileMining(boolean original) {
    try { return OldAnimationsHooks.hittingBlockForUse(original); } catch (Throwable ignored) { return original; }
  }

  @Inject(method = "sendClickBlockToController", at = @At("HEAD"), require = 0)
  private void cbui$oldBlockHitSwing(boolean holdingAttack, CallbackInfo ci) {
    try {
      Minecraft mc = (Minecraft) (Object) this;
      if (mc.thePlayer == null) return;
      OldAnimationsHooks.blockHitSwing(mc, holdingAttack, leftClickCounter, ((OldAnimationsLivingBaseInvoker) (Object) mc.thePlayer).cbui$armSwingEnd());
    } catch (Throwable ignored) { }
  }

  @Inject(method = "clickMouse", at = @At("HEAD"), require = 0)
  private void cbui$oldAlwaysSwing(CallbackInfo ci) {
    try {
      Minecraft mc = (Minecraft) (Object) this;
      if (mc.thePlayer == null) return;
      OldAnimationsHooks.alwaysSwing(mc, ((OldAnimationsLivingBaseInvoker) (Object) mc.thePlayer).cbui$armSwingEnd());
    } catch (Throwable ignored) { }
  }
}

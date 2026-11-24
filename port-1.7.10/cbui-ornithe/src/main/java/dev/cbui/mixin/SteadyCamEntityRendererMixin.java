package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.cbui.Cbui;
import dev.cbui.feature.steadycam.SteadyCamModule;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * the steady camera card: lets each bob and hurt-tilt call of the world pass and the hand pass be skipped on its own, and zeroes
 * the sideways part of the bob. Conditions, not cancels, so zoom's and BetterHurtCam's hooks on the same calls still run.
 */
@Mixin(EntityRenderer.class)
public abstract class SteadyCamEntityRendererMixin {
  @WrapWithCondition(method = "setupCameraTransform",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;setupViewBobbing(F)V"), require = 0)
  private boolean cbui$steadyViewBob(EntityRenderer self, float partialTicks) {
    try {
      SteadyCamModule module = SteadyCamModule.active();
      return module == null || module.keepsViewBob();
    } catch (Throwable error) {
      Cbui.reportOnce("shake tweaks view bob", error);
      return true;
    }
  }

  @WrapWithCondition(method = "setupCameraTransform",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;hurtCameraEffect(F)V"), require = 0)
  private boolean cbui$steadyViewHurt(EntityRenderer self, float partialTicks) {
    try {
      SteadyCamModule module = SteadyCamModule.active();
      return module == null || module.keepsViewHurt();
    } catch (Throwable error) {
      Cbui.reportOnce("shake tweaks view hurt", error);
      return true;
    }
  }

  @WrapWithCondition(method = "renderHand",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;setupViewBobbing(F)V"), require = 0)
  private boolean cbui$steadyHandBob(EntityRenderer self, float partialTicks) {
    try {
      SteadyCamModule module = SteadyCamModule.active();
      return module == null || module.keepsHandBob();
    } catch (Throwable error) {
      Cbui.reportOnce("shake tweaks hand bob", error);
      return true;
    }
  }

  @WrapWithCondition(method = "renderHand",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;hurtCameraEffect(F)V"), require = 0)
  private boolean cbui$steadyHandHurt(EntityRenderer self, float partialTicks) {
    try {
      SteadyCamModule module = SteadyCamModule.active();
      return module == null || module.keepsHandHurt();
    } catch (Throwable error) {
      Cbui.reportOnce("shake tweaks hand hurt", error);
      return true;
    }
  }

  /** The first translation of the bob: its X is the sideways shift. */
  @ModifyArg(method = "setupViewBobbing",
      at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glTranslatef(FFF)V", ordinal = 0, remap = false),
      index = 0, require = 0)
  private float cbui$steadySideShift(float x) {
    return cbui$vertical() ? 0f : x;
  }

  /** The first rotation of the bob is the roll around the view axis. */
  @ModifyArg(method = "setupViewBobbing",
      at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glRotatef(FFFF)V", ordinal = 0, remap = false),
      index = 0, require = 0)
  private float cbui$steadyRoll(float angle) {
    return cbui$vertical() ? 0f : angle;
  }

  private static boolean cbui$vertical() {
    try {
      SteadyCamModule module = SteadyCamModule.active();
      return module != null && module.verticalBobOnly();
    } catch (Throwable error) {
      Cbui.reportOnce("shake tweaks vertical bob", error);
      return false;
    }
  }
}

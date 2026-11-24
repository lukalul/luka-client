package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cbui.Cbui;
import dev.cbui.feature.skyhorizon.SkyHorizonModule;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * the sky horizon card inside renderSky: drops the dark lower dome and the black walls drawn under the horizon, and the
 * sunrise/sunset glow. Draw call sites are wrapped, never removed, so other hooks that count them by ordinal keep
 * finding theirs.
 */
@Mixin(RenderGlobal.class)
public abstract class SkyHorizonRenderGlobalMixin {
  @Shadow private int glSkyList2;

  /** Only the display list of the lower dome is skipped; the main dome and the stars stay. */
  @WrapWithCondition(method = "renderSky(F)V",
      at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glCallList(I)V", remap = false), require = 0)
  private boolean cbui$skyLowerList(int list) {
    return list != glSkyList2 || !cbui$hideLower();
  }

  /**
   * The fifth tessellator draw of renderSky (after the End sky, glow, sun and moon) is the black box around the camera
   * when the eyes are under the horizon.
   */
  @WrapOperation(method = "renderSky(F)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;draw()I", ordinal = 4), require = 0)
  private int cbui$skyWalls(Tessellator tessellator, Operation<Integer> original) {
    if (!cbui$hideLower()) return original.call(tessellator);
    dev.luka.compat.Compat.discardTessellator();
    return 0;
  }

  /** No colours means vanilla skips the glow fan. Only this call; the fog colour has its own. */
  @ModifyExpressionValue(method = "renderSky(F)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/WorldProvider;calcSunriseSunsetColors(FF)[F"), require = 0)
  private float[] cbui$skyGlow(float[] colors) {
    if (colors == null) return null;
    try {
      SkyHorizonModule module = SkyHorizonModule.active();
      return module != null && module.hidesGlow() ? null : colors;
    } catch (Throwable error) {
      Cbui.reportOnce("smooth skies glow", error);
      return colors;
    }
  }

  private static boolean cbui$hideLower() {
    try {
      SkyHorizonModule module = SkyHorizonModule.active();
      return module != null && module.hidesLowerSky();
    } catch (Throwable error) {
      Cbui.reportOnce("smooth skies lower sky", error);
      return false;
    }
  }
}

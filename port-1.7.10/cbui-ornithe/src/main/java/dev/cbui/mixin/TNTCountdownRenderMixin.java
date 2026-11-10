package dev.cbui.mixin;

import dev.cbui.feature.tntcountdown.TNTCountdownModule;
import net.minecraft.client.renderer.entity.Render;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Render.class)
public abstract class TNTCountdownRenderMixin {
  private static final String FONT_DRAW = "Lnet/minecraft/client/gui/FontRenderer;drawString(Ljava/lang/String;III)I";
  private static int applyCountdownColor(int vanillaColor) {
    int replacement = TNTCountdownModule.labelRgb;
    return replacement == -1 ? vanillaColor : vanillaColor & 0xFF000000 | replacement;
  }
  @ModifyArg(method = "renderLivingLabel", at = @At(value = "INVOKE", target = FONT_DRAW, ordinal = 0), index = 3, require = 0)
  private int cbui$colorTransparentPass(int vanillaColor) { return applyCountdownColor(vanillaColor); }
  @ModifyArg(method = "renderLivingLabel", at = @At(value = "INVOKE", target = FONT_DRAW, ordinal = 1), index = 3, require = 0)
  private int cbui$colorOpaquePass(int vanillaColor) { return applyCountdownColor(vanillaColor); }
}

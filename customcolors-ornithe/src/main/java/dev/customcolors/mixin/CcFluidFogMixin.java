package dev.customcolors.mixin;

import dev.customcolors.CustomColorsMod;
import dev.customcolors.color.SkyTints;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Underwater and underlava fog colour from the pack, written right after vanilla chose its own
 * water/lava colour and before that colour is darkened and used.
 */
@Mixin(EntityRenderer.class)
public abstract class CcFluidFogMixin {
    @Shadow private Minecraft mc;
    @Shadow private boolean cloudFog;
    @Shadow private float fogColorRed;
    @Shadow private float fogColorGreen;
    @Shadow private float fogColorBlue;

    @Inject(method = "updateFogColor",
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/EntityRenderer;fogColor2:F",
            opcode = Opcodes.GETFIELD, ordinal = 0),
        require = 0)
    private void customcolors$fluid(float partialTicks, CallbackInfo ci) {
        try {
            if (cloudFog) {
                return;
            }
            if (SkyTints.fluidFog(mc.theWorld, mc.getRenderViewEntity(), partialTicks)) {
                fogColorRed = SkyTints.fogRed;
                fogColorGreen = SkyTints.fogGreen;
                fogColorBlue = SkyTints.fogBlue;
            }
        } catch (Throwable t) {
            CustomColorsMod.reportOnce("fluid fog colour", t);
        }
    }
}

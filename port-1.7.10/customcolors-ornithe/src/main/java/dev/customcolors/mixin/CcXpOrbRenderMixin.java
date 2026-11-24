package dev.customcolors.mixin;

import dev.customcolors.CustomColorsMod;
import dev.customcolors.color.ParticleTints;
import net.minecraft.client.renderer.entity.RenderXPOrb;
import net.minecraft.entity.item.EntityXPOrb;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Experience orbs cycle through the pack's xporb list instead of vanilla's green-yellow pulse. */
@Mixin(RenderXPOrb.class)
public abstract class CcXpOrbRenderMixin {
    /** Colour picked for the orb being drawn; meaningful only while {@code customcolors$tinted}. */
    @Unique private int customcolors$colour;
    @Unique private boolean customcolors$tinted;

    @Inject(method = "doRender(Lnet/minecraft/entity/item/EntityXPOrb;DDDFF)V", at = @At("HEAD"), require = 0)
    private void customcolors$pick(EntityXPOrb orb, double x, double y, double z, float yaw, float partialTicks,
                                   CallbackInfo ci) {
        try {
            int c = ParticleTints.xpOrb(orb.xpColor, partialTicks);
            customcolors$tinted = c >= 0;
            customcolors$colour = c;
        } catch (Throwable t) {
            customcolors$tinted = false;
            CustomColorsMod.reportOnce("experience orb colour", t);
        }
    }

    @ModifyArg(method = "doRender(Lnet/minecraft/entity/item/EntityXPOrb;DDDFF)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;setColorRGBA_I(II)V"),
        index = 0, require = 0)
    private int customcolors$colour(int vanilla) {
        return customcolors$tinted ? customcolors$colour : vanilla;
    }
}

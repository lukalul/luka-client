package dev.customcolors.mixin;

import dev.customcolors.CustomColors;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderXPOrb;
import net.minecraft.entity.item.EntityXPOrb;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RenderXPOrb.class)
public abstract class RenderXPOrbMixin {
    @Redirect(method = "doRender(Lnet/minecraft/entity/item/EntityXPOrb;DDDFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;setColorRGBA_I(II)V"))
    private void customcolors$color(Tessellator tessellator, int rgb, int alpha,
                                    EntityXPOrb orb, double x, double y, double z, float yaw, float partialTicks) {
        int color = CustomColors.xpOrb((orb.xpColor + partialTicks) / 2.0F);
        tessellator.setColorRGBA_I(color == -1 ? rgb : color, alpha);
    }
}

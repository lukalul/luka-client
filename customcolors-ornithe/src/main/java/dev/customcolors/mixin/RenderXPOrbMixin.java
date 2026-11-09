package dev.customcolors.mixin;

import dev.customcolors.CustomColors;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderXPOrb;
import net.minecraft.entity.item.EntityXPOrb;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RenderXPOrb.class)
public abstract class RenderXPOrbMixin {
    @Redirect(method = "doRender(Lnet/minecraft/entity/item/EntityXPOrb;DDDFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/WorldRenderer;color(IIII)Lnet/minecraft/client/renderer/WorldRenderer;"))
    private WorldRenderer customcolors$color(WorldRenderer buffer, int red, int green, int blue, int alpha,
                                             EntityXPOrb orb, double x, double y, double z, float yaw, float partialTicks) {
        int color = CustomColors.xpOrb((orb.xpColor + partialTicks) / 2.0F);
        if (color == -1) return buffer.color(red, green, blue, alpha);
        return buffer.color(color >> 16 & 255, color >> 8 & 255, color & 255, alpha);
    }
}

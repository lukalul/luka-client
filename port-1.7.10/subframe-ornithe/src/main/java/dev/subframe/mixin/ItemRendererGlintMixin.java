package dev.subframe.mixin;

import dev.subframe.SubFrameMod;
import net.minecraft.client.renderer.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The enchantment shine on held items follows the replay's clock (see SubFrameMod.glintMillis). */
@Mixin(ItemRenderer.class)
public abstract class ItemRendererGlintMixin {
    @Redirect(method = "renderItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getSystemTime()J"), require = 0)
    private long subframe$glintClock() {
        return SubFrameMod.glintMillis();
    }
}

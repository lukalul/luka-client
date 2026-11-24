package dev.subframe.mixin;

import dev.subframe.SubFrameMod;
import net.minecraft.client.renderer.entity.RenderItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The enchantment shine on items follows the replay's clock in replays and renders (see SubFrameMod.glintMillis). */
@Mixin(RenderItem.class)
public abstract class RenderItemGlintMixin {
    @Redirect(method = "renderEffect", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getSystemTime()J"), require = 0)
    private long subframe$glintClock() {
        return SubFrameMod.glintMillis();
    }
}

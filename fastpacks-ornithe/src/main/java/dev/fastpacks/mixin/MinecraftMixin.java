package dev.fastpacks.mixin;

import dev.fastpacks.FastPacks;
import dev.fastpacks.gui.GuiFastResourcePacks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenResourcePacks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Shadow private GuiScreen currentScreen;

    @ModifyVariable(method = "displayGuiScreen", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private GuiScreen fastpacks$replacePackScreen(GuiScreen screen) {
        if (FastPacks.consumeVanillaFallback()) return screen;
        if (!(screen instanceof GuiScreenResourcePacks) || screen instanceof GuiFastResourcePacks) return screen;
        try { return new GuiFastResourcePacks(currentScreen); }
        catch (Throwable t) { FastPacks.reportOnce("screen hook", t); return screen; }
    }
}

package dev.subframe;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.GuiIngame;

/** Vanilla in-game GUI without the screen-edge vignette, so it cannot differ between live and replay. */
final class QuietIngameGui extends GuiIngame {
    QuietIngameGui(Minecraft mc) {
        super(mc);
    }

    protected void renderVignette(float lightLevel, ScaledResolution scaledRes) {
    }
}

package dev.subframe;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;

/** A small red "REC" in the corner while a take is being recorded. */
final class RecIndicator {
    static void render() {
        try {
            renderImpl();
        } catch (Throwable t) {
            SubFrameMod.problem("onOverlay", t);
        }
    }

    private static void renderImpl() {
        // not drawn in the pixel-diff test, whose live frames must match the replay exactly
        if (!SubFrameMod.showIndicator || SubFrameMod.captureLive || !Recorder.isCapturing()) {
            return;
        }
        Gui.drawRect(4, 4, 10, 10, 0xFFE03030);
        Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow("REC", 14, 3, 0xFFFFFF);
    }
}

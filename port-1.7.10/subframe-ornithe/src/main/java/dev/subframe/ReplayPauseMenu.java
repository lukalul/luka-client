package dev.subframe;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngameMenu;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

/**
 * The game's own pause menu, opened from the viewer menu. Other mods (OneConfig, Patcher, HUD editors, ...) add their
 * settings buttons to that menu, so showing it is the way into their screens while watching a replay. Only the buttons
 * that would act on the fake connection are redirected.
 */
final class ReplayPauseMenu extends GuiIngameMenu {
    @Override
    protected void actionPerformed(GuiButton button) {
        Replay r = Replay.active;
        switch (button.id) {
            case 1: // "Disconnect" / "Save and quit to title"
                if (r != null) {
                    r.quit();
                }
                return;
            case 4: // "Back to game"
                back();
                return;
            case 7: // "Open to LAN": meaningless for a replay
                return;
            default:
                super.actionPerformed(button);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            back();
        } else {
            super.keyTyped(typedChar, keyCode);
        }
    }

    private void back() {
        Replay r = Replay.active;
        mc.displayGuiScreen(r != null ? r.screen() : null);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}

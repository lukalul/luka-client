package dev.cbui.gui;

import dev.cbui.Cbui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiScreen;

/**
 * The normal pause screen with one extra button: Mods & HUD (the luka menu, with the live HUD behind it), in the full-width slot between the statistics row and the options row (where a
 * mod list button used to sit). Everything else is the vanilla screen, so the confirm-disconnect feature and other mods hooking it keep working.
 */
public final class LukaPauseMenu extends GuiIngameMenu {
    private static final int HUD_BUTTON = 9101, FRIENDS_BUTTON = 9102;

    /** Pause screens made by the game are swapped for ours; anything else (ours already, a subclass from another mod) is left alone. */
    public static GuiScreen swap(GuiScreen screen) {
        if (screen == null || screen.getClass() != GuiIngameMenu.class) return screen;
        try { return new LukaPauseMenu(); }
        catch (Throwable error) { Cbui.reportOnce("pause menu construction", error); return screen; }
    }

    @Override public void initGui() {
        super.initGui();
        try {
            // vanilla rows sit at height/4 + 24 (back to game), + 48 (achievements / statistics), + 96 (options / LAN), + 120 (disconnect)
            int row = this.height / 4 + 72 - 16;
            if (dev.cbui.Release.SOCIAL) {
                this.buttonList.add(new GuiButton(HUD_BUTTON, this.width / 2 - 100, row, 98, 20, "Mods & HUD"));
                this.buttonList.add(new GuiButton(FRIENDS_BUTTON, this.width / 2 + 2, row, 98, 20, "Friends"));
            } else {
                this.buttonList.add(new GuiButton(HUD_BUTTON, this.width / 2 - 100, row, 200, 20, "Mods & HUD"));
            }
        } catch (Throwable error) { Cbui.reportOnce("pause menu HUD button", error); }
    }

    @Override protected void actionPerformed(GuiButton button) {
        if (button.id == FRIENDS_BUTTON) {
            try { this.mc.displayGuiScreen(new FriendsScreen(this)); }
            catch (Throwable error) { Cbui.reportOnce("pause menu friends screen", error); }
            return;
        }
        if (button.id == HUD_BUTTON) {
            try { this.mc.displayGuiScreen(new ModuleMenu(this)); }
            catch (Throwable error) { Cbui.reportOnce("pause menu HUD screen", error); }
            return;
        }
        super.actionPerformed(button);
    }
}

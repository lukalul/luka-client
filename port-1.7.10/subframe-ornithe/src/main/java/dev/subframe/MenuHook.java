package dev.subframe;

import net.minecraft.client.gui.GuiButton;

import java.util.List;

/** Adds the replay manager entry to the vanilla title screen. */
public final class MenuHook {
    public static final int BUTTON_ID = 0x5F5F;

    private MenuHook() {
    }

    public static void addButton(List<GuiButton> buttons) {
        try {
            buttons.add(new GuiButton(BUTTON_ID, 6, 70, 112, 20, "SubFrame Replays"));
        } catch (Throwable t) {
            SubFrameMod.problem("menuInit", t);
        }
    }

    public static boolean handle(GuiButton button) {
        if (button.id != BUTTON_ID) {
            return false;
        }
        try {
            ReplayManagerScreen.open();
        } catch (Throwable t) {
            SubFrameMod.problem("menuAction", t);
        }
        return true;
    }
}

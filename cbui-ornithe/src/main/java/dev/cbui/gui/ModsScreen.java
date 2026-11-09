package dev.cbui.gui;

import net.minecraft.client.gui.GuiScreen;

/** Compatibility entry point: the title-screen MODS button opens the unified GUI on its Mods filter. */
public final class ModsScreen extends ModuleMenu {
    public ModsScreen() {
        this(null);
    }

    public ModsScreen(GuiScreen parent) {
        super(parent, true);
    }
}

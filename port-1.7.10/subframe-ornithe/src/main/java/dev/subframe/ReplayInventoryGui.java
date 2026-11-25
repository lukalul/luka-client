package dev.subframe;

import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.entity.player.EntityPlayer;

/**
 * The recorded player's own inventory, drawn over the replay picture (never opened as a screen). Its layout is set
 * here instead of in GuiInventory.initGui, which in creative mode opens the creative inventory screen and which
 * makes this container the player's open one.
 */
final class ReplayInventoryGui extends GuiInventory {
    final EntityPlayer player;

    ReplayInventoryGui(EntityPlayer player) {
        super(player);
        this.player = player;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        guiLeft = (width - xSize) / 2;
        guiTop = (height - ySize) / 2;
    }

    @Override
    public void updateScreen() {
    }

    /** Where the player model is drawn: a mouse there makes it look straight ahead. */
    int modelX() {
        return guiLeft + 51;
    }

    int modelEyeY() {
        return guiTop + 75 - 50;
    }
}

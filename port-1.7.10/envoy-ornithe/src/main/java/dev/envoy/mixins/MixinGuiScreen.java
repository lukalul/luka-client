package dev.envoy.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.envoy.module.inventory.InventoryInteraction;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;

/**
 * Inventory (interaction) module: hides the tooltip of items tagged {@code lunar.hideItemTooltip}.
 */
@Mixin(GuiScreen.class)
public class MixinGuiScreen {

    @Inject(method = "renderToolTip", at = @At("HEAD"), cancellable = true, require = 0)
    private void envoy$filterTooltip(ItemStack hoveredItem, int mouseX, int mouseY, CallbackInfo callback) {
        boolean hidden = InventoryInteraction.hideTooltip(hoveredItem);
        if (hidden) callback.cancel();
    }
}

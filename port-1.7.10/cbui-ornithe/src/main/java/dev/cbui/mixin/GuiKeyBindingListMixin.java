package dev.cbui.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiControls;
import net.minecraft.client.gui.GuiKeyBindingList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla sizes the controls list as keys + registered categories. When a mod registers a category that no key ends up
 * using, the tail of the list is null and drawing the screen crashes ("getListEntry(..) is null"). Dropping the empty
 * slots after the list is built fixes that.
 */
@Mixin(GuiKeyBindingList.class)
public abstract class GuiKeyBindingListMixin {
  @Shadow @Final @Mutable private net.minecraft.client.gui.GuiListExtended$IGuiListEntry[] listEntries;

  @Inject(method = "<init>", at = @At("RETURN"), require = 0)
  private void cbui$dropEmptyEntries(GuiControls controls, Minecraft mc, CallbackInfo ci) {
    int kept = 0;
    for (net.minecraft.client.gui.GuiListExtended$IGuiListEntry entry : listEntries) if (entry != null) kept++;
    if (kept == listEntries.length) return;
    net.minecraft.client.gui.GuiListExtended$IGuiListEntry[] compact = new net.minecraft.client.gui.GuiListExtended$IGuiListEntry[kept];
    int i = 0;
    for (net.minecraft.client.gui.GuiListExtended$IGuiListEntry entry : listEntries) if (entry != null) compact[i++] = entry;
    listEntries = compact;
  }
}

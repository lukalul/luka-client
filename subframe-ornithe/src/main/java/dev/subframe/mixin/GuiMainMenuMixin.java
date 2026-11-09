package dev.subframe.mixin;

import dev.subframe.MenuHook;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiMainMenu.class)
abstract class GuiMainMenuMixin {
    @Inject(method = "initGui", at = @At("RETURN"))
    private void subframe$addButton(CallbackInfo ci) {
        MenuHook.addButton(((GuiScreenAccessor) this).subframe$getButtons());
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true)
    private void subframe$action(GuiButton button, CallbackInfo ci) {
        if (MenuHook.handle(button)) {
            ci.cancel();
        }
    }
}

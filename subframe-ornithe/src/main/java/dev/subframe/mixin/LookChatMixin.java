package dev.subframe.mixin;

import dev.subframe.SubFrameMod;
import net.minecraft.client.gui.GuiNewChat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replay editor "hide chat". Priority 500: before luka's chat features. */
@Mixin(value = GuiNewChat.class, priority = 500)
abstract class LookChatMixin {
    @Inject(method = "drawChat", at = @At("HEAD"), cancellable = true, require = 0)
    private void subframe$hideChat(CallbackInfo ci) {
        if (SubFrameMod.lookHides("chat")) {
            ci.cancel();
        }
    }
}

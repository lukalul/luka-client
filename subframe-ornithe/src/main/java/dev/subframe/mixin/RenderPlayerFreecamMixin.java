package dev.subframe.mixin;

import dev.subframe.Freecam;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.RenderPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The game only draws the local player when it is the camera. In the free camera the recorded player has to be seen from outside. */
@Mixin(RenderPlayer.class)
abstract class RenderPlayerFreecamMixin {
    @Redirect(
            method = "doRender(Lnet/minecraft/client/entity/AbstractClientPlayer;DDDFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/AbstractClientPlayer;isUser()Z"),
            require = 0)
    private boolean subframe$drawRecordedPlayer(AbstractClientPlayer player) {
        return player.isUser() && !Freecam.detached();
    }
}

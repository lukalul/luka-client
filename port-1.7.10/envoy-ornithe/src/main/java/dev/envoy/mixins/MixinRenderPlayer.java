package dev.envoy.mixins;

import dev.envoy.EnvoySettings;
import dev.envoy.module.limb.LimbState;
import dev.envoy.module.model.ModelRegistry;
import dev.envoy.render.GlowRenderer;
import dev.envoy.render.JemItemRenderer;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Player armor passes (1.8's LayerArmorBase): hides armor for the Limb module and swaps in JEM armor models.
 * Pass 0 is the helmet and pass 3 the boots, so a pass maps to equipment slot {@code 4 - pass}.
 */
@Mixin(RenderPlayer.class)
public abstract class MixinRenderPlayer {
    @Shadow private ModelBiped modelBipedMain;

    @Inject(method = "shouldRenderPass(Lnet/minecraft/client/entity/AbstractClientPlayer;IF)I", at = @At("HEAD"),
        cancellable = true, require = 0)
    private void envoy$filterArmorPass(AbstractClientPlayer player, int pass, float partialTicks, CallbackInfoReturnable<Integer> result) {
        if (GlowRenderer.isRenderingOutline) {
            result.setReturnValue(-1);
            return;
        }
        if (pass < 0 || pass > 3) return;
        int armorSlot = 4 - pass;
        if (EnvoySettings.limbs) {
            int limbSlot = 5 - armorSlot;
            if ((LimbState.armorMaskOrZero(player.getUniqueID()) & LimbState.bit(limbSlot)) != 0) {
                result.setReturnValue(-1);
                return;
            }
        }
        if (EnvoySettings.customModels && ModelRegistry.hasItems()) {
            ItemStack equipped = player.getEquipmentInSlot(armorSlot);
            if (equipped != null && JemItemRenderer.renderArmor(player, equipped, armorSlot, this.modelBipedMain)) {
                result.setReturnValue(-1);
            }
        }
    }

    /** The outline pass draws only the silhouette: no held items, capes or ears (1.8 cancelled renderLayers). */
    @Inject(method = "renderEquippedItems(Lnet/minecraft/client/entity/AbstractClientPlayer;F)V", at = @At("HEAD"),
        cancellable = true, require = 0)
    private void envoy$omitOutlineExtras(AbstractClientPlayer player, float partialTicks, CallbackInfo callback) {
        if (GlowRenderer.isRenderingOutline) callback.cancel();
    }
}

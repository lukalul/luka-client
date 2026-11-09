package dev.envoy.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.envoy.module.limb.LimbState;
import dev.envoy.EnvoySettings;
import dev.envoy.module.model.ModelRegistry;
import dev.envoy.render.JemItemRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.client.renderer.entity.layers.LayerArmorBase;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

@Mixin(LayerArmorBase.class)
public class MixinLayerArmor {

    @Inject(method = "renderLayer", at = @At("HEAD"), cancellable = true, require = 0)
    private void envoy$suppressHiddenArmor(EntityLivingBase entity, float limbSwing, float limbSwingAmount,
                                   float partialTicks, float ageInTicks, float netHeadYaw, float headPitch,
                                   float scale, int armorSlot, CallbackInfo ci) {
        if (!EnvoySettings.limbs || !(entity instanceof EntityPlayer)) return;
        int limbSlot = 5 - armorSlot;
        boolean hidden = (LimbState.armorMaskOrZero(entity.getUniqueID()) & LimbState.bit(limbSlot)) != 0;
        if (hidden) ci.cancel();
    }

    @Inject(method = "renderLayer", at = @At("HEAD"), cancellable = true, require = 0)
    private void envoy$substituteArmorModel(EntityLivingBase entity, float limbSwing, float limbSwingAmount,
                                          float partialTicks, float ageInTicks, float netHeadYaw,
                                          float headPitch, float scale, int armorSlot, CallbackInfo ci) {
        if (!EnvoySettings.customModels || !ModelRegistry.hasItems()) return;
        ItemStack equipped = entity.getEquipmentInSlot(armorSlot);
        boolean replaced = equipped != null && JemItemRenderer.renderArmor(entity, equipped, armorSlot);
        if (replaced) ci.cancel();
    }
}

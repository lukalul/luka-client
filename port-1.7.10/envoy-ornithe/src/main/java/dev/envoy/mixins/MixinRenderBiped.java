package dev.envoy.mixins;

import dev.envoy.EnvoySettings;
import dev.envoy.module.model.ModelRegistry;
import dev.envoy.render.JemItemRenderer;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.entity.EntityLiving;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** JEM armor models on biped mobs, the mob half of 1.8's LayerArmorBase. */
@Mixin(RenderBiped.class)
public abstract class MixinRenderBiped {
    @Shadow protected ModelBiped modelBipedMain;

    @Inject(method = "shouldRenderPass(Lnet/minecraft/entity/EntityLiving;IF)I", at = @At("HEAD"), cancellable = true, require = 0)
    private void envoy$substituteArmorModel(EntityLiving entity, int pass, float partialTicks, CallbackInfoReturnable<Integer> result) {
        if (!EnvoySettings.customModels || !ModelRegistry.hasItems() || pass < 0 || pass > 3) return;
        int armorSlot = 4 - pass;
        ItemStack equipped = entity.getEquipmentInSlot(armorSlot);
        if (equipped != null && JemItemRenderer.renderArmor(entity, equipped, armorSlot, this.modelBipedMain)) {
            result.setReturnValue(-1);
        }
    }
}

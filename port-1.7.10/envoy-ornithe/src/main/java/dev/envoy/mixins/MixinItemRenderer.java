package dev.envoy.mixins;

import dev.envoy.render.GlintRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Held items: the coloured glint. In 1.8 it went through {@code RenderItem.renderItem}. */
@Mixin(ItemRenderer.class)
public abstract class MixinItemRenderer {
    private static final String RENDER_ITEM = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;I)V";

    private ItemStack envoy$glintStack;

    @Inject(method = RENDER_ITEM, at = @At("HEAD"), require = 0)
    private void envoy$noteHeldStack(EntityLivingBase holder, ItemStack stack, int pass, CallbackInfo callback) {
        this.envoy$glintStack = pass == 0 && GlintRenderer.wants(stack) ? stack : null;
    }

    /** The first renderItemIn2D draws the item; vanilla's own glint passes are the later two. */
    @Redirect(method = RENDER_ITEM, require = 0,
        at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/client/renderer/ItemRenderer;renderItemIn2D(Lnet/minecraft/client/renderer/Tessellator;FFFFIIF)V"))
    private void envoy$drawItemThenGlint(Tessellator tessellator, float maxU, float minV, float minU, float maxV,
                                          int width, int height, float thickness) {
        ItemRenderer.renderItemIn2D(tessellator, maxU, minV, minU, maxV, width, height, thickness);
        ItemStack stack = this.envoy$glintStack;
        if (stack != null) {
            GlintRenderer.meshGlint(tessellator, Minecraft.getMinecraft().getTextureManager(), stack, thickness);
        }
    }
}

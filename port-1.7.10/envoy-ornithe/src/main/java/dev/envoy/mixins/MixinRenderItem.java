package dev.envoy.mixins;

import dev.envoy.render.GlintRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** GUI and dropped items: the coloured glint. */
@Mixin(RenderItem.class)
public abstract class MixinRenderItem {
    @Shadow public float zLevel;

    private ItemStack envoy$droppedGlintStack;

    @Inject(method = "renderItemAndEffectIntoGUI(Lnet/minecraft/client/gui/FontRenderer;Lnet/minecraft/client/renderer/texture/TextureManager;Lnet/minecraft/item/ItemStack;II)V",
        at = @At("TAIL"), require = 0)
    private void envoy$overlayGuiGlint(FontRenderer font, TextureManager textures, ItemStack stack, int x, int y, CallbackInfo callback) {
        if (GlintRenderer.wants(stack)) GlintRenderer.guiGlint(textures, stack, x - 2, y - 2, 20, 20, this.zLevel + 50.0F);
    }

    @Inject(method = "renderDroppedItem", at = @At("HEAD"), require = 0)
    private void envoy$noteDroppedStack(EntityItem item, IIcon icon, int count, float partialTicks, float red, float green, float blue, CallbackInfo callback) {
        ItemStack stack = item.getEntityItem();
        this.envoy$droppedGlintStack = GlintRenderer.wants(stack) ? stack : null;
    }

    @Redirect(method = "renderDroppedItem", require = 0,
        at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/client/renderer/ItemRenderer;renderItemIn2D(Lnet/minecraft/client/renderer/Tessellator;FFFFIIF)V"))
    private void envoy$drawDroppedThenGlint(Tessellator tessellator, float maxU, float minV, float minU, float maxV,
                                             int width, int height, float thickness) {
        ItemRenderer.renderItemIn2D(tessellator, maxU, minV, minU, maxV, width, height, thickness);
        ItemStack stack = this.envoy$droppedGlintStack;
        if (stack != null) {
            GlintRenderer.meshGlint(tessellator, Minecraft.getMinecraft().getTextureManager(), stack, thickness);
        }
    }
}

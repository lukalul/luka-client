package dev.envoy.mixins;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.gen.Invoker;

import dev.envoy.module.glint.GlintState;
import dev.envoy.module.model.ModelRegistry;
import dev.envoy.render.JemItemRenderer;
import dev.envoy.module.glint.GlintTexture;
import dev.envoy.EnvoySettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.item.ItemStack;

@Mixin(RenderItem.class)
public abstract class MixinRenderItem {

    @Invoker("renderModel")
    protected abstract void envoy$renderModel(IBakedModel model, int color);

    @Inject(
        method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/resources/model/IBakedModel;)V",
        at = @At("HEAD"), cancellable = true, require = 0)
    private void envoy$replaceHeldItem(ItemStack stack, IBakedModel model, CallbackInfo callback) {
        boolean eligible = EnvoySettings.customModels && stack != null && ModelRegistry.hasItems();
        if (eligible && JemItemRenderer.renderHeld(stack)) callback.cancel();
    }

    @Inject(
        method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/resources/model/IBakedModel;)V",
        at = @At("RETURN"), require = 0)
    private void envoy$overlayColoredGlint(ItemStack stack, IBakedModel model, CallbackInfo callback) {
        if (!shouldOverlay(stack, model)) return;

        GlStateManager.pushMatrix();
        GlStateManager.scale(0.5F, 0.5F, 0.5F);
        GlStateManager.translate(-0.5F, -0.5F, -0.5F);
        drawGlintPasses(model, GlintState.glintArgb(stack));
        GlStateManager.popMatrix();
    }

    private static boolean shouldOverlay(ItemStack stack, IBakedModel model) {
        return model != null && !model.isBuiltInRenderer() && GlintState.hasGlint(stack)
            && ModelRegistry.findForItem(stack) == null;
    }

    private void drawGlintPasses(IBakedModel model, int argb) {
        GlStateManager.depthMask(false);
        GlStateManager.depthFunc(514);
        GlStateManager.disableLighting();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        Minecraft.getMinecraft().getTextureManager().bindTexture(GlintTexture.get());
        GlStateManager.matrixMode(GL11.GL_TEXTURE);

        drawMovingLayer(model, argb, 3000L, 1.0F, -50.0F);
        drawMovingLayer(model, argb, 4873L, -1.0F, 10.0F);

        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.enableLighting();
        GlStateManager.depthFunc(515);
        GlStateManager.depthMask(true);
        Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
    }

    private void drawMovingLayer(IBakedModel model, int color, long period, float direction, float angle) {
        GlStateManager.pushMatrix();
        GlStateManager.scale(8.0F, 8.0F, 8.0F);
        float phase = (float) (Minecraft.getSystemTime() % period) / (float) period / 8.0F;
        GlStateManager.translate(direction * phase, 0.0F, 0.0F);
        GlStateManager.rotate(angle, 0.0F, 0.0F, 1.0F);
        this.envoy$renderModel(model, color);
        GlStateManager.popMatrix();
    }
}

package dev.bakeplus.mixin;

import dev.bakeplus.BakePlus;
import dev.bakeplus.BakePlusConfig;
import net.minecraft.client.Minecraft;
import dev.luka.compat.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.RenderEndPortal;
import net.minecraft.tileentity.TileEntityEndPortal;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderEndPortal.class)
public abstract class TileEntityEndPortalRendererMixin {
    private static final ResourceLocation BAKEPLUS_END_SKY =
        new ResourceLocation("textures/environment/end_sky.png");
    private static final ResourceLocation BAKEPLUS_END_PORTAL =
        new ResourceLocation("textures/entity/end_portal.png");

    @Inject(method = "renderTileEntityAt(Lnet/minecraft/tileentity/TileEntityEndPortal;DDDF)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void bakeplus$staticPortal(TileEntityEndPortal tile, double x, double y, double z,
                                       float partialTicks, CallbackInfo ci) {
        if (!BakePlusConfig.staticEndPortals) return;
        try {
            bakeplus$drawPortal(x, y, z);
            ci.cancel();
        } catch (Throwable failure) {
            BakePlus.report("end portal", failure);
        }
    }

    private static void bakeplus$drawPortal(double x, double y, double z) {
        GlStateManager.pushMatrix();
        try {
            GlStateManager.disableLighting();
            GlStateManager.disableCull();
            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(770, 771);
            GlStateManager.depthMask(true);

            Minecraft.getMinecraft().getTextureManager().bindTexture(BAKEPLUS_END_SKY);
            bakeplus$quad(x, y + 0.75D, z, 0.055F, 0.075F, 0.09F, 1.0F);

            GlStateManager.depthMask(false);
            GlStateManager.blendFunc(770, 1);
            Minecraft.getMinecraft().getTextureManager().bindTexture(BAKEPLUS_END_PORTAL);
            bakeplus$quad(x, y + 0.751D, z, 0.08F, 0.28F, 0.32F, 0.72F);
        } finally {
            GlStateManager.depthMask(true);
            GlStateManager.blendFunc(770, 771);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.enableCull();
            GlStateManager.enableLighting();
            GlStateManager.popMatrix();
        }
    }

    private static void bakeplus$quad(double x, double y, double z,
                                      float red, float green, float blue, float alpha) {
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.setColorRGBA_F(red, green, blue, alpha);
        tessellator.addVertexWithUV(x, y, z, 0.0D, 0.0D);
        tessellator.addVertexWithUV(x, y, z + 1.0D, 0.0D, 1.0D);
        tessellator.addVertexWithUV(x + 1.0D, y, z + 1.0D, 1.0D, 1.0D);
        tessellator.addVertexWithUV(x + 1.0D, y, z, 1.0D, 0.0D);
        tessellator.draw();
    }
}

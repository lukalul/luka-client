package dev.bakeplus.mixin;

import dev.bakeplus.BakePlus;
import dev.bakeplus.BakePlusConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.tileentity.TileEntityEndPortal;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.minecraft.unmapped.C_86127799", remap = false)
public abstract class TileEntityEndPortalRendererMixin {
    private static final ResourceLocation BAKEPLUS_END_SKY =
        new ResourceLocation("textures/environment/end_sky.png");
    private static final ResourceLocation BAKEPLUS_END_PORTAL =
        new ResourceLocation("textures/entity/end_portal.png");

    @Inject(method = "m_88730533", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void bakeplus$staticPortal(TileEntityEndPortal tile, double x, double y, double z,
                                       float partialTicks, int destroyStage, CallbackInfo ci) {
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
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer buffer = tessellator.getWorldRenderer();
        buffer.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
        buffer.pos(x, y, z).tex(0.0D, 0.0D).color(red, green, blue, alpha).endVertex();
        buffer.pos(x, y, z + 1.0D).tex(0.0D, 1.0D).color(red, green, blue, alpha).endVertex();
        buffer.pos(x + 1.0D, y, z + 1.0D).tex(1.0D, 1.0D).color(red, green, blue, alpha).endVertex();
        buffer.pos(x + 1.0D, y, z).tex(1.0D, 0.0D).color(red, green, blue, alpha).endVertex();
        tessellator.draw();
    }
}

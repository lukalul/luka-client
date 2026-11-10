package dev.bakeplus.mixin;

import dev.bakeplus.BakePlus;
import dev.bakeplus.BakePlusConfig;
import net.minecraft.client.Minecraft;
import dev.luka.compat.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntityBeaconRenderer;
import net.minecraft.tileentity.TileEntityBeacon;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TileEntityBeaconRenderer.class)
public abstract class TileEntityBeaconRendererMixin {
    private static final ResourceLocation BAKEPLUS_BEAM =
        new ResourceLocation("textures/entity/beacon_beam.png");

    @Inject(method = "renderTileEntityAt(Lnet/minecraft/tileentity/TileEntityBeacon;DDDF)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void bakeplus$staticBeam(TileEntityBeacon beacon, double x, double y, double z,
                                     float partialTicks, CallbackInfo ci) {
        if (!BakePlusConfig.staticBeacons) return;
        if (x * x + y * y + z * z > 48.0D * 48.0D) {
            ci.cancel();
            return;
        }
        try {
            if (beacon.shouldBeamRender() > 0.0F) bakeplus$drawBeam(x, y, z);
            ci.cancel();
        } catch (Throwable failure) {
            BakePlus.report("beacon", failure);
        }
    }

    /** 1.7.10 beacons have one uncoloured beam (coloured glass segments arrived in 1.8). */
    private static void bakeplus$drawBeam(double x, double y, double z) {
        GlStateManager.pushMatrix();
        try {
            GlStateManager.disableLighting();
            GlStateManager.disableCull();
            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(770, 1);
            GlStateManager.depthMask(false);
            Minecraft.getMinecraft().getTextureManager().bindTexture(BAKEPLUS_BEAM);
            bakeplus$crossedSegment(x, y, z, 256, 1.0F, 1.0F, 1.0F);
        } finally {
            GlStateManager.depthMask(true);
            GlStateManager.blendFunc(770, 771);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.enableCull();
            GlStateManager.enableLighting();
            GlStateManager.popMatrix();
        }
    }

    private static void bakeplus$crossedSegment(double x, double y, double z, int height,
                                                 float red, float green, float blue) {
        double min = 0.30D;
        double max = 0.70D;
        double top = y + height;
        double v = height * 0.5D;
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.setColorRGBA_F(red, green, blue, 0.72F);
        bakeplus$plane(tessellator, x + min, z + 0.5D, x + max, z + 0.5D, y, top, v);
        bakeplus$plane(tessellator, x + 0.5D, z + min, x + 0.5D, z + max, y, top, v);
        tessellator.draw();
    }

    private static void bakeplus$plane(Tessellator tessellator, double x1, double z1, double x2, double z2,
                                       double bottom, double top, double v) {
        tessellator.addVertexWithUV(x1, bottom, z1, 0.0D, 0.0D);
        tessellator.addVertexWithUV(x1, top, z1, 0.0D, v);
        tessellator.addVertexWithUV(x2, top, z2, 1.0D, v);
        tessellator.addVertexWithUV(x2, bottom, z2, 1.0D, 0.0D);
    }
}

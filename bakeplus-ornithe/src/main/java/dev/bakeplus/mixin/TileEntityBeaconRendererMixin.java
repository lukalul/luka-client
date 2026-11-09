package dev.bakeplus.mixin;

import dev.bakeplus.BakePlus;
import dev.bakeplus.BakePlusConfig;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.tileentity.TileEntityBeacon;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.minecraft.unmapped.C_42182049", remap = false)
public abstract class TileEntityBeaconRendererMixin {
    private static final ResourceLocation BAKEPLUS_BEAM =
        new ResourceLocation("textures/entity/beacon_beam.png");

    @Inject(method = "m_03541616", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void bakeplus$staticBeam(TileEntityBeacon beacon, double x, double y, double z,
                                     float partialTicks, int destroyStage, CallbackInfo ci) {
        if (!BakePlusConfig.staticBeacons) return;
        if (x * x + y * y + z * z > 48.0D * 48.0D) {
            ci.cancel();
            return;
        }
        try {
            if (beacon.shouldBeamRender() > 0.0F) bakeplus$drawBeam(beacon, x, y, z);
            ci.cancel();
        } catch (Throwable failure) {
            BakePlus.report("beacon", failure);
        }
    }

    private static void bakeplus$drawBeam(TileEntityBeacon beacon, double x, double y, double z) {
        List<TileEntityBeacon.BeamSegment> segments = beacon.getBeamSegments();
        if (segments == null || segments.isEmpty()) return;

        GlStateManager.pushMatrix();
        try {
            GlStateManager.disableLighting();
            GlStateManager.disableCull();
            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(770, 1);
            GlStateManager.depthMask(false);
            Minecraft.getMinecraft().getTextureManager().bindTexture(BAKEPLUS_BEAM);

            int bottom = 0;
            for (int index = 0; index < segments.size(); index++) {
                TileEntityBeacon.BeamSegment segment = segments.get(index);
                int height = index == segments.size() - 1 ? 256 : segment.getHeight();
                float[] color = segment.getColors();
                if (color != null && color.length >= 3 && height > 0) {
                    bakeplus$crossedSegment(x, y + bottom, z, height,
                        color[0], color[1], color[2]);
                }
                bottom += segment.getHeight();
            }
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
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer buffer = tessellator.getWorldRenderer();
        buffer.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
        bakeplus$plane(buffer, x + min, z + 0.5D, x + max, z + 0.5D, y, top, v, red, green, blue);
        bakeplus$plane(buffer, x + 0.5D, z + min, x + 0.5D, z + max, y, top, v, red, green, blue);
        tessellator.draw();
    }

    private static void bakeplus$plane(WorldRenderer buffer, double x1, double z1, double x2, double z2,
                                       double bottom, double top, double v,
                                       float red, float green, float blue) {
        buffer.pos(x1, bottom, z1).tex(0.0D, 0.0D).color(red, green, blue, 0.72F).endVertex();
        buffer.pos(x1, top, z1).tex(0.0D, v).color(red, green, blue, 0.72F).endVertex();
        buffer.pos(x2, top, z2).tex(1.0D, v).color(red, green, blue, 0.72F).endVertex();
        buffer.pos(x2, bottom, z2).tex(1.0D, 0.0D).color(red, green, blue, 0.72F).endVertex();
    }
}

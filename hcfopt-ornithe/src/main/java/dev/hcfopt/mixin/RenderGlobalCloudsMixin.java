package dev.hcfopt.mixin;

import dev.hcfopt.CloudMeshCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderGlobal.class)
abstract class RenderGlobalCloudsMixin {
    @Shadow @Final private static ResourceLocation locationCloudsPng;
    @Shadow @Final private TextureManager renderEngine;
    @Shadow @Final private Minecraft mc;
    @Shadow private WorldClient theWorld;
    @Shadow private int cloudTickCounter;
    @Shadow private boolean vboEnabled;

    /**
     * Draws fancy clouds from the cached mesh instead of tessellating all 64 tiles twice per frame, with the same
     * maths and the same GL state as the game. Everything that can fail runs before the first GL state change, so
     * falling through to the game's clouds is safe.
     */
    @Inject(method = "renderCloudsFancy", at = @At("HEAD"), cancellable = true, require = 0)
    private void hcfopt$cachedFancyClouds(float partialTicks, int pass, CallbackInfo ci) {
        if (!CloudMeshCache.active()) return;

        float height;
        float fracX;
        float fracZ;
        try {
            Entity viewer = mc.getRenderViewEntity();
            float cameraY = (float) (viewer.lastTickPosY + (viewer.posY - viewer.lastTickPosY) * (double) partialTicks);
            double drift = (double) ((float) cloudTickCounter + partialTicks);
            double cloudX = (viewer.prevPosX + (viewer.posX - viewer.prevPosX) * (double) partialTicks
                    + drift * 0.029999999329447746D) / 12.0D;
            double cloudZ = (viewer.prevPosZ + (viewer.posZ - viewer.prevPosZ) * (double) partialTicks) / 12.0D
                    + 0.33000001311302185D;
            height = theWorld.provider.getCloudHeight() - cameraY + 0.33F;
            int wrapX = MathHelper.floor_double(cloudX / 2048.0D);
            int wrapZ = MathHelper.floor_double(cloudZ / 2048.0D);
            cloudX -= (double) (wrapX * 2048);
            cloudZ -= (double) (wrapZ * 2048);

            Vec3 colour = theWorld.getCloudColour(partialTicks);
            float red = (float) colour.xCoord;
            float green = (float) colour.yCoord;
            float blue = (float) colour.zCoord;
            if (pass != 2) {
                float mixedRed = (red * 30.0F + green * 59.0F + blue * 11.0F) / 100.0F;
                float mixedGreen = (red * 30.0F + green * 70.0F) / 100.0F;
                float mixedBlue = (red * 30.0F + blue * 70.0F) / 100.0F;
                red = mixedRed;
                green = mixedGreen;
                blue = mixedBlue;
            }

            int cellX = MathHelper.floor_double(cloudX);
            int cellZ = MathHelper.floor_double(cloudZ);
            fracX = (float) (cloudX - (double) cellX);
            fracZ = (float) (cloudZ - (double) cellZ);
            if (!CloudMeshCache.prepare(cellX, cellZ, red, green, blue, height > -5.0F, height <= 5.0F, vboEnabled)) return;
        } catch (Throwable t) {
            CloudMeshCache.fail(t);
            return;
        }

        GlStateManager.disableCull();
        renderEngine.bindTexture(locationCloudsPng);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.scale(12.0F, 1.0F, 12.0F);
        GlStateManager.pushMatrix();
        GlStateManager.translate(-fracX, height, -fracZ);
        for (int i = 0; i < 2; i++) {
            if (i == 0) {
                GlStateManager.colorMask(false, false, false, false);
            } else {
                switch (pass) {
                    case 0:
                        GlStateManager.colorMask(false, true, true, true);
                        break;
                    case 1:
                        GlStateManager.colorMask(true, false, false, true);
                        break;
                    case 2:
                        GlStateManager.colorMask(true, true, true, true);
                        break;
                    default:
                        break;
                }
            }
            CloudMeshCache.draw();
        }
        GlStateManager.popMatrix();
        // the mesh's vertex colours changed the GL colour behind GlStateManager's cache: forget the cached value first,
        // or a cache that already holds white skips the call and the hand and hotbar keep the clouds' grey
        GlStateManager.resetColor();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableBlend();
        GlStateManager.enableCull();
        ci.cancel();
    }
}

package dev.hcfopt.mixin;

import dev.hcfopt.CloudMeshCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderGlobal.class)
abstract class RenderGlobalMixin {
    @Shadow @Final private static ResourceLocation locationCloudsPng;
    @Shadow @Final private TextureManager renderEngine;
    @Shadow private Minecraft mc;
    @Shadow private WorldClient theWorld;
    @Shadow private int cloudTickCounter;

    /**
     * Draws fancy clouds from a cached display list instead of tessellating all 64 cells twice per frame.
     * Everything that can fail runs before the first GL state change, so falling through to vanilla is safe.
     */
    @Inject(method = "renderCloudsFancy", at = @At("HEAD"), cancellable = true, require = 0)
    private void hcfopt$cachedFancyClouds(float partialTicks, CallbackInfo ci) {
        if (!CloudMeshCache.active()) return;

        int list;
        float height;
        float fracX;
        float fracZ;
        boolean anaglyph;
        try {
            EntityLivingBase viewer = mc.renderViewEntity;
            float cameraY = (float) (viewer.lastTickPosY + (viewer.posY - viewer.lastTickPosY) * (double) partialTicks);
            float scale = 12.0F;
            double drift = (double) ((float) cloudTickCounter + partialTicks);
            double cloudX = (viewer.prevPosX + (viewer.posX - viewer.prevPosX) * (double) partialTicks
                    + drift * (double) 0.03F) / (double) scale;
            double cloudZ = (viewer.prevPosZ + (viewer.posZ - viewer.prevPosZ) * (double) partialTicks) / (double) scale
                    + (double) 0.33F;
            height = theWorld.provider.getCloudHeight() - cameraY + 0.33F;
            int wrapX = MathHelper.floor_double(cloudX / 2048.0D);
            int wrapZ = MathHelper.floor_double(cloudZ / 2048.0D);
            cloudX -= (double) (wrapX * 2048);
            cloudZ -= (double) (wrapZ * 2048);

            Vec3 colour = theWorld.getCloudColour(partialTicks);
            float red = (float) colour.xCoord;
            float green = (float) colour.yCoord;
            float blue = (float) colour.zCoord;
            anaglyph = mc.gameSettings.anaglyph;
            if (anaglyph) {
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
            list = CloudMeshCache.listFor(cellX, cellZ, red, green, blue, height > -5.0F, height <= 5.0F);
        } catch (Throwable t) {
            CloudMeshCache.fail(t);
            return;
        }
        if (list == 0) return;

        GL11.glDisable(GL11.GL_CULL_FACE);
        renderEngine.bindTexture(locationCloudsPng);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        GL11.glScalef(12.0F, 1.0F, 12.0F);
        GL11.glPushMatrix();
        GL11.glTranslatef(-fracX, height, -fracZ);

        GL11.glColorMask(false, false, false, false);
        GL11.glCallList(list);
        if (anaglyph) {
            if (EntityRenderer.anaglyphField == 0) {
                GL11.glColorMask(false, true, true, true);
            } else {
                GL11.glColorMask(true, false, false, true);
            }
        } else {
            GL11.glColorMask(true, true, true, true);
        }
        GL11.glCallList(list);

        GL11.glPopMatrix();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_CULL_FACE);
        ci.cancel();
    }
}

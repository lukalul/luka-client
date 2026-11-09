package dev.envoy.mixins;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.envoy.Envoy;
import dev.envoy.module.glow.GlowState;
import dev.envoy.render.GlowRenderer;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.entity.player.EntityPlayer;

@Mixin(RendererLivingEntity.class)
public abstract class MixinRendererLivingEntity<T extends EntityLivingBase> {

    @Inject(method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void envoy$replaceLivingRender(T entity, double x, double y, double z, float yaw, float frameDelta, CallbackInfo callback) {
        boolean replaced = Envoy.onRenderLiving(entity, x, y, z);
        if (replaced) callback.cancel();
    }

    @Inject(method = "renderName(Lnet/minecraft/entity/EntityLivingBase;DDD)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void envoy$replaceLivingName(T entity, double x, double y, double z, CallbackInfo callback) {
        boolean suppress = GlowRenderer.isRenderingOutline;
        if (!suppress) suppress = Envoy.onRenderName(entity, x, y, z);
        if (suppress) callback.cancel();
    }

    @Inject(method = "renderLayers", at = @At("HEAD"), cancellable = true, require = 0)
    private void envoy$omitOutlineLayers(T entity, float limbSwing, float limbSwingAmount,
            float partialTicks, float ageInTicks, float netHeadYaw, float headPitch, float scale,
            CallbackInfo ci) {
        if (GlowRenderer.isRenderingOutline) {
            ci.cancel();
        }
    }

    @Dynamic
    @Inject(method = "lunar$setScoreTeamColor", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void envoy$setLunarOutlineTint(EntityLivingBase entity, CallbackInfoReturnable<Integer> result) {
        int rgb = outlineColor(entity);
        prepareOutlineColor(rgb, true);
        result.setReturnValue(rgb);
    }

    private static int outlineColor(EntityLivingBase entity) {
        if (GlowRenderer.isRenderingOutline && GlowState.isGlowing(entity.getUniqueID())) {
            return GlowState.color(entity.getUniqueID()) & 0xFFFFFF;
        }
        if (entity instanceof EntityPlayer && entity.getTeam() instanceof ScorePlayerTeam) {
            return decodeTeamColor(FontRenderer.getFormatFromString(((ScorePlayerTeam) entity.getTeam()).getColorPrefix()));
        }
        return 0xFFFFFF;
    }

    private static int decodeTeamColor(String format) {
        if (format.length() >= 14 && Character.toLowerCase(format.charAt(1)) == 'x') {
            int rgb = 0;
            for (int i = 0; i < 6; i++) {
                int digit = Character.digit(format.charAt(3 + i * 2), 16);
                if (digit < 0) {
                    return 0xFFFFFF;
                }
                rgb = rgb << 4 | digit;
            }
            return rgb;
        }
        if (format.length() >= 2) {
            int index = "0123456789abcdef".indexOf(Character.toLowerCase(format.charAt(1)));
            if (index >= 0) {
                return Minecraft.getMinecraft().fontRendererObj.getColorCode(format.charAt(1)) & 0xFFFFFF;
            }
        }
        return 0xFFFFFF;
    }

    @Inject(method = "setScoreTeamColor", at = @At("HEAD"), cancellable = true, require = 0)
    private void envoy$setVanillaOutlineTint(EntityLivingBase entity, CallbackInfoReturnable<Boolean> result) {
        if (!(entity instanceof EntityPlayer)) return;
        final int rgb;
        if (GlowRenderer.isRenderingOutline && GlowState.isGlowing(entity.getUniqueID())) {
            rgb = GlowState.color(entity.getUniqueID());
        } else {
            String format = entity.getTeam() instanceof ScorePlayerTeam
                ? FontRenderer.getFormatFromString(((ScorePlayerTeam) entity.getTeam()).getColorPrefix()) : "";
            if (format.length() < 2 || Character.toLowerCase(format.charAt(1)) != 'x') return;
            rgb = decodeTeamColor(format);
        }

        prepareOutlineColor(rgb, false);
        result.setReturnValue(true);
    }

    private static void prepareOutlineColor(int rgb, boolean lunarPath) {
        if (lunarPath) GlStateManager.resetColor();
        float red = (rgb >> 16 & 0xFF) / 255.0F;
        float green = (rgb >> 8 & 0xFF) / 255.0F;
        float blue = (rgb & 0xFF) / 255.0F;

        if (lunarPath) {
            GL11.glDisable(GL11.GL_LIGHTING);
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GL11.glColor4f(red, green, blue, 1.0F);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        } else {
            GlStateManager.disableLighting();
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GlStateManager.color(red, green, blue, 1.0F);
            GlStateManager.disableTexture2D();
            GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            GlStateManager.disableTexture2D();
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        }
    }
}

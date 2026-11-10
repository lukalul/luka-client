package dev.envoy.mixins;

import java.util.ArrayDeque;
import java.util.Deque;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.envoy.Envoy;
import dev.envoy.EnvoySettings;
import dev.envoy.module.glow.GlowState;
import dev.envoy.module.limb.LimbState;
import dev.envoy.render.GlowRenderer;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

@Mixin(RendererLivingEntity.class)
public abstract class MixinRendererLivingEntity {
    @Shadow protected ModelBase mainModel;

    /** Show-flags saved before a player's main model is drawn with limbs hidden, innermost last. */
    private static final Deque<boolean[]> envoy$hiddenLimbFrames = new ArrayDeque<boolean[]>();

    @Inject(method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void envoy$replaceLivingRender(EntityLivingBase entity, double x, double y, double z, float yaw, float frameDelta, CallbackInfo callback) {
        boolean replaced = Envoy.onRenderLiving(entity, x, y, z);
        if (replaced) callback.cancel();
    }

    @Inject(method = "passSpecialRender(Lnet/minecraft/entity/EntityLivingBase;DDD)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void envoy$replaceLivingName(EntityLivingBase entity, double x, double y, double z, CallbackInfo callback) {
        boolean suppress = GlowRenderer.isRenderingOutline;
        if (!suppress) suppress = Envoy.onRenderName(entity, x, y, z);
        if (suppress) callback.cancel();
    }

    /**
     * Two 1.8 hooks converge here: the Limb module (1.8's ModelPlayer.render) and the Glowing silhouette (1.8's
     * outline mode, which 1.7.10 does not have). Armor uses separate models, so hiding limbs only touches the body.
     */
    @Inject(method = "renderModel(Lnet/minecraft/entity/EntityLivingBase;FFFFFF)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void envoy$beforeMainModel(EntityLivingBase entity, float limbSwing, float limbSwingAmount, float age,
                                       float headYaw, float headPitch, float scale, CallbackInfo callback) {
        boolean[] saved = envoy$hideLimbs(entity);
        if (GlowRenderer.isRenderingOutline) {
            envoy$drawSilhouette(entity, limbSwing, limbSwingAmount, age, headYaw, headPitch, scale);
            if (saved != null) envoy$restoreLimbs(saved);
            callback.cancel();
            return;
        }
        if (saved != null) envoy$hiddenLimbFrames.push(saved);
    }

    @Inject(method = "renderModel(Lnet/minecraft/entity/EntityLivingBase;FFFFFF)V", at = @At("RETURN"), require = 0)
    private void envoy$afterMainModel(EntityLivingBase entity, float limbSwing, float limbSwingAmount, float age,
                                      float headYaw, float headPitch, float scale, CallbackInfo callback) {
        if (!envoy$hiddenLimbFrames.isEmpty() && envoy$isPlayerBody(entity)) envoy$restoreLimbs(envoy$hiddenLimbFrames.pop());
    }

    private boolean envoy$isPlayerBody(EntityLivingBase entity) {
        return EnvoySettings.limbs && entity instanceof EntityPlayer && this.mainModel instanceof ModelBiped;
    }

    private boolean[] envoy$hideLimbs(EntityLivingBase entity) {
        if (!envoy$isPlayerBody(entity)) return null;
        int mask = LimbState.maskOrZero(entity.getUniqueID());
        if (mask == 0) return null;
        ModelBiped model = (ModelBiped) this.mainModel;
        ModelRenderer[] parts = envoy$parts(model);
        boolean[] saved = new boolean[parts.length];
        for (int index = 0; index < parts.length; index++) saved[index] = parts[index].showModel;
        if ((mask & LimbState.bit(LimbState.HEAD)) != 0) { model.bipedHead.showModel = false; model.bipedHeadwear.showModel = false; }
        if ((mask & LimbState.bit(LimbState.TORSO)) != 0) model.bipedBody.showModel = false;
        if ((mask & LimbState.bit(LimbState.LEFT_ARM)) != 0) model.bipedLeftArm.showModel = false;
        if ((mask & LimbState.bit(LimbState.RIGHT_ARM)) != 0) model.bipedRightArm.showModel = false;
        if ((mask & LimbState.bit(LimbState.LEFT_LEG)) != 0) model.bipedLeftLeg.showModel = false;
        if ((mask & LimbState.bit(LimbState.RIGHT_LEG)) != 0) model.bipedRightLeg.showModel = false;
        return saved;
    }

    private void envoy$restoreLimbs(boolean[] saved) {
        ModelRenderer[] parts = envoy$parts((ModelBiped) this.mainModel);
        for (int index = 0; index < parts.length; index++) parts[index].showModel = saved[index];
    }

    private static ModelRenderer[] envoy$parts(ModelBiped model) {
        return new ModelRenderer[] { model.bipedHead, model.bipedHeadwear, model.bipedBody, model.bipedLeftArm,
            model.bipedRightArm, model.bipedLeftLeg, model.bipedRightLeg };
    }

    private void envoy$drawSilhouette(EntityLivingBase entity, float limbSwing, float limbSwingAmount, float age,
                                      float headYaw, float headPitch, float scale) {
        int rgb = GlowState.isGlowing(entity.getUniqueID()) ? GlowState.color(entity.getUniqueID()) & 0xFFFFFF : 0xFFFFFF;
        GL11.glDisable(GL11.GL_LIGHTING);
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glColor4f((rgb >> 16 & 0xFF) / 255.0F, (rgb >> 8 & 0xFF) / 255.0F, (rgb & 0xFF) / 255.0F, 1.0F);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        this.mainModel.render(entity, limbSwing, limbSwingAmount, age, headYaw, headPitch, scale);
        GL11.glEnable(GL11.GL_LIGHTING);
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }
}

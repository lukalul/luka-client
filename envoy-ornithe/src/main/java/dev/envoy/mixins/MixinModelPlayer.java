package dev.envoy.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.envoy.module.limb.LimbState;
import dev.envoy.EnvoySettings;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Hides a player's body parts (Limb module): before the model is drawn we turn off {@code showModel}
 * on the hidden parts (base + "wear" layer), then restore it afterwards.
 */
@Mixin(ModelPlayer.class)
public class MixinModelPlayer {

    private static final ThreadLocal<java.util.Deque<boolean[]>> envoy$visibilityFrames =
        new ThreadLocal<java.util.Deque<boolean[]>>() {
            @Override protected java.util.Deque<boolean[]> initialValue() {
                return new java.util.ArrayDeque<boolean[]>();
            }
        };

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void envoy$applyLimbVisibility(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                   float netHeadYaw, float headPitch, float scale, CallbackInfo ci) {
        ModelRenderer[] modelParts = collectParts();
        int hiddenMask = visibilityMask(entity);
        boolean[] previous = snapshot(modelParts);
        envoy$visibilityFrames.get().push(previous);
        if (hiddenMask == 0) return;

        int[] limbIds = { LimbState.HEAD, LimbState.TORSO, LimbState.LEFT_ARM,
            LimbState.RIGHT_ARM, LimbState.LEFT_LEG, LimbState.RIGHT_LEG };
        for (int pair = 0; pair < limbIds.length; pair++) {
            if ((hiddenMask & LimbState.bit(limbIds[pair])) == 0) continue;
            modelParts[pair * 2].showModel = false;
            modelParts[pair * 2 + 1].showModel = false;
        }
    }

    @Inject(method = "render", at = @At("RETURN"), require = 0)
    private void envoy$restoreLimbVisibility(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                      float netHeadYaw, float headPitch, float scale, CallbackInfo ci) {
        java.util.Deque<boolean[]> frames = envoy$visibilityFrames.get();
        if (frames.isEmpty()) return;
        boolean[] previous = frames.pop();
        ModelRenderer[] modelParts = collectParts();
        for (int index = 0; index < modelParts.length; index++) modelParts[index].showModel = previous[index];
        if (frames.isEmpty()) envoy$visibilityFrames.remove();
    }

    private static int visibilityMask(Entity entity) {
        if (!EnvoySettings.limbs || !(entity instanceof EntityPlayer)) return 0;
        return LimbState.maskOrZero(entity.getUniqueID());
    }

    private static boolean[] snapshot(ModelRenderer[] parts) {
        boolean[] states = new boolean[parts.length];
        for (int index = 0; index < parts.length; index++) states[index] = parts[index].showModel;
        return states;
    }

    private ModelRenderer[] collectParts() {
        ModelPlayer model = (ModelPlayer) (Object) this;
        return new ModelRenderer[] {
            model.bipedHead, model.bipedHeadwear,
            model.bipedBody, model.bipedBodyWear,
            model.bipedLeftArm, model.bipedLeftArmwear,
            model.bipedRightArm, model.bipedRightArmwear,
            model.bipedLeftLeg, model.bipedLeftLegwear,
            model.bipedRightLeg, model.bipedRightLegwear
        };
    }
}

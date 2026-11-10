package dev.motionblur.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.motionblur.MotionBlurSettings;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import net.minecraft.client.Minecraft;
import dev.luka.compat.GlStateManager;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Block-hitting and item use (1.7.10): when you start or stop blocking the sword normally teleports between two poses, and a
 * right click yanks the hand down in one frame. This moves between the poses over a short time and lets the hand dip over a
 * tick. Adapted from framejob by Z1xus (MIT licence, see META-INF/LICENSE.framejob), ported to the MCP names used here.
 */
@Mixin(ItemRenderer.class)
public abstract class SmoothItemUseMixin {
    @Unique
    private static final float BLOCK_DECAY = 0.05F;
    @Unique
    private static final int MODELVIEW_MATRIX = 0x0BA6;
    @Unique
    private static final FloatBuffer BUFFER = ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder()).asFloatBuffer();

    @Shadow
    private Minecraft mc;
    @Shadow
    private ItemStack itemToRender;
    @Shadow
    private float equippedProgress;

    @Unique
    private final float[] framejob$pose = new float[16];
    @Unique
    private final float[] framejob$shown = new float[16];
    @Unique
    private final float[] framejob$offset = new float[16];
    @Unique
    private final float[] framejob$scaleOffset = new float[3];
    @Unique
    private int framejob$ticks;
    @Unique
    private float framejob$switchTime;
    @Unique
    private boolean framejob$wasBlocking;
    @Unique
    private boolean framejob$hasShown;
    @Unique
    private float framejob$heldHeight;
    @Unique
    private boolean framejob$dropped;

    @Inject(method = {"resetEquippedProgress", "resetEquippedProgress2"}, at = @At("HEAD"), require = 0)
    private void framejob$holdHeight(CallbackInfo ci) {
        this.framejob$heldHeight = this.equippedProgress;
    }

    @Inject(method = {"resetEquippedProgress", "resetEquippedProgress2"}, at = @At("TAIL"), require = 0)
    private void framejob$deferDrop(CallbackInfo ci) {
        if (!MotionBlurSettings.smoothItemUse) return;
        this.equippedProgress = this.framejob$heldHeight;
        this.framejob$dropped = true;
    }

    @Inject(method = "updateEquippedItem", at = @At("RETURN"), require = 0)
    private void framejob$advance(CallbackInfo ci) {
        this.framejob$ticks++;
        if (this.framejob$dropped) {
            this.equippedProgress = 0;
            this.framejob$dropped = false;
        }
        if (!this.framejob$canBlock()) this.framejob$hasShown = false;
    }

    @WrapOperation(
        method = "renderItemInFirstPerson",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemRenderer;renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;I)V"
        ),
        require = 0
    )
    private void framejob$easeBlocking(
        ItemRenderer renderer,
        EntityLivingBase entity,
        ItemStack stack,
        int renderPass,
        Operation<Void> original,
        @Local(argsOnly = true) float tickDelta
    ) {
        if (MotionBlurSettings.smoothItemUse && renderPass == 0 && this.framejob$canBlock()) {
            boolean blocking = this.framejob$isBlocking();
            float time = this.framejob$ticks + tickDelta;
            GlStateManager.getFloat(MODELVIEW_MATRIX, BUFFER);
            BUFFER.get(this.framejob$pose).rewind();
            if (this.framejob$hasShown && blocking != this.framejob$wasBlocking) {
                for (int i = 0; i < 16; i++) this.framejob$offset[i] = this.framejob$shown[i] - this.framejob$pose[i];
                for (int i = 0; i < 3; i++) this.framejob$scaleOffset[i] = framejob$length(this.framejob$shown, i * 4) - framejob$length(this.framejob$pose, i * 4);
                this.framejob$switchTime = time;
            }
            float weight = this.framejob$hasShown ? (float) Math.pow(BLOCK_DECAY, time - this.framejob$switchTime) : 0;
            for (int i = 0; i < 16; i++) this.framejob$shown[i] = this.framejob$pose[i] + this.framejob$offset[i] * weight;
            if (weight > 0.001F) {
                if (framejob$isRigid(this.framejob$pose)) framejob$makeRigid(this.framejob$shown, this.framejob$pose, this.framejob$scaleOffset, weight);
                BUFFER.put(this.framejob$shown).rewind();
                GlStateManager.loadIdentity();
                GlStateManager.multMatrix(BUFFER);
            }
            this.framejob$wasBlocking = blocking;
            this.framejob$hasShown = true;
        }
        original.call(renderer, entity, stack, renderPass);
    }

    @Unique
    private boolean framejob$canBlock() {
        return this.itemToRender != null && this.itemToRender.getItemUseAction() == EnumAction.block;
    }

    @Unique
    private boolean framejob$isBlocking() {
        return this.mc.thePlayer != null && this.mc.thePlayer.getItemInUseCount() > 0 && this.framejob$canBlock();
    }

    @Unique
    private static boolean framejob$isRigid(float[] matrix) {
        return Math.abs(framejob$cosine(matrix, 0, 4)) < 0.001F
            && Math.abs(framejob$cosine(matrix, 0, 8)) < 0.001F
            && Math.abs(framejob$cosine(matrix, 4, 8)) < 0.001F;
    }

    @Unique
    private static void framejob$makeRigid(float[] matrix, float[] pose, float[] scaleOffset, float weight) {
        float scaleX = framejob$length(pose, 0) + scaleOffset[0] * weight;
        float scaleY = framejob$length(pose, 4) + scaleOffset[1] * weight;
        float scaleZ = framejob$length(pose, 8) + scaleOffset[2] * weight;
        framejob$normalize(matrix, 0);
        float dot = matrix[0] * matrix[4] + matrix[1] * matrix[5] + matrix[2] * matrix[6];
        for (int i = 0; i < 3; i++) matrix[4 + i] -= matrix[i] * dot;
        framejob$normalize(matrix, 4);
        matrix[8] = matrix[1] * matrix[6] - matrix[2] * matrix[5];
        matrix[9] = matrix[2] * matrix[4] - matrix[0] * matrix[6];
        matrix[10] = matrix[0] * matrix[5] - matrix[1] * matrix[4];
        for (int i = 0; i < 3; i++) {
            matrix[i] *= scaleX;
            matrix[4 + i] *= scaleY;
            matrix[8 + i] *= scaleZ;
        }
    }

    @Unique
    private static float framejob$cosine(float[] matrix, int a, int b) {
        float dot = matrix[a] * matrix[b] + matrix[a + 1] * matrix[b + 1] + matrix[a + 2] * matrix[b + 2];
        return dot / (framejob$length(matrix, a) * framejob$length(matrix, b));
    }

    @Unique
    private static float framejob$length(float[] matrix, int column) {
        return (float) Math.sqrt(matrix[column] * matrix[column] + matrix[column + 1] * matrix[column + 1] + matrix[column + 2] * matrix[column + 2]);
    }

    @Unique
    private static void framejob$normalize(float[] matrix, int column) {
        float length = framejob$length(matrix, column);
        for (int i = 0; i < 3; i++) matrix[column + i] /= length;
    }
}

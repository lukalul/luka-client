package dev.motionblur.mixin;

import dev.motionblur.BlurRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Inject(
        method = "updateCameraAndRender",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/profiler/Profiler;endStartSection(Ljava/lang/String;)V",
            shift = At.Shift.BEFORE
        ),
        require = 0
    )
    private void motionblur$afterWorldBeforeGui(float partialTicks, long finishTimeNano, CallbackInfo ci) {
        BlurRenderer.render(Minecraft.getMinecraft());
    }
}

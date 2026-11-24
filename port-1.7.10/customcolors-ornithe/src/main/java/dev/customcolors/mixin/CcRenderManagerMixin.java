package dev.customcolors.mixin;

import dev.customcolors.mobs.MobSkins;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Marks which entity is being drawn so its textures can be varied. The mark is cleared on every exit;
 * vanilla turns every failure in this method into a crash report first, so that call clears it too.
 */
@Mixin(RenderManager.class)
public abstract class CcRenderManagerMixin {
    @Inject(method = "doRenderEntity", at = @At("HEAD"), require = 0)
    private void customcolors$begin(Entity entity, double x, double y, double z, float yaw, float partialTicks,
                                    boolean hideBox, CallbackInfoReturnable<Boolean> cir) {
        MobSkins.begin(entity);
    }

    @Inject(method = "doRenderEntity", at = @At("RETURN"), require = 0)
    private void customcolors$end(Entity entity, double x, double y, double z, float yaw, float partialTicks,
                                  boolean hideBox, CallbackInfoReturnable<Boolean> cir) {
        MobSkins.end();
    }

    @Inject(method = "doRenderEntity",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/crash/CrashReport;makeCrashReport(Ljava/lang/Throwable;Ljava/lang/String;)Lnet/minecraft/crash/CrashReport;"),
        require = 0)
    private void customcolors$failed(Entity entity, double x, double y, double z, float yaw, float partialTicks,
                                     boolean hideBox, CallbackInfoReturnable<Boolean> cir) {
        MobSkins.end();
    }
}

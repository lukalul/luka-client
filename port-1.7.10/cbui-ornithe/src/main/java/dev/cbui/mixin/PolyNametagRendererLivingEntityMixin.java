package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cbui.Cbui;
import dev.cbui.feature.polynametag.NametagHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RendererLivingEntity.class)
public abstract class PolyNametagRendererLivingEntityMixin {
  @Unique private EntityLivingBase cbui$checkedEntity;

  @Inject(method = "canRenderName(Lnet/minecraft/entity/EntityLivingBase;)Z", at = @At("HEAD"), require = 0)
  private void cbui$captureCheckedEntity(EntityLivingBase entity, CallbackInfoReturnable<Boolean> cir) { cbui$checkedEntity = entity; }

  @ModifyReturnValue(method = "canRenderName(Lnet/minecraft/entity/EntityLivingBase;)Z", at = @At("RETURN"), require = 0)
  private boolean cbui$applySelfRule(boolean vanilla) {
    try { return NametagHooks.ownLabel(vanilla, cbui$checkedEntity); }
    catch (Throwable error) { Cbui.reportOnce("nametag own hook", error); return vanilla; }
  }

  @WrapOperation(method = "canRenderName(Lnet/minecraft/entity/EntityLivingBase;)Z",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;isGuiEnabled()Z"), require = 0)
  private boolean cbui$applyHudRule(Operation<Boolean> vanillaCall) {
    boolean vanilla = vanillaCall.call();
    try { return NametagHooks.hudAllows(vanilla, cbui$checkedEntity); }
    catch (Throwable error) { Cbui.reportOnce("nametag hud hook", error); return vanilla; }
  }

  @ModifyExpressionValue(method = "passSpecialRender(Lnet/minecraft/entity/EntityLivingBase;DDD)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/EntityLivingBase;isSneaking()Z", ordinal = 1), require = 0)
  private boolean cbui$routeSneakingLabel(boolean sneaking) {
    try { return sneaking && NametagHooks.enabledSettings() == null; }
    catch (Throwable error) { Cbui.reportOnce("nametag sneak hook", error); return sneaking; }
  }
}

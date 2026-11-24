package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.cbui.feature.nickhider.NickHiderModule;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Nick Hider's skin switches. The player model and first-person arm take their skin from here; the Steve texture is a
 * classic 64x32 skin, so luka's 64x64 and slim models are not picked for a hidden skin.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class NickHiderSkinMixin {
  @ModifyReturnValue(method = "getLocationSkin()Lnet/minecraft/util/ResourceLocation;", at = @At("RETURN"), require = 0)
  private ResourceLocation cbui$defaultSkin(ResourceLocation skin) {
    return NickHiderModule.hideSkin(((AbstractClientPlayer) (Object) this).getUniqueID()) ? AbstractClientPlayer.locationStevePng : skin;
  }
}

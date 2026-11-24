package dev.cbui.mixin.skins;

import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import dev.cbui.skins.PlayerSkins;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.client.resources.SkinManager$SkinAvailableCallback;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A player's skin reaches the game here, together with its metadata. When the waiting callback is a player, the arm
 * model from that metadata is stored for them before the skin can be shown.
 */
@Mixin(SkinManager.class)
public abstract class SkinsSkinManagerMixin {
  @Inject(method = "loadSkin(Lcom/mojang/authlib/minecraft/MinecraftProfileTexture;"
      + "Lcom/mojang/authlib/minecraft/MinecraftProfileTexture$Type;"
      + "Lnet/minecraft/client/resources/SkinManager$SkinAvailableCallback;)Lnet/minecraft/util/ResourceLocation;",
      at = @At("HEAD"), require = 0)
  private void cbui$readArmModel(MinecraftProfileTexture texture, MinecraftProfileTexture.Type type,
      SkinManager$SkinAvailableCallback callback, CallbackInfoReturnable<ResourceLocation> cir) {
    if (type == MinecraftProfileTexture.Type.SKIN && callback instanceof AbstractClientPlayer) {
      PlayerSkins.remember(((AbstractClientPlayer) callback).getUniqueID(), texture);
    }
  }
}

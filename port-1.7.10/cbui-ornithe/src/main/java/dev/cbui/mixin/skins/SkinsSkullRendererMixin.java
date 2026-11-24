package dev.cbui.mixin.skins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.authlib.GameProfile;
import dev.cbui.Cbui;
import dev.cbui.skins.PlayerHeadModel;
import dev.cbui.skins.PlayerSkins;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.tileentity.TileEntitySkullRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Player heads (placed, or worn on a player's head) use the owner's skin with a model built for 64x32 textures. Now
 * that skins are 64x64, a head whose skin really is 64x64 is drawn with a matching model instead.
 */
@Mixin(TileEntitySkullRenderer.class)
public abstract class SkinsSkullRendererMixin {
  private static final String DRAW_SKULL = "m_95502655(FFFIFILcom/mojang/authlib/GameProfile;)V";

  /** The skin of the head being drawn right now; null for mob skulls and heads without a skin. */
  @Unique private ResourceLocation cbui$skin;
  @Unique private PlayerHeadModel cbui$headModel;

  @Inject(method = DRAW_SKULL, at = @At("HEAD"), require = 0)
  private void cbui$forgetSkin(float x, float y, float z, int side, float rotation, int type, GameProfile owner,
      CallbackInfo ci) {
    cbui$skin = null;
  }

  @ModifyExpressionValue(method = DRAW_SKULL, at = @At(value = "INVOKE",
      target = "Lnet/minecraft/client/resources/SkinManager;loadSkin(Lcom/mojang/authlib/minecraft/MinecraftProfileTexture;"
          + "Lcom/mojang/authlib/minecraft/MinecraftProfileTexture$Type;)Lnet/minecraft/util/ResourceLocation;"),
      require = 0)
  private ResourceLocation cbui$noteSkin(ResourceLocation skin) {
    cbui$skin = skin;
    return skin;
  }

  @WrapOperation(method = DRAW_SKULL, at = @At(value = "INVOKE",
      target = "Lnet/minecraft/client/model/ModelBase;render(Lnet/minecraft/entity/Entity;FFFFFF)V"), require = 0)
  private void cbui$tallHead(ModelBase model, Entity entity, float limbSwing, float limbSwingAmount, float age,
      float headYaw, float headPitch, float scale, Operation<Void> original) {
    ModelBase use = model;
    ResourceLocation skin = cbui$skin;
    cbui$skin = null;
    if (skin != null) {
      try {
        if (PlayerSkins.isModern(skin)) {
          if (cbui$headModel == null) cbui$headModel = new PlayerHeadModel();
          use = cbui$headModel;
        }
      } catch (Throwable error) {
        Cbui.reportOnce("player head skins", error);
      }
    }
    original.call(use, entity, limbSwing, limbSwingAmount, age, headYaw, headPitch, scale);
  }
}

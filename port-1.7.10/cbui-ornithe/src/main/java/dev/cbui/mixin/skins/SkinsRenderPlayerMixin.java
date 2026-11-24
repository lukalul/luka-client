package dev.cbui.mixin.skins;

import dev.cbui.Cbui;
import dev.cbui.skins.PlayerSkinModel;
import dev.cbui.skins.PlayerSkins;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * One RenderPlayer draws every player, so the model is chosen per player for the length of each draw: slim or classic
 * for a 64x64 skin, and the game's own model for anything still 64x32 (Steve, a skin still downloading). Both the body
 * pass (mainModel) and the extras that read modelBipedMain (held items, ears, cape, first-person arm) see the choice.
 * Armour keeps its own models.
 */
@Mixin(RenderPlayer.class)
public abstract class SkinsRenderPlayerMixin extends RendererLivingEntity {
  @Shadow private ModelBiped modelBipedMain;

  @Unique private ModelBiped cbui$vanillaModel;
  @Unique private PlayerSkinModel cbui$classicModel;
  @Unique private PlayerSkinModel cbui$slimModel;
  @Unique private boolean cbui$failed;

  private SkinsRenderPlayerMixin() {
    super(null, 0.0F);
  }

  @Inject(method = "doRender(Lnet/minecraft/client/entity/AbstractClientPlayer;DDDFF)V", at = @At("HEAD"), require = 0)
  private void cbui$pickModel(AbstractClientPlayer player, double x, double y, double z, float yaw, float partialTicks,
      CallbackInfo ci) {
    cbui$use(player);
  }

  @Inject(method = "doRender(Lnet/minecraft/client/entity/AbstractClientPlayer;DDDFF)V", at = @At("RETURN"), require = 0)
  private void cbui$restoreModel(AbstractClientPlayer player, double x, double y, double z, float yaw, float partialTicks,
      CallbackInfo ci) {
    cbui$restore();
  }

  @Inject(method = "renderFirstPersonArm(Lnet/minecraft/entity/player/EntityPlayer;)V", at = @At("HEAD"), require = 0)
  private void cbui$pickArmModel(EntityPlayer player, CallbackInfo ci) {
    cbui$use(player);
  }

  /** The game draws only the bare arm in first person; the sleeve goes right after it, already posed by the model. */
  @Inject(method = "renderFirstPersonArm(Lnet/minecraft/entity/player/EntityPlayer;)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/ModelRenderer;render(F)V", shift = At.Shift.AFTER),
      require = 0)
  private void cbui$drawSleeve(EntityPlayer player, CallbackInfo ci) {
    if (modelBipedMain instanceof PlayerSkinModel) ((PlayerSkinModel) modelBipedMain).bipedRightArmWear.render(0.0625F);
  }

  @Inject(method = "renderFirstPersonArm(Lnet/minecraft/entity/player/EntityPlayer;)V", at = @At("RETURN"), require = 0)
  private void cbui$restoreArmModel(EntityPlayer player, CallbackInfo ci) {
    cbui$restore();
  }

  @Unique
  private void cbui$use(EntityPlayer player) {
    if (cbui$vanillaModel == null) cbui$vanillaModel = modelBipedMain;
    ModelBiped chosen = cbui$vanillaModel;
    if (!cbui$failed && player instanceof AbstractClientPlayer) {
      try {
        AbstractClientPlayer clientPlayer = (AbstractClientPlayer) player;
        if (PlayerSkins.isModern(clientPlayer.getLocationSkin())) {
          if (PlayerSkins.isSlim(clientPlayer.getUniqueID())) {
            if (cbui$slimModel == null) cbui$slimModel = new PlayerSkinModel(true);
            chosen = cbui$slimModel;
          } else {
            if (cbui$classicModel == null) cbui$classicModel = new PlayerSkinModel(false);
            chosen = cbui$classicModel;
          }
        }
      } catch (Throwable error) {
        cbui$failed = true;
        Cbui.reportOnce("player skin models", error);
      }
    }
    cbui$set(chosen);
  }

  @Unique
  private void cbui$restore() {
    if (cbui$vanillaModel != null && modelBipedMain != cbui$vanillaModel) cbui$set(cbui$vanillaModel);
  }

  @Unique
  private void cbui$set(ModelBiped model) {
    modelBipedMain = model;
    mainModel = model;
  }
}

package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.cbui.feature.oldanimations.LegacyGlint;
import dev.cbui.feature.oldanimations.OldAnimationsHooks;
import dev.cbui.feature.oldanimations.OldAnimationsModule;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Old Animations: 1.7 first person item angles and the 1.7 enchantment glint (in the world and in GUIs). */
@Mixin(RenderItem.class)
public abstract class OldAnimationsRenderItemMixin {
  @Shadow @Final private static ResourceLocation RES_ITEM_GLINT;
  @Shadow @Final private TextureManager textureManager;
  @Shadow public float zLevel;
  @Shadow protected abstract void renderModel(IBakedModel model, int color);

  @Unique private boolean cbui$renderingGui;

  /** First person: when the 1.7 transform was applied, the vanilla transform is skipped. */
  @WrapWithCondition(method = "renderItemModelTransform(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/resources/model/IBakedModel;Lnet/minecraft/client/renderer/block/model/ItemCameraTransforms$TransformType;)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/model/ItemCameraTransforms;applyTransform(Lnet/minecraft/client/renderer/block/model/ItemCameraTransforms$TransformType;)V"),
      require = 0)
  private boolean cbui$oldFirstPerson(ItemCameraTransforms transforms, ItemCameraTransforms.TransformType type,
      @Local(argsOnly = true, ordinal = 0) ItemStack stack, @Local(argsOnly = true) IBakedModel model) {
    try {
      return !(type == ItemCameraTransforms.TransformType.FIRST_PERSON && OldAnimationsHooks.applyFirstPerson(stack, model));
    } catch (Throwable ignored) { return true; }
  }

  /** The 1.7 glint on models in the world / hand. */
  @Inject(method = "renderEffect", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$oldGlint(IBakedModel model, CallbackInfo ci) {
    try {
      OldAnimationsModule m = OldAnimationsModule.active();
      if (m == null || !m.glintRendering.get()) return;
      if (cbui$renderingGui) {
        if (GL11.glIsEnabled(GL11.GL_DEPTH_TEST)) ci.cancel();
        return;
      }
      IBakedModel itemSpace = LegacyGlint.itemSpaceModel(model);
      ci.cancel();
      GlStateManager.depthMask(false);
      GlStateManager.depthFunc(514);
      GlStateManager.disableLighting();
      GlStateManager.blendFunc(768, 1);
      this.textureManager.bindTexture(RES_ITEM_GLINT);
      GlStateManager.matrixMode(5890);
      long now = dev.cbui.render.GlintClock.now();
      for (int i = 0; i < 2; i++) {
        GlStateManager.pushMatrix();
        float shift = LegacyGlint.scroll(now, i) * 8.0f;
        GlStateManager.scale(0.125f, 0.125f, 0.125f);
        GlStateManager.translate(i == 0 ? shift : -shift, 0.0f, 0.0f);
        GlStateManager.rotate(i == 0 ? -50.0f : 10.0f, 0.0f, 0.0f, 1.0f);
        this.renderModel(itemSpace, LegacyGlint.GLINT_COLOR);
        GlStateManager.popMatrix();
      }
      GlStateManager.matrixMode(5888);
      GlStateManager.blendFunc(770, 771);
      GlStateManager.enableLighting();
      GlStateManager.depthFunc(515);
      GlStateManager.depthMask(true);
      this.textureManager.bindTexture(TextureMap.locationBlocksTexture);
    } catch (Throwable ignored) { }
  }

  /** Marks the model draw inside renderItemIntoGUI so the glint code knows it is a GUI draw. */
  @WrapOperation(method = "renderItemIntoGUI",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/RenderItem;renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/resources/model/IBakedModel;)V"),
      require = 0)
  private void cbui$markGui(RenderItem instance, ItemStack stack, IBakedModel model, Operation<Void> original) {
    cbui$renderingGui = true;
    try { original.call(instance, stack, model); } finally { cbui$renderingGui = false; }
  }

  /** The flat 1.7 glint overlay on GUI items. */
  @Inject(method = "renderItemIntoGUI", at = @At("TAIL"), require = 0)
  private void cbui$oldGuiGlint(ItemStack stack, int x, int y, CallbackInfo ci) {
    try {
      OldAnimationsModule m = OldAnimationsModule.active();
      if (m == null || !m.glintRendering.get() || stack == null || !stack.hasEffect() || dev.cbui.render.GlintDistance.skip() || !GL11.glIsEnabled(GL11.GL_DEPTH_TEST)) return;
      LegacyGlint.drawGuiGlint(this.textureManager, RES_ITEM_GLINT, this.zLevel, x, y);
    } catch (Throwable ignored) { }
  }
}

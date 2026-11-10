package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cbui.feature.smoothskies.SmoothSkiesModule;
import net.minecraft.client.renderer.RenderGlobal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Hides the dark slab vanilla draws under the horizon: it is the second sky display list, so skipping that one call is enough.
 * Kept in its own class so a problem here cannot take the other sky hooks down with it.
 */
@Mixin(RenderGlobal.class)
public abstract class SmoothSkiesVoidBoxMixin {
  @Shadow private int glSkyList2;

  @WrapOperation(
      method = "renderSky(F)V",
      at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glCallList(I)V", remap = false),
      require = 0)
  private void cbui$dropHorizonSlab(int displayList, Operation<Void> vanillaCall) {
    SmoothSkiesModule module = displayList == glSkyList2 ? SmoothSkiesModule.active() : null;
    if (module != null && module.removeVoidBox.get()) return;
    vanillaCall.call(displayList);
  }
}

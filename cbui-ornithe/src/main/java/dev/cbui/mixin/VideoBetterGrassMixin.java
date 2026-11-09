package dev.cbui.mixin;

import dev.cbui.config.GlobalOptions;
import dev.cbui.render.BetterGrassModel;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.block.BlockGrass;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockModelShapes;
import net.minecraft.client.resources.model.IBakedModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Only the Vulkan renderer needs this: on the other one Cera has its own Better Grass. */
@Mixin(BlockModelShapes.class)
public abstract class VideoBetterGrassMixin {
  @Unique private final Map<IBakedModel, IBakedModel> cbui$betterGrass = new IdentityHashMap<>();

  @Inject(method = "getModelForState", at = @At("RETURN"), cancellable = true, require = 0)
  private void cbui$grass(IBlockState state, CallbackInfoReturnable<IBakedModel> cir) {
    if (!GlobalOptions.betterGrass || !(state.getBlock() instanceof BlockGrass) || state.getValue(BlockGrass.SNOWY)) return;
    IBakedModel base = cir.getReturnValue();
    if (base == null) return;
    synchronized (cbui$betterGrass) {
      IBakedModel wrapped = cbui$betterGrass.get(base);
      if (wrapped == null) {
        wrapped = new BetterGrassModel(base);
        cbui$betterGrass.put(base, wrapped);
      }
      cir.setReturnValue(wrapped);
    }
  }
}

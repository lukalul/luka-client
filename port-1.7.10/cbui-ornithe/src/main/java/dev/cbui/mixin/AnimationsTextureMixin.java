package dev.cbui.mixin;

import dev.cbui.config.GlobalOptions;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextureAtlasSprite.class)
public abstract class AnimationsTextureMixin {
  @Inject(method = "updateAnimation", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$filterAnimatedTexture(CallbackInfo ci) {
    String name = ((TextureAtlasSprite) (Object) this).getIconName().toLowerCase(java.util.Locale.ROOT);
    if ((name.contains("water_still") || name.contains("water_flow")) && !GlobalOptions.animationEnabled("water_animation")) ci.cancel();
    else if ((name.contains("lava_still") || name.contains("lava_flow")) && !GlobalOptions.animationEnabled("lava_animation")) ci.cancel();
    else if (name.contains("fire_layer") && !GlobalOptions.animationEnabled("fire_animation")) ci.cancel();
    else if (name.contains("portal") && !GlobalOptions.animationEnabled("portal_animation")) ci.cancel();
  }
}

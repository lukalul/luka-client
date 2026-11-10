package yt.ren.astatine.mixin.async;

import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.resource.manager.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yt.ren.astatine.terrain.async.TerrainWorkers;

/** luka: no mesh worker reads sprites while the atlas is stitched (see TerrainWorkers). */
@Mixin(value = TextureAtlas.class, priority = 900)
public abstract class TextureAtlasAsyncMixin {
	@Inject(method = "loadAndStitch", at = @At("HEAD"), require = 0)
	private void astatine$pauseWorkers(ResourceManager manager, CallbackInfo callback) {
		TerrainWorkers.pauseForStitch();
	}

	@Inject(method = "loadAndStitch", at = @At("RETURN"), require = 0)
	private void astatine$resumeWorkers(ResourceManager manager, CallbackInfo callback) {
		TerrainWorkers.resumeAfterStitch();
	}
}

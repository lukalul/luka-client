package yt.ren.astatine.mixin.texture;

import java.util.Map;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.resource.Identifier;
import net.minecraft.client.resource.manager.ResourceManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yt.ren.astatine.terrain.ConnectedTextures;

/**
 * Adds the connected texture tiles of the enabled packs to the block atlas each time it is stitched. 1.7.10 keeps its
 * sprite list for the life of the atlas, so tiles from the previous stitch are taken out first.
 */
@Mixin(TextureAtlas.class)
public abstract class TextureAtlasCtmMixin {
	@Shadow @Final private Map sourcedSprites;
	@Shadow @Final private int type;

	@Inject(method = "loadAndStitch", at = @At("HEAD"), require = 0)
	private void astatine$ctmRegister(ResourceManager manager, CallbackInfo callback) {
		if (this.type == 0) {
			ConnectedTextures.register((TextureAtlas) (Object) this, this.sourcedSprites);
		}
	}

	@Inject(method = "loadAndStitch", at = @At("RETURN"), require = 0)
	private void astatine$ctmBake(ResourceManager manager, CallbackInfo callback) {
		if (this.type == 0) {
			ConnectedTextures.bake((TextureAtlas) (Object) this, this.sourcedSprites);
		}
	}

	/** Tiles live outside textures/blocks, so their sprite names carry the whole path. */
	@Inject(method = "getResourceId", at = @At("HEAD"), cancellable = true, require = 0)
	private void astatine$ctmPath(Identifier id, int mipmap, CallbackInfoReturnable<Identifier> callback) {
		if (this.type == 0) {
			Identifier tile = ConnectedTextures.resource(id, mipmap);
			if (tile != null) {
				callback.setReturnValue(tile);
			}
		}
	}
}

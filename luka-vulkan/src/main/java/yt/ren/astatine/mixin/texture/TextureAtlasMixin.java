package yt.ren.astatine.mixin.texture;

import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.resource.manager.ResourceManager;
import net.minecraft.resource.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yt.ren.astatine.render.geometry.ItemMeshes;
import yt.ren.astatine.terrain.Ctm;
import yt.ren.astatine.extension.TextureHolder;
import yt.ren.astatine.render.TextureBinding;

@Mixin(TextureAtlas.class)
public abstract class TextureAtlasMixin {
	@Inject(method = "loadAndStitch", at = @At("HEAD"))
	private void astatine$connectedTextures(ResourceManager manager, CallbackInfo callback) {
		ItemMeshes.clear();
		yt.ren.astatine.Native.INSTANCE.setAnisotropy(Integer.getInteger("luka.anisotropy", 1));
		Ctm.registerSprites((TextureAtlas) (Object) this, manager);
		yt.ren.astatine.terrain.BakedBlockEntities.registerSprites((TextureAtlas) (Object) this);
	}

	/** Connected texture tiles live outside textures/, so their sprite name is already the whole path. */
	@Inject(method = "getResourceId", at = @At("HEAD"), cancellable = true)
	private void astatine$tilePath(Identifier id, int mipLevel, CallbackInfoReturnable<Identifier> callback) {
		String path = id.getPath();
		if (mipLevel == 0 && (path.startsWith("mcpatcher/") || path.startsWith("optifine/"))) {
			callback.setReturnValue(new Identifier(id.getNamespace(), path + ".png"));
		}
	}

	@Redirect(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/texture/TextureAtlas;getGlId()I"))
	private int astatine$bindSelf(TextureAtlas texture) {
		TextureBinding.bind(((TextureHolder) texture).astatine$texture());
		return TextureBinding.OBJECT;
	}
}

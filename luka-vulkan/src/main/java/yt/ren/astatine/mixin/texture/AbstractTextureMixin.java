package yt.ren.astatine.mixin.texture;

import net.minecraft.client.render.texture.AbstractTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yt.ren.astatine.Native;
import yt.ren.astatine.extension.TextureHolder;

@Mixin(AbstractTexture.class)
public abstract class AbstractTextureMixin implements TextureHolder {
	@Unique
	private final long astatine$texture = Native.textureCreate();

	@Override
	public long astatine$texture() {
		return this.astatine$texture;
	}

	@Unique
	private int astatine$registeredName = -1;

	// luka: mods that bind by int name (GlStateManager.bindTexture(texture.getGlId())) need the name mapped to this texture
	@Inject(method = "getGlId", at = @At("RETURN"))
	private void astatine$register(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Integer> callback) {
		int name = callback.getReturnValueI();
		if (name != this.astatine$registeredName) {
			this.astatine$registeredName = name;
			yt.ren.astatine.compat.TextureNames.put(name, this.astatine$texture);
		}
	}

	@Inject(method = "clearGlId", at = @At("HEAD"))
	private void astatine$release(CallbackInfo callback) {
		Native.textureDetach(this.astatine$texture);
	}
}

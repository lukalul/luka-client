package yt.ren.astatine.mixin.texture;

import net.minecraft.client.render.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import yt.ren.astatine.terrain.CtmSlot;

@Mixin(TextureAtlasSprite.class)
public abstract class TextureAtlasSpriteCtmMixin implements CtmSlot {
	@Unique
	private Object astatine$ctmRules;

	@Override
	public Object astatine$ctm() {
		return this.astatine$ctmRules;
	}

	@Override
	public void astatine$setCtm(Object value) {
		this.astatine$ctmRules = value;
	}
}

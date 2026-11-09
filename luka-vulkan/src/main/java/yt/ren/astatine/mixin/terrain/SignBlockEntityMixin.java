package yt.ren.astatine.mixin.terrain;

import net.minecraft.block.entity.SignBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import yt.ren.astatine.extension.LidBaked;

@Mixin(SignBlockEntity.class)
public abstract class SignBlockEntityMixin implements LidBaked {
	@Unique private volatile boolean astatine$boardBaked;

	@Override
	public void astatine$built(boolean baked) {
		this.astatine$boardBaked = baked;
	}

	@Override
	public boolean astatine$baked() {
		return this.astatine$boardBaked;
	}
}

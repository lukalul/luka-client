package yt.ren.astatine.mixin.terrain;

import net.minecraft.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import yt.ren.astatine.terrain.CtmSlot;

@Mixin(Block.class)
public abstract class BlockCtmMixin implements CtmSlot {
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

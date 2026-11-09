package yt.ren.astatine.mixin.terrain;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yt.ren.astatine.extension.LidBaked;

/** luka: same as ChestBlockEntityMixin, for ender chests. */
@Mixin(EnderChestBlockEntity.class)
public abstract class EnderChestBlockEntityMixin extends BlockEntity implements LidBaked {
	@Shadow public int viewerCount;
	@Shadow public float animationProgress;

	@Unique private volatile boolean astatine$bakedShape = true;
	@Unique private volatile boolean astatine$pending;

	@Override
	public void astatine$built(boolean baked) {
		this.astatine$bakedShape = baked;
		this.astatine$pending = false;
	}

	@Override
	public boolean astatine$baked() {
		return this.astatine$bakedShape;
	}

	@Inject(method = "tick", at = @At("RETURN"))
	private void astatine$watchLid(CallbackInfo callback) {
		if (this.world == null || !this.world.isClient) return;
		boolean closed = this.viewerCount == 0 && this.animationProgress == 0.0F;
		if (closed != this.astatine$bakedShape && !this.astatine$pending) {
			this.astatine$pending = true;
			this.world.notifyRegionChanged(this.pos, this.pos);
		}
	}
}

package yt.ren.astatine.mixin.async;

import net.minecraft.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import yt.ren.astatine.terrain.async.BlockBounds;
import yt.ren.astatine.terrain.async.BoundsAccess;

/**
 * luka: gives every block the raw access to its bounds fields and the per-worker copies (see BlockBounds). The six
 * shadow fields are also how AsyncBoundsPlugin learns the fields' names at run time: they are declared here in the
 * order minX, minY, minZ, maxX, maxY, maxZ and the jar's remapping renames them to the names the game runs with.
 * The plugin leaves the two astatine$raw methods alone, so they keep reading and writing the real fields.
 */
@Mixin(Block.class)
public abstract class BlockBoundsMixin implements BoundsAccess {
	@Shadow protected double minX;
	@Shadow protected double minY;
	@Shadow protected double minZ;
	@Shadow protected double maxX;
	@Shadow protected double maxY;
	@Shadow protected double maxZ;

	@Unique
	private final double[] astatine$boundSlots = new double[BlockBounds.SLOTS * 7];

	@Override
	public double astatine$rawGet(int index) {
		switch (index) {
			case 0: return this.minX;
			case 1: return this.minY;
			case 2: return this.minZ;
			case 3: return this.maxX;
			case 4: return this.maxY;
			default: return this.maxZ;
		}
	}

	@Override
	public void astatine$rawSet(int index, double value) {
		switch (index) {
			case 0: this.minX = value; break;
			case 1: this.minY = value; break;
			case 2: this.minZ = value; break;
			case 3: this.maxX = value; break;
			case 4: this.maxY = value; break;
			default: this.maxZ = value; break;
		}
	}

	@Override
	public double[] astatine$slots() {
		return this.astatine$boundSlots;
	}
}

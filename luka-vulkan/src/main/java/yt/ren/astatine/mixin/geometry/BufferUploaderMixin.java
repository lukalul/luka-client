package yt.ren.astatine.mixin.geometry;

import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.BufferUploader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import yt.ren.astatine.shaderpack.WorldDraws;
import yt.ren.astatine.memory.Memory;
import yt.ren.astatine.render.geometry.VertexLayouts;

@Mixin(BufferUploader.class)
public abstract class BufferUploaderMixin {
	/**
	 * @author Ren
	 * @reason Astatine renderer
	 */
	@Overwrite
	public void end(BufferBuilder builder) {
		int count = builder.getVertexCount();
		if (count > 0) {
			int layout = VertexLayouts.of(builder.getFormat());
			long source = Memory.INSTANCE.address(builder.getBuffer());
			// luka: inside glNewList the geometry goes into the display list instead of being drawn
			if (!yt.ren.astatine.compat.GLCompat.captureDraw(layout, builder.getDrawMode(), source, count)) {
				WorldDraws.draw(layout, builder.getDrawMode(), source, count);
			}
		}
		builder.clear();
	}
}

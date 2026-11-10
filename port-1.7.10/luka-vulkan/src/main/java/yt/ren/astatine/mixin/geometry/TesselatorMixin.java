package yt.ren.astatine.mixin.geometry;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import net.minecraft.client.render.vertex.Tesselator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import yt.ren.astatine.Native;
import yt.ren.astatine.compat.GLCompat;
import yt.ren.astatine.memory.Memory;
import yt.ren.astatine.terrain.SectionCapture;

/**
 * 1.7.10 has one global Tesselator and no vertex-format layer: its end() sets up client arrays and draws. Here the
 * same vertex data goes either to chunk capture, into the display list being compiled, or straight to the renderer.
 */
@Mixin(Tesselator.class)
public abstract class TesselatorMixin {
	@Shadow private ByteBuffer byteBuffer;
	@Shadow private IntBuffer intBuffer;
	@Shadow private int[] vertexBuffer;
	@Shadow private int vertexCount;
	@Shadow private boolean tesselating;
	@Shadow private boolean textured;
	@Shadow private boolean hasBrightness;
	@Shadow private boolean colored;
	@Shadow private boolean hasNormals;
	@Shadow private int index;
	@Shadow private int drawMode;

	@Shadow
	private void clear() {
		throw new AssertionError();
	}

	/**
	 * @author luka
	 * @reason Draw through the Vulkan renderer rather than OpenGL client arrays
	 */
	@Overwrite
	public int end() {
		if (!this.tesselating) {
			throw new IllegalStateException("Not tesselating!");
		}
		this.tesselating = false;
		if (this.vertexCount > 0) {
			if (SectionCapture.active) {
				SectionCapture.append(this.vertexBuffer, this.index);
			} else {
				this.intBuffer.clear();
				this.intBuffer.put(this.vertexBuffer, 0, this.index);
				this.byteBuffer.position(0);
				this.byteBuffer.limit(this.index * 4);
				int layout = GLCompat.tesselatorLayout(this.textured, this.colored, this.hasNormals, this.hasBrightness);
				long source = Memory.INSTANCE.address(this.byteBuffer);
				if (!GLCompat.captureDraw(layout, this.drawMode, source, this.vertexCount)) {
					Native.draw(layout, this.drawMode, source, this.vertexCount);
				}
			}
		}
		int bytes = this.index * 4;
		this.clear();
		return bytes;
	}
}

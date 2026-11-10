package yt.ren.astatine.terrain.async;

/** luka: where a mesh worker's Tesselator hands its finished vertices instead of drawing them. */
public interface CaptureSink {
	void append(int[] vertices, int ints);

	/** Implemented by the Tesselator (TesselatorMixin); null, the default, is the game's normal behaviour. */
	interface Holder {
		void astatine$setSink(CaptureSink sink);
	}
}

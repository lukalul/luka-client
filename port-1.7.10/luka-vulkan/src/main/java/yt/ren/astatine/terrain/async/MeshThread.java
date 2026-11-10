package yt.ren.astatine.terrain.async;

/**
 * luka: marks a mesh worker thread. BlockBounds tests for it on every bounds access from the first block the game
 * creates on, so it refers to nothing that would load renderer classes that early.
 */
public interface MeshThread {
	/** This worker's slot in BlockBounds. */
	int astatine$slot();

	/** This worker's own Tesselator (a net.minecraft Tesselator; Object keeps this interface free of game classes). */
	Object astatine$tesselator();
}

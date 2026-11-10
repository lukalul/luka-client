package yt.ren.astatine.terrain.async;

/**
 * luka: what {@code mixin/async/BlockBoundsMixin} adds to every Block: the game's own six bounds fields, read and
 * written without the per-thread rewrite, and the storage for each mesh worker's private copy of them.
 */
public interface BoundsAccess {
	/** The game's field: 0 minX, 1 minY, 2 minZ, 3 maxX, 4 maxY, 5 maxZ. */
	double astatine$rawGet(int index);

	void astatine$rawSet(int index, double value);

	/** {@link BlockBounds#SLOTS} blocks of 7 doubles: six bounds and a marker that is 1 once the slot holds a copy. */
	double[] astatine$slots();
}

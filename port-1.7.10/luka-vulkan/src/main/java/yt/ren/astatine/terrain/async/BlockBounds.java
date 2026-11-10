package yt.ren.astatine.terrain.async;

/**
 * luka: block bounds per thread, so sections can be meshed on worker threads.
 *
 * <p>In 1.7.10 a block's shape lives in six fields of the shared Block object, and drawing a block first writes its
 * shape there (updateShape, from the block and its neighbours) and then reads it back. The game thread does the same
 * for collisions and ray tracing at any time. {@code compat/AsyncBoundsPlugin} rewrites every read and write of those
 * fields in the block classes into calls here: on the game thread they go to the fields as before, on a mesh worker
 * to that worker's own copy, which starts as a copy of the fields the first time the worker touches the block.
 * Every drawing path sets the shape of a block whose shape depends on its position before it reads it, so a copy
 * taken at any time is correct; blocks with a fixed shape never change their fields after construction.
 */
public final class BlockBounds {
	/** Mesh workers that can hold their own bounds; worker n uses slot n. */
	public static final int SLOTS = 4;

	private BlockBounds() {
	}

	private static double get(Object block, int index) {
		Thread thread = Thread.currentThread();
		if (thread instanceof MeshThread) {
			double[] slots = ((BoundsAccess) block).astatine$slots();
			int base = ((MeshThread) thread).astatine$slot() * 7;
			if (slots[base + 6] == 0.0) fill((BoundsAccess) block, slots, base);
			return slots[base + index];
		}
		return ((BoundsAccess) block).astatine$rawGet(index);
	}

	private static void set(Object block, int index, double value) {
		Thread thread = Thread.currentThread();
		if (thread instanceof MeshThread) {
			double[] slots = ((BoundsAccess) block).astatine$slots();
			int base = ((MeshThread) thread).astatine$slot() * 7;
			if (slots[base + 6] == 0.0) fill((BoundsAccess) block, slots, base);
			slots[base + index] = value;
			return;
		}
		((BoundsAccess) block).astatine$rawSet(index, value);
	}

	private static void fill(BoundsAccess block, double[] slots, int base) {
		for (int i = 0; i < 6; i++) slots[base + i] = block.astatine$rawGet(i);
		slots[base + 6] = 1.0;
	}

	// Called from the rewritten bytecode: getN replaces GETFIELD of bound N, setN replaces PUTFIELD.
	public static double get0(Object block) { return get(block, 0); }
	public static double get1(Object block) { return get(block, 1); }
	public static double get2(Object block) { return get(block, 2); }
	public static double get3(Object block) { return get(block, 3); }
	public static double get4(Object block) { return get(block, 4); }
	public static double get5(Object block) { return get(block, 5); }
	public static void set0(Object block, double value) { set(block, 0, value); }
	public static void set1(Object block, double value) { set(block, 1, value); }
	public static void set2(Object block, double value) { set(block, 2, value); }
	public static void set3(Object block, double value) { set(block, 3, value); }
	public static void set4(Object block, double value) { set(block, 4, value); }
	public static void set5(Object block, double value) { set(block, 5, value); }
}

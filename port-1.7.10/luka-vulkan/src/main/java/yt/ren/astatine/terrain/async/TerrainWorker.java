package yt.ren.astatine.terrain.async;

import java.util.Arrays;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.material.Material;
import net.minecraft.client.render.block.BlockRenderer;
import net.minecraft.client.render.vertex.Tesselator;
import yt.ren.astatine.Native;
import yt.ren.astatine.memory.Memory;
import yt.ren.astatine.mixin.async.TesselatorInvoker;
import yt.ren.astatine.terrain.TerrainRenderer;

/**
 * luka: one mesh worker. Runs the same block loop as SectionCapture, against a {@link SectionSnapshot} instead of
 * the live world, with its own Tesselator (BlockRendererAsyncMixin), its own block bounds (BlockBounds) and its own
 * vertex buffers, and hands the result to the native mesher, which accepts it from any thread.
 */
public final class TerrainWorker extends Thread implements CaptureSink, MeshThread {
	private static final int LAYERS = 4;
	private static final long VERTEX_BYTES = 32L;

	/** This worker's bounds slot in BlockBounds. */
	final int slot;
	final Tesselator tesselator;

	private final long[] capacity = new long[LAYERS];
	private final long[] sinks = new long[LAYERS];
	private final long[] written = new long[LAYERS];
	private final long addresses = Memory.INSTANCE.allocate(LAYERS * 8L);
	private final long counts = Memory.INSTANCE.allocate(LAYERS * 4L);
	private final long opaque = Memory.INSTANCE.allocate(512L);
	private int currentLayer = TerrainRenderer.LAYER_SOLID;

	TerrainWorker(int slot) {
		super("Astatine terrain " + slot);
		this.slot = slot;
		setDaemon(true);
		setPriority(Thread.NORM_PRIORITY - 1);
		this.tesselator = TesselatorInvoker.astatine$create(1 << 18);
		((CaptureSink.Holder) (Object) this.tesselator).astatine$setSink(this);
		for (int i = 0; i < LAYERS; i++) {
			capacity[i] = 1L << 20;
			sinks[i] = Memory.INSTANCE.allocate(capacity[i]);
		}
	}

	@Override
	public int astatine$slot() {
		return slot;
	}

	@Override
	public Object astatine$tesselator() {
		return tesselator;
	}

	@Override
	public void run() {
		TerrainWorkers.loop(this);
	}

	@Override
	public void append(int[] vertices, int ints) {
		if (ints == 0) return;
		int layer = currentLayer;
		long bytes = ints * 4L;
		long needed = written[layer] + bytes;
		if (needed > capacity[layer]) {
			long size = capacity[layer];
			while (needed > size) size *= 2;
			long larger = Memory.INSTANCE.allocate(size);
			Memory.INSTANCE.copy(sinks[layer], larger, written[layer]);
			Memory.INSTANCE.free(sinks[layer]);
			sinks[layer] = larger;
			capacity[layer] = size;
		}
		Memory.INSTANCE.copy(vertices, 0, sinks[layer] + written[layer], ints);
		written[layer] = needed;
	}

	/** Meshes the job's section into this worker's buffers; returns the opaque block count. */
	int mesh(TerrainWorkers.Job job) {
		Arrays.fill(written, 0L);
		Memory.INSTANCE.fill(opaque, 512L, (byte) 0);
		SectionSnapshot view = job.snapshot;
		int minX = job.x << 4, minY = job.y << 4, minZ = job.z << 4;
		int opaqueCount = 0;
		BlockRenderer renderer = new BlockRenderer(view);
		Tesselator tesselator = this.tesselator;
		tesselator.begin();
		tesselator.offset(-minX, -minY, -minZ);
		currentLayer = TerrainRenderer.LAYER_SOLID;
		try {
			for (int pass = 0; pass < 2; pass++) {
				boolean more = false;
				for (int by = minY; by < minY + 16; by++) {
					for (int bz = minZ; bz < minZ + 16; bz++) {
						for (int bx = minX; bx < minX + 16; bx++) {
							Block block = view.getBlock(bx, by, bz);
							if (block.getMaterial() == Material.AIR) continue;
							if (pass == 0 && block.isOpaque()) {
								int index = (by - minY << 8) | (bz - minZ << 4) | (bx - minX);
								long at = opaque + (index >> 6) * 8L;
								Memory.INSTANCE.putLong(at, Memory.INSTANCE.getLong(at) | (1L << (index & 63)));
								opaqueCount++;
							}
							int layer = block.getRenderLayer();
							if (layer > pass) {
								more = true;
								continue;
							}
							if (layer != pass) continue;
							select(tesselator, target(block, pass), minX, minY, minZ);
							renderer.tesselateInWorld(block, bx, by, bz);
							if (block.getRenderType() == 0 && bx == job.cameraX && by == job.cameraY && bz == job.cameraZ) {
								renderer.setFlipDepth(true);
								renderer.setRenderFaceAlways(true);
								renderer.tesselateInWorld(block, bx, by, bz);
								renderer.setFlipDepth(false);
								renderer.setRenderFaceAlways(false);
							}
						}
					}
				}
				if (!more) break;
			}
		} finally {
			tesselator.end();
			tesselator.offset(0.0, 0.0, 0.0);
		}
		return opaqueCount;
	}

	/** Hands the meshed section to the native mesher. */
	void deliver(TerrainWorkers.Job job, int opaqueCount) {
		for (int layer = 0; layer < LAYERS; layer++) {
			Memory.INSTANCE.putLong(addresses + layer * 8L, sinks[layer]);
			Memory.INSTANCE.putInt(counts + layer * 4L, (int) (written[layer] / VERTEX_BYTES));
		}
		Native.terrainBuildCaptured(job.x, job.y, job.z, job.version, false, 0L, addresses, counts, opaque, opaqueCount);
	}

	/** Verify mode: set by {@link #difference} when the only differences were in the normal int, which nothing reads. */
	boolean normalOnly;

	/** Verify mode: what the last difference was (layer, field, block), to report each kind once. */
	String differenceKind;

	private static final String[] FIELDS = {"x", "y", "z", "u", "v", "colour", "normal", "light"};

	/**
	 * Verify mode: null when this worker's vertices match the game thread's in every int the native mesher reads
	 * (position, uv, colour, light), otherwise a description of the first difference. A captured vertex is 8 ints:
	 * x y z u v colour normal light. The Tesselator writes the normal only when normals are on, which terrain never
	 * turns on, so that int holds whatever an earlier draw left in the buffer and differs between Tesselators; the
	 * native mesher ignores it (appendCapturedQuad), so it is skipped here and only counted.
	 */
	String difference(int[][] expected, TerrainWorkers.Job job) {
		normalOnly = false;
		for (int layer = 0; layer < LAYERS; layer++) {
			int ints = (int) (written[layer] / 4L);
			int[] want = expected[layer];
			long base = sinks[layer];
			int common = Math.min(ints, want.length);
			for (int i = 0; i < common; i++) {
				int got = Memory.INSTANCE.getInt(base + i * 4L);
				if (got == want[i]) continue;
				if (i % 8 == 6) {
					normalOnly = true;
					continue;
				}
				return describe(layer, i, got, want[i], ints, want.length, base, job);
			}
			if (ints != want.length) {
				differenceKind = layer + "/count";
				return "layer " + layer + " has " + ints / 8 + " vertices on the worker, " + want.length / 8 + " on the game thread (the first "
					+ common / 8 + " agree)";
			}
		}
		return null;
	}

	private String describe(int layer, int i, int got, int want, int ints, int wantInts, long base, TerrainWorkers.Job job) {
		int vertex = i / 8, field = i % 8;
		String values = field <= 4
			? Float.intBitsToFloat(got) + " vs " + Float.intBitsToFloat(want)
			: String.format("0x%08X vs 0x%08X", got, want);
		// the block the quad belongs to: the quad's centre, positions are relative to the section's corner
		int quad = vertex / 4 * 4;
		float cx = 0, cy = 0, cz = 0;
		for (int k = 0; k < 4 && (quad + k) * 8 + 2 < ints; k++) {
			long at = base + (quad + k) * 32L;
			cx += Float.intBitsToFloat(Memory.INSTANCE.getInt(at)) / 4;
			cy += Float.intBitsToFloat(Memory.INSTANCE.getInt(at + 4)) / 4;
			cz += Float.intBitsToFloat(Memory.INSTANCE.getInt(at + 8)) / 4;
		}
		int bx = (job.x << 4) + (int) Math.floor(cx), by = (job.y << 4) + (int) Math.floor(cy), bz = (job.z << 4) + (int) Math.floor(cz);
		String block;
		try {
			Block at = job.snapshot.getBlock(bx, by, bz);
			block = "block " + Block.getId(at) + ":" + job.snapshot.getBlockMetadata(bx, by, bz) + " (" + at.getClass().getSimpleName() + ", render type " + at.getRenderType() + ")";
		} catch (RuntimeException outside) {
			block = "block outside the copy";
		}
		differenceKind = layer + "/" + FIELDS[field] + "/" + block;
		return "layer " + layer + " vertex " + vertex + " " + FIELDS[field] + ": worker " + values.replace(" vs ", ", game thread ")
			+ " at " + bx + " " + by + " " + bz + ", " + block + (ints != wantInts ? "; vertex counts " + ints / 8 + " / " + wantInts / 8 : "");
	}

	/** Same as SectionCapture.target: pass 0 is opaque cubes or alpha tested, pass 1 translucent. */
	private static int target(Block block, int pass) {
		if (pass == 1) return TerrainRenderer.LAYER_TRANSLUCENT;
		if (block.isOpaque() && block.getRenderType() == 0 && block != Blocks.GRASS) return TerrainRenderer.LAYER_SOLID;
		return TerrainRenderer.LAYER_CUTOUT;
	}

	private void select(Tesselator tesselator, int layer, int minX, int minY, int minZ) {
		if (layer == currentLayer) return;
		tesselator.end();
		currentLayer = layer;
		tesselator.begin();
		tesselator.offset(-minX, -minY, -minZ);
	}
}

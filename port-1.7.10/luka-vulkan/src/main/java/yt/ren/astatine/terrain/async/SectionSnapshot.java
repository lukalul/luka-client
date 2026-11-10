package yt.ren.astatine.terrain.async;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.PistonHeadBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.material.Material;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.WorldRegion;
import net.minecraft.world.WorldView;
import net.minecraft.world.biome.Biome;

/**
 * luka: a copy of what meshing one section reads from the world, taken on the game thread: blocks, metadata, raw
 * sky and block light and biomes for the section and a border of {@link #BORDER} blocks, and the block entities of
 * the section itself. As a WorldView it answers exactly like the WorldRegion the game thread would mesh from
 * (same clamping, same neighbour light rule). A read outside the copy throws {@link Outside}; the section is then
 * built on the game thread instead.
 */
public final class SectionSnapshot implements WorldView {
	public static final int BORDER = 2;
	private static final int SIZE = 16 + 2 * BORDER;
	private static final int VOLUME = SIZE * SIZE * SIZE;

	/** Thrown for a read outside the copied area; no stack trace, it is expected now and then. */
	public static final class Outside extends RuntimeException {
		static final Outside INSTANCE = new Outside();

		private Outside() {
			super("outside the section snapshot", null, false, false);
		}
	}

	private final Block[] blocks = new Block[VOLUME];
	private final byte[] metadata = new byte[VOLUME];
	private final byte[] skyLight = new byte[VOLUME];
	private final byte[] blockLight = new byte[VOLUME];
	private final Biome[] biomes = new Biome[SIZE * SIZE];
	private final Map<Long, BlockEntity> entities = new HashMap<>();
	private int originX, originY, originZ;
	private boolean noSky;

	/**
	 * Fills the copy for the section at section coordinates (x, y, z), adds the section's block entities that have a
	 * renderer to {@code rendered}, and returns false when the section has a block whose drawing changes shared state
	 * the copy cannot cover (stairs, piston heads): such a section is built on the game thread.
	 */
	boolean fill(World world, WorldRegion region, int x, int y, int z, List<BlockEntity> rendered) {
		int minX = x << 4, minY = y << 4, minZ = z << 4;
		originX = minX - BORDER;
		originY = minY - BORDER;
		originZ = minZ - BORDER;
		noSky = world.dimension.noSky;
		entities.clear();
		boolean workerSafe = true;
		int at = 0;
		for (int dy = 0; dy < SIZE; dy++) {
			int wy = originY + dy;
			for (int dz = 0; dz < SIZE; dz++) {
				int wz = originZ + dz;
				for (int dx = 0; dx < SIZE; dx++, at++) {
					int wx = originX + dx;
					Block block = region.getBlock(wx, wy, wz);
					blocks[at] = block;
					metadata[at] = (byte) region.getBlockMetadata(wx, wy, wz);
					skyLight[at] = (byte) region.getLight(LightType.SKY, wx, wy, wz);
					blockLight[at] = (byte) region.getLight(LightType.BLOCK, wx, wy, wz);
					boolean inside = dx >= BORDER && dx < BORDER + 16 && dy >= BORDER && dy < BORDER + 16 && dz >= BORDER && dz < BORDER + 16;
					if (inside && block.getMaterial() != Material.AIR) {
						if (block instanceof StairsBlock || block instanceof PistonHeadBlock) workerSafe = false;
						if (block.hasBlockEntity()) {
							// the same call the game thread's meshing makes; it may create the block entity
							BlockEntity entity = region.getBlockEntity(wx, wy, wz);
							entities.put(key(wx, wy, wz), entity);
							if (entity != null && BlockEntityRenderDispatcher.INSTANCE.hasRenderer(entity)) rendered.add(entity);
						}
					}
				}
			}
		}
		for (int dz = 0; dz < SIZE; dz++) {
			for (int dx = 0; dx < SIZE; dx++) biomes[dz * SIZE + dx] = region.getBiome(originX + dx, originZ + dz);
		}
		return workerSafe;
	}

	private static long key(int x, int y, int z) {
		return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | (y & 0xFFFL);
	}

	private int index(int x, int y, int z) {
		int dx = x - originX, dy = y - originY, dz = z - originZ;
		if (dx < 0 || dx >= SIZE || dy < 0 || dy >= SIZE || dz < 0 || dz >= SIZE) throw Outside.INSTANCE;
		return (dy * SIZE + dz) * SIZE + dx;
	}

	@Override
	public Block getBlock(int x, int y, int z) {
		if (y < 0 || y >= 256) return Blocks.AIR;
		return blocks[index(x, y, z)];
	}

	@Override
	public BlockEntity getBlockEntity(int x, int y, int z) {
		long key = key(x, y, z);
		BlockEntity entity = entities.get(key);
		if (entity != null || entities.containsKey(key)) return entity;
		// not copied: fine when the block has none, otherwise only the game thread can answer
		if (y < 0 || y >= 256 || !getBlock(x, y, z).hasBlockEntity()) return null;
		throw Outside.INSTANCE;
	}

	/** WorldRegion.getLight: y clamped, the stored light of that position. */
	private int light(LightType type, int x, int y, int z) {
		if (y < 0) y = 0;
		if (y >= 256) y = 255;
		int i = index(x, y, z);
		return type == LightType.SKY ? skyLight[i] : blockLight[i];
	}

	/** WorldRegion.getBrightness: y clamped, no sky light without a sky, the brightest neighbour for some blocks. */
	private int brightness(LightType type, int x, int y, int z) {
		if (y < 0) y = 0;
		if (y >= 256) y = 255;
		if (type == LightType.SKY && noSky) return 0;
		if (getBlock(x, y, z).usesNeighborLight()) {
			int best = light(type, x, y + 1, z);
			best = Math.max(best, light(type, x + 1, y, z));
			best = Math.max(best, light(type, x - 1, y, z));
			best = Math.max(best, light(type, x, y, z + 1));
			best = Math.max(best, light(type, x, y, z - 1));
			return best;
		}
		return light(type, x, y, z);
	}

	@Override
	public int getLightColor(int x, int y, int z, int emitted) {
		int sky = brightness(LightType.SKY, x, y, z);
		int block = brightness(LightType.BLOCK, x, y, z);
		if (block < emitted) block = emitted;
		return sky << 20 | block << 4;
	}

	@Override
	public int getBlockMetadata(int x, int y, int z) {
		if (y < 0 || y >= 256) return 0;
		return metadata[index(x, y, z)];
	}

	@Override
	public boolean isAir(int x, int y, int z) {
		return getBlock(x, y, z).getMaterial() == Material.AIR;
	}

	@Override
	public Biome getBiome(int x, int z) {
		int dx = x - originX, dz = z - originZ;
		if (dx < 0 || dx >= SIZE || dz < 0 || dz >= SIZE) throw Outside.INSTANCE;
		return biomes[dz * SIZE + dx];
	}

	@Override
	public int getHeight() {
		return 256;
	}

	@Override
	public boolean isEmpty() {
		return false;
	}

	@Override
	public int getDirectSignal(int x, int y, int z, int side) {
		return getBlock(x, y, z).getDirectSignal(this, x, y, z, side);
	}
}

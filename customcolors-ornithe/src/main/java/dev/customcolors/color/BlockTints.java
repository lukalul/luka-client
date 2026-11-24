package dev.customcolors.color;

import dev.customcolors.core.Palette;
import net.minecraft.block.Block;
import net.minecraft.block.BlockOldLeaf;
import net.minecraft.block.BlockPlanks;
import net.minecraft.block.BlockRedstoneWire;
import net.minecraft.block.BlockStem;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.biome.BiomeGenBase;

/**
 * Block tints from pack colormaps. These run on chunk-building threads for every block, so they only
 * read the prepared {@link ColorSet} and never allocate. Every method returns -1 for "keep vanilla".
 */
public final class BlockTints {
    private static final ThreadLocal<BlockPos.MutableBlockPos> SPOT = new ThreadLocal<BlockPos.MutableBlockPos>() {
        @Override
        protected BlockPos.MutableBlockPos initialValue() {
            return new BlockPos.MutableBlockPos();
        }
    };

    private BlockTints() {
    }

    /** Water: the colormap averaged over the 3x3 columns around the block, like vanilla's own smoothing. */
    public static int water(IBlockAccess world, BlockPos pos) {
        Palette p = ColorLoader.current().water;
        if (p == null) {
            return -1;
        }
        if (p.isSolid()) {
            return p.entry(0);
        }
        BlockPos.MutableBlockPos spot = SPOT.get();
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        int r = 0;
        int g = 0;
        int b = 0;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                spot.set(x + dx, y, z + dz);
                int c = climate(world.getBiomeGenForCoords(spot), spot, p);
                r += (c >> 16) & 255;
                g += (c >> 8) & 255;
                b += c & 255;
            }
        }
        return ((r / 9) << 16) | ((g / 9) << 8) | (b / 9);
    }

    /** One colormap sample at a position (no smoothing). */
    public static int at(IBlockAccess world, BlockPos pos, Palette p) {
        if (p.isSolid()) {
            return p.entry(0);
        }
        return climate(world.getBiomeGenForCoords(pos), pos, p);
    }

    static int climate(BiomeGenBase biome, BlockPos pos, Palette p) {
        if (biome == null) {
            return p.climate(0.5F, 0.5F);
        }
        return p.climate(biome.getFloatTemperature(pos), biome.getFloatRainfall());
    }

    public static int swampGrass(BiomeGenBase swamp, BlockPos pos) {
        Palette p = ColorLoader.current().swampGrass;
        return p == null ? -1 : climate(swamp, pos, p);
    }

    public static int swampFoliage(BiomeGenBase swamp, BlockPos pos) {
        Palette p = ColorLoader.current().swampFoliage;
        return p == null ? -1 : climate(swamp, pos, p);
    }

    /** Spruce and birch leaves; other leaves keep vanilla. */
    public static int leaves(IBlockAccess world, BlockPos pos, Block self) {
        ColorSet s = ColorLoader.current();
        if (s.pine == null && s.birch == null) {
            return -1;
        }
        IBlockState state = world.getBlockState(pos);
        if (state.getBlock() != self) {
            return -1;
        }
        BlockPlanks.EnumType type = state.getValue(BlockOldLeaf.VARIANT);
        Palette p = null;
        if (type == BlockPlanks.EnumType.SPRUCE) {
            p = s.pine;
        } else if (type == BlockPlanks.EnumType.BIRCH) {
            p = s.birch;
        }
        return p == null ? -1 : at(world, pos, p);
    }

    public static int redstone(IBlockAccess world, BlockPos pos, Block self) {
        int[] table = ColorLoader.current().redstone;
        if (table == null) {
            return -1;
        }
        IBlockState state = world.getBlockState(pos);
        if (state.getBlock() != self) {
            return -1;
        }
        int power = state.getValue(BlockRedstoneWire.POWER);
        return table[Math.max(0, Math.min(15, power))];
    }

    public static int stem(IBlockState state, Block crop) {
        ColorSet s = ColorLoader.current();
        int[] table = crop == Blocks.pumpkin ? s.pumpkinStem : s.melonStem;
        if (table == null || !(state.getBlock() instanceof BlockStem)) {
            return -1;
        }
        int age = state.getValue(BlockStem.AGE);
        return table[Math.max(0, Math.min(7, age))];
    }

    public static int lilyPad() {
        return ColorLoader.current().lilyPad;
    }
}

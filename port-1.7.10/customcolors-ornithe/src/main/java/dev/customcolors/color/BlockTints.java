package dev.customcolors.color;

import dev.customcolors.core.Palette;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.biome.BiomeGenBase;

/**
 * Block tints from pack colormaps. These run for every block while chunks are built, so they only
 * read the prepared {@link ColorSet} and never allocate. Every method returns -1 for "keep vanilla".
 */
public final class BlockTints {
    private BlockTints() {
    }

    /** Water: the colormap averaged over the 3x3 columns around the block, like vanilla's own smoothing. */
    public static int water(IBlockAccess world, int x, int y, int z) {
        Palette p = ColorLoader.current().water;
        if (p == null) {
            return -1;
        }
        if (p.isSolid()) {
            return p.entry(0);
        }
        int r = 0;
        int g = 0;
        int b = 0;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int c = climate(world.getBiomeGenForCoords(x + dx, z + dz), x + dx, y, z + dz, p);
                r += (c >> 16) & 255;
                g += (c >> 8) & 255;
                b += c & 255;
            }
        }
        return ((r / 9) << 16) | ((g / 9) << 8) | (b / 9);
    }

    /** One colormap sample at a position (no smoothing). */
    public static int at(IBlockAccess world, int x, int y, int z, Palette p) {
        if (p.isSolid()) {
            return p.entry(0);
        }
        return climate(world.getBiomeGenForCoords(x, z), x, y, z, p);
    }

    static int climate(BiomeGenBase biome, int x, int y, int z, Palette p) {
        if (biome == null) {
            return p.climate(0.5F, 0.5F);
        }
        return p.climate(biome.getFloatTemperature(x, y, z), biome.getFloatRainfall());
    }

    public static int swampGrass(BiomeGenBase swamp, int x, int y, int z) {
        Palette p = ColorLoader.current().swampGrass;
        return p == null ? -1 : climate(swamp, x, y, z, p);
    }

    public static int swampFoliage(BiomeGenBase swamp, int x, int y, int z) {
        Palette p = ColorLoader.current().swampFoliage;
        return p == null ? -1 : climate(swamp, x, y, z, p);
    }

    /** Spruce (metadata type 1) and birch (type 2) leaves; other leaves keep vanilla. */
    public static int leaves(IBlockAccess world, int x, int y, int z) {
        ColorSet s = ColorLoader.current();
        if (s.pine == null && s.birch == null) {
            return -1;
        }
        int type = world.getBlockMetadata(x, y, z) & 3;
        Palette p = null;
        if (type == 1) {
            p = s.pine;
        } else if (type == 2) {
            p = s.birch;
        }
        return p == null ? -1 : at(world, x, y, z, p);
    }

    /** Wire tint for a power level (block metadata 0..15). */
    public static int redstone(int power) {
        int[] table = ColorLoader.current().redstone;
        if (table == null) {
            return -1;
        }
        return table[Math.max(0, Math.min(15, power))];
    }

    /** Stem colour for a growth stage (metadata 0..7). */
    public static int stem(int age, Block crop) {
        ColorSet s = ColorLoader.current();
        int[] table = crop == Blocks.pumpkin ? s.pumpkinStem : s.melonStem;
        if (table == null) {
            return -1;
        }
        return table[Math.max(0, Math.min(7, age))];
    }

    public static int lilyPad() {
        return ColorLoader.current().lilyPad;
    }
}

package dev.customcolors.color;

import dev.customcolors.core.Glide;
import dev.customcolors.core.Palette;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/**
 * Sky, distance-fog, underwater and underlava colours sampled at the camera. Each glides to a new value
 * over a few seconds so biome borders do not snap. Render thread only.
 */
public final class SkyTints {
    private static final Glide SKY = new Glide();
    private static final Glide FOG = new Glide();
    private static final Glide WATER = new Glide();
    private static final Glide LAVA = new Glide();
    private static final BlockPos.MutableBlockPos SPOT = new BlockPos.MutableBlockPos();

    /** Result of {@link #fluidFog}, 0..1 per channel. */
    public static float fogRed;
    public static float fogGreen;
    public static float fogBlue;

    private SkyTints() {
    }

    static void reset() {
        SKY.reset();
        FOG.reset();
        WATER.reset();
        LAVA.reset();
    }

    /** Base sky colour in the Overworld (vanilla adds night, sunsets and weather on top). */
    public static int sky(World world, Entity viewer, int vanilla) {
        Palette p = ColorLoader.current().sky;
        if (p == null || world == null || viewer == null || world.provider.getDimensionId() != 0) {
            return vanilla;
        }
        return SKY.toward(sample(world, viewer, p), System.nanoTime());
    }

    /** Overworld distance fog: the pack colour shaped by vanilla's time-of-day curve; null keeps vanilla. */
    public static Vec3 fog(World world, float celestialAngle) {
        Palette p = ColorLoader.current().fog;
        if (p == null || world == null || world.provider.getDimensionId() != 0) {
            return null;
        }
        Entity viewer = Minecraft.getMinecraft().getRenderViewEntity();
        if (viewer == null) {
            return null;
        }
        int c = FOG.toward(sample(world, viewer, p), System.nanoTime());
        float day = MathHelper.cos(celestialAngle * (float) Math.PI * 2.0F) * 2.0F + 0.5F;
        day = MathHelper.clamp_float(day, 0.0F, 1.0F);
        float r = ((c >> 16) & 255) / 255.0F * (day * 0.94F + 0.06F);
        float g = ((c >> 8) & 255) / 255.0F * (day * 0.94F + 0.06F);
        float b = (c & 255) / 255.0F * (day * 0.91F + 0.09F);
        return new Vec3(r, g, b);
    }

    /**
     * Underwater/underlava colour when the camera is inside such a fluid and the pack has one; fills
     * {@link #fogRed}/{@link #fogGreen}/{@link #fogBlue} and returns true.
     */
    public static boolean fluidFog(World world, Entity viewer, float partialTicks) {
        ColorSet s = ColorLoader.current();
        if ((s.underwater == null && s.underlava == null) || world == null || viewer == null) {
            return false;
        }
        Block block = ActiveRenderInfo.getBlockAtEntityViewpoint(world, viewer, partialTicks);
        Material m = block.getMaterial();
        if (m == Material.water && s.underwater != null) {
            int c = WATER.toward(sample(world, viewer, s.underwater), System.nanoTime());
            float boost = EnchantmentHelper.getRespiration(viewer) * 0.2F;
            store(c, boost);
            return true;
        }
        if (m == Material.lava && s.underlava != null) {
            store(LAVA.toward(sample(world, viewer, s.underlava), System.nanoTime()), 0.0F);
            return true;
        }
        return false;
    }

    private static void store(int c, float boost) {
        fogRed = ((c >> 16) & 255) / 255.0F + boost;
        fogGreen = ((c >> 8) & 255) / 255.0F + boost;
        fogBlue = (c & 255) / 255.0F + boost;
    }

    private static int sample(World world, Entity viewer, Palette p) {
        if (p.isSolid()) {
            return p.entry(0);
        }
        SPOT.set(MathHelper.floor_double(viewer.posX), MathHelper.floor_double(viewer.posY),
            MathHelper.floor_double(viewer.posZ));
        return BlockTints.climate(world.getBiomeGenForCoords(SPOT), SPOT, p);
    }
}

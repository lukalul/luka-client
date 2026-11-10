package dev.hcfopt;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityBanner;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityEnderChest;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.tileentity.TileEntityHopper;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.tileentity.TileEntitySkull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Allocation-light decisions shared by mixins. All public methods fail open. */
public final class HcfOptHooks {
    private static int clientTick;
    private static int farParticleSpawns;
    private static volatile int[] admittedItemIds = new int[0];
    /** True while there are no more item entities than the render cap, so no per-frame sorting is needed. */
    private static volatile boolean admitAllItems = true;

    private static double[] scratchDist = new double[0];
    private static double[] scratchSorted = new double[0];

    private HcfOptHooks() { }

    public static void onTickStart() {
        try {
            clientTick++;
            farParticleSpawns = 0;
            BenchmarkController.onTickStart();
        } catch (Throwable t) {
            HcfOptMod.problemOnce("tick-start", t);
        }
    }

    public static void onTickEnd(long elapsedNanos) {
        try {
            BenchmarkController.onTickEnd(elapsedNanos);
        } catch (Throwable t) {
            HcfOptMod.problemOnce("tick-end", t);
        }
    }

    public static void onFrameStart() {
        try {
            prepareItemAdmission();
            RenderCounters.beginFrame();
            BenchmarkController.onFrameStart();
            LightQueue.drain();
        } catch (Throwable t) {
            admittedItemIds = new int[0];
            HcfOptMod.problemOnce("frame-start", t);
        }
    }

    public static void onFrameEnd(long elapsedNanos) {
        try {
            BenchmarkController.onFrameEnd(elapsedNanos, RenderCounters.entities(), RenderCounters.tiles());
        } catch (Throwable t) {
            HcfOptMod.problemOnce("frame-end", t);
        }
    }

    public static boolean skipItemTick(EntityItem item) {
        return skipEntityTick(item, HcfOptMod.config().itemTickNearDistance);
    }

    public static boolean skipOrbTick(EntityXPOrb orb) {
        return skipEntityTick(orb, HcfOptMod.config().orbTickNearDistance);
    }

    public static boolean skipMobMovement(EntityLivingBase entity) {
        HcfOptConfig c = HcfOptMod.config();
        if (!active(c.entityTickThrottling) || entity instanceof EntityPlayer || unsafeEntity(entity)) return false;
        Entity camera = Minecraft.getMinecraft().getRenderViewEntity();
        if (camera == null || entity.worldObj == null || !entity.worldObj.isRemote) return false;
        double d2 = squared(entity, camera);
        double safe = Math.max(c.attackSafetyDistance, c.mobTickNearDistance);
        if (d2 <= safe * safe) return false;
        return !ThrottlePolicy.shouldRun(entity.getEntityId(), clientTick, d2, c.mobTickNearDistance);
    }

    private static boolean skipEntityTick(Entity entity, double near) {
        try {
            HcfOptConfig c = HcfOptMod.config();
            if (!active(c.entityTickThrottling) || unsafeEntity(entity) || entity.worldObj == null || !entity.worldObj.isRemote) return false;
            Entity camera = Minecraft.getMinecraft().getRenderViewEntity();
            if (camera == null) return false;
            double d2 = squared(entity, camera);
            double safe = Math.max(c.attackSafetyDistance, near);
            if (d2 <= safe * safe) return false;
            return !ThrottlePolicy.shouldRun(entity.getEntityId(), clientTick, d2, near);
        } catch (Throwable t) {
            HcfOptMod.problemOnce("entity-throttle", t);
            return false;
        }
    }

    private static boolean unsafeEntity(Entity entity) {
        Minecraft mc = Minecraft.getMinecraft();
        return entity == mc.thePlayer || entity == mc.getRenderViewEntity() || entity.ridingEntity != null || entity.riddenByEntity != null;
    }

    public static boolean skipTileTick(TileEntity tile) {
        try {
            HcfOptConfig c = HcfOptMod.config();
            if (!active(c.tileTickOptimization) || tile.getWorld() == null || !tile.getWorld().isRemote) return false;
            if (tile instanceof TileEntityHopper) return true;
            if (tile instanceof TileEntityFurnace) return farFromTile(tile, 8.0);
            if (tile instanceof TileEntityChest || tile instanceof TileEntityEnderChest) {
                if (!farFromTile(tile, c.chestTickFullDistance)) return false;
                return Math.floorMod(tile.getPos().hashCode() + clientTick, c.distantChestTickInterval) != 0;
            }
            return false;
        } catch (Throwable t) {
            HcfOptMod.problemOnce("tile-tick", t);
            return false;
        }
    }

    public static boolean skipTileRender(TileEntity tile) {
        try {
            HcfOptConfig c = HcfOptMod.config();
            if (!active(c.tileRenderDistance)) return false;
            double distance = -1.0;
            if (tile instanceof TileEntitySign) distance = c.signRenderDistance;
            else if (tile instanceof TileEntityChest || tile instanceof TileEntityEnderChest) distance = c.chestRenderDistance;
            else if (tile instanceof TileEntitySkull) distance = c.skullRenderDistance;
            else if (tile instanceof TileEntityBanner) distance = c.bannerRenderDistance;
            return distance >= 0.0 && farFromTile(tile, distance);
        } catch (Throwable t) {
            HcfOptMod.problemOnce("tile-render-distance", t);
            return false;
        }
    }

    public static boolean allowItemRender(EntityItem item) {
        try {
            HcfOptConfig c = HcfOptMod.config();
            if (!active(c.itemRenderCap) || admitAllItems) return true;
            Entity camera = Minecraft.getMinecraft().getRenderViewEntity();
            if (camera != null && squared(item, camera) <= c.itemRenderNearExemptionDistance * c.itemRenderNearExemptionDistance) return true;
            return Arrays.binarySearch(admittedItemIds, item.getEntityId()) >= 0;
        } catch (Throwable t) {
            HcfOptMod.problemOnce("item-render-cap", t);
            return true;
        }
    }

    private static int liveParticles(List<EntityFX>[][] layers) {
        int live = 0;
        if (layers != null) {
            for (List<EntityFX>[] layer : layers) if (layer != null) for (List<EntityFX> list : layer) if (list != null) live += list.size();
        }
        return live;
    }

    /** Counts live particles only when the cheap distance checks cannot decide, instead of on every spawn. */
    public static boolean allowParticle(EntityFX particle, List<EntityFX>[][] layers) {
        try {
            HcfOptConfig c = HcfOptMod.config();
            if (!active(c.particleCaps)) return true;
            Entity camera = Minecraft.getMinecraft().getRenderViewEntity();
            if (camera == null) return true;
            double d2 = squared(particle, camera);
            if (d2 <= c.particleNearExemptionDistance * c.particleNearExemptionDistance) return true;
            if (d2 > c.particleMaxDistance * c.particleMaxDistance) return false;
            if (liveParticles(layers) >= c.maxFarParticles || farParticleSpawns >= c.maxFarParticleSpawnsPerTick) return false;
            farParticleSpawns++;
            return true;
        } catch (Throwable t) {
            HcfOptMod.problemOnce("particle-cap", t);
            return true;
        }
    }

    private static boolean active(boolean toggle) {
        return HcfOptMod.config().masterEnabled && toggle;
    }

    private static double squared(Entity a, Entity b) {
        double x = a.posX - b.posX, y = a.posY - b.posY, z = a.posZ - b.posZ;
        return x * x + y * y + z * z;
    }

    private static boolean farFromTile(TileEntity tile, double distance) {
        Entity camera = Minecraft.getMinecraft().getRenderViewEntity();
        if (camera == null) return false;
        double x = tile.getPos().getX() + 0.5 - camera.posX;
        double y = tile.getPos().getY() + 0.5 - camera.posY;
        double z = tile.getPos().getZ() + 0.5 - camera.posZ;
        return x * x + y * y + z * z > distance * distance;
    }

    private static void prepareItemAdmission() {
        HcfOptConfig c = HcfOptMod.config();
        Minecraft mc = Minecraft.getMinecraft();
        if (!active(c.itemRenderCap) || mc.theWorld == null || mc.getRenderViewEntity() == null) {
            admittedItemIds = new int[0];
            admitAllItems = true;
            return;
        }
        final Entity camera = mc.getRenderViewEntity();
        List<Entity> all = mc.theWorld.loadedEntityList;
        int total = 0;
        for (int i = 0, n = all.size(); i < n; i++) if (all.get(i) instanceof EntityItem) total++;
        if (total <= c.maxRenderedItems) {
            admittedItemIds = new int[0];
            admitAllItems = true;
            return;
        }
        admitAllItems = false;
        // Primitive-only selection of the nearest `limit` items (same result as a stable distance sort: ties keep list order).
        if (scratchDist.length < total) { scratchDist = new double[total + 64]; scratchSorted = new double[total + 64]; }
        final double[] dist = scratchDist;
        final double[] sorted = scratchSorted;
        int k = 0;
        for (int i = 0, n = all.size(); i < n; i++) {
            Entity e = all.get(i);
            if (e instanceof EntityItem) { dist[k] = sorted[k] = squared(e, camera); k++; }
        }
        int limit = Math.min(c.maxRenderedItems, k);
        int[] ids = new int[limit];
        if (limit > 0) {
            Arrays.sort(sorted, 0, k);
            double threshold = sorted[limit - 1];
            int below = 0;
            for (int i = 0; i < k; i++) if (dist[i] < threshold) below++;
            int ties = limit - below;
            int out = 0, idx = 0;
            for (int i = 0, n = all.size(); i < n; i++) {
                Entity e = all.get(i);
                if (!(e instanceof EntityItem)) continue;
                double d = dist[idx++];
                if (d < threshold || (d == threshold && ties-- > 0)) ids[out++] = e.getEntityId();
            }
            Arrays.sort(ids, 0, out);
            if (out != limit) ids = Arrays.copyOf(ids, out);
        }
        admittedItemIds = ids;
    }
}

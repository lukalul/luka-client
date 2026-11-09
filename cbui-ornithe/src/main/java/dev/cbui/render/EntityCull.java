package dev.cbui.render;

import dev.cbui.Cbui;
import dev.cbui.config.GlobalOptions;
import dev.cbui.feature.hideblocks.HideBlocksModule;
import dev.cbui.feature.staff.XRayState;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLeavesBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

/**
 * Finds living entities with solid blocks fully between them and the camera, so their bodies can be skipped. Names still draw.
 * Answers are about a tick old, so each box is tested larger than it is: nobody appears late when stepping out from cover.
 */
public final class EntityCull {
  /** Implemented on every entity by a mixin. */
  public interface Flag {
    boolean cbui$culled();

    void cbui$culled(boolean culled);

    /** True when the entity has moved more than {@code limit} on any axis since it was last checked. */
    boolean cbui$movedSinceCheck(double limit);
  }

  private static final double ALWAYS_DRAW_WITHIN_SQ = 6 * 6;
  private static final double RENDER_LIMIT_SQ = 128 * 128;
  private static final double MARGIN = 1.0;
  private static final int NX = 5, NY = 6, NZ = 5;
  /** Loot piles can be hundreds of entities, so each item gets a coarser test, every other tick. */
  private static final int LOOT_GRID = 3;
  /** A pearl or a teleport outruns the margin: whoever jumped this far since the last check is drawn straight away. */
  private static final double JUMP = 1.0;

  /** False whenever something makes hidden entities matter (third person, X-Ray, the option being off). */
  public static boolean active;

  private static EntityPlayerSP checkedFor;
  private static double checkedX, checkedY, checkedZ;
  private static boolean oddTick;
  private static World world;
  private static Chunk chunk;
  private static int chunkX, chunkZ;

  private EntityCull() {}

  /** Whether this entity's body can be skipped this frame. */
  public static boolean skip(Entity entity) {
    if (!active) return false;
    Flag flag = (Flag) entity;
    if (!flag.cbui$culled() || flag.cbui$movedSinceCheck(JUMP)) return false;
    EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
    return player == checkedFor && Math.abs(player.posX - checkedX) <= JUMP
        && Math.abs(player.posY - checkedY) <= JUMP && Math.abs(player.posZ - checkedZ) <= JUMP;
  }

  /** Once per client tick. */
  public static void tick() {
    try {
      run();
    } catch (Throwable error) {
      active = false;
      Cbui.reportOnce("entity culling", error);
    } finally {
      world = null;
      chunk = null;
    }
  }

  private static void run() {
    Minecraft mc = Minecraft.getMinecraft();
    EntityPlayerSP player = mc.thePlayer;
    active = GlobalOptions.entityCulling && mc.theWorld != null && player != null
        && mc.getRenderViewEntity() == player && mc.gameSettings.thirdPersonView == 0 && !XRayState.active
        && !player.isSpectator() && !player.noClip;
    if (!active) return;
    world = mc.theWorld;
    checkedFor = player;
    checkedX = player.posX;
    checkedY = player.posY;
    checkedZ = player.posZ;
    double eyeX = player.posX, eyeY = player.posY + player.getEyeHeight(), eyeZ = player.posZ;
    // with the head inside a block the faces around it are not drawn, so that block hides nothing
    if (solid(MathHelper.floor_double(eyeX), MathHelper.floor_double(eyeY), MathHelper.floor_double(eyeZ))) {
      active = false;
      return;
    }
    oddTick = !oddTick;
    List<Entity> entities = world.loadedEntityList;
    for (int i = 0, n = entities.size(); i < n; i++) {
      Entity entity = entities.get(i);
      if (entity == player) continue;
      if (entity instanceof EntityLivingBase) {
        ((Flag) entity).cbui$culled(hidden(entity, eyeX, eyeY, eyeZ, NX, NY, NZ));
      } else if ((entity instanceof EntityItem || entity instanceof EntityXPOrb) && (entity.getEntityId() & 1) == (oddTick ? 1 : 0)) {
        ((Flag) entity).cbui$culled(hidden(entity, eyeX, eyeY, eyeZ, LOOT_GRID, LOOT_GRID, LOOT_GRID));
      }
    }
  }

  private static boolean hidden(Entity entity, double eyeX, double eyeY, double eyeZ, int nx, int ny, int nz) {
    double distance = entity.getDistanceSq(eyeX, eyeY, eyeZ);
    if (distance < ALWAYS_DRAW_WITHIN_SQ || distance > RENDER_LIMIT_SQ) return false;
    AxisAlignedBB box = entity.getEntityBoundingBox();
    double moved = Math.abs(entity.posX - entity.prevPosX) + Math.abs(entity.posY - entity.prevPosY) + Math.abs(entity.posZ - entity.prevPosZ);
    double grow = MARGIN + moved * 2;
    double minX = box.minX - grow, minY = box.minY - grow, minZ = box.minZ - grow;
    double spanX = box.maxX - box.minX + grow * 2, spanY = box.maxY - box.minY + grow * 2, spanZ = box.maxZ - box.minZ + grow * 2;
    if (clear(eyeX, eyeY, eyeZ, minX + spanX / 2, minY + spanY / 2, minZ + spanZ / 2)) return false;
    // only the surface of the box: a ray that reaches inside it must have crossed the surface
    for (int ix = 0; ix < nx; ix++) {
      double x = minX + spanX * ix / (nx - 1);
      for (int iy = 0; iy < ny; iy++) {
        double y = minY + spanY * iy / (ny - 1);
        for (int iz = 0; iz < nz; iz++) {
          if (ix > 0 && ix < nx - 1 && iy > 0 && iy < ny - 1 && iz > 0 && iz < nz - 1) continue;
          if (clear(eyeX, eyeY, eyeZ, x, y, minZ + spanZ * iz / (nz - 1))) return false;
        }
      }
    }
    return true;
  }

  /** Walks the block grid along the ray. The camera's own block and the end point's block never count as cover. */
  private static boolean clear(double fromX, double fromY, double fromZ, double toX, double toY, double toZ) {
    int x = MathHelper.floor_double(fromX), y = MathHelper.floor_double(fromY), z = MathHelper.floor_double(fromZ);
    int endX = MathHelper.floor_double(toX), endY = MathHelper.floor_double(toY), endZ = MathHelper.floor_double(toZ);
    double dx = toX - fromX, dy = toY - fromY, dz = toZ - fromZ;
    int stepX = dx > 0 ? 1 : -1, stepY = dy > 0 ? 1 : -1, stepZ = dz > 0 ? 1 : -1;
    double deltaX = dx == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dx);
    double deltaY = dy == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dy);
    double deltaZ = dz == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dz);
    double nextX = dx == 0 ? Double.POSITIVE_INFINITY : (dx > 0 ? x + 1 - fromX : fromX - x) * deltaX;
    double nextY = dy == 0 ? Double.POSITIVE_INFINITY : (dy > 0 ? y + 1 - fromY : fromY - y) * deltaY;
    double nextZ = dz == 0 ? Double.POSITIVE_INFINITY : (dz > 0 ? z + 1 - fromZ : fromZ - z) * deltaZ;
    int steps = Math.abs(endX - x) + Math.abs(endY - y) + Math.abs(endZ - z);
    for (int i = 0; i < steps; i++) {
      if (nextX <= nextY && nextX <= nextZ) {
        x += stepX;
        nextX += deltaX;
      } else if (nextY <= nextZ) {
        y += stepY;
        nextY += deltaY;
      } else {
        z += stepZ;
        nextZ += deltaZ;
      }
      if (i < steps - 1 && solid(x, y, z)) return false;
    }
    return true;
  }

  private static boolean solid(int x, int y, int z) {
    if (y < 0 || y > 255) return false;
    int cx = x >> 4, cz = z >> 4;
    if (chunk == null || cx != chunkX || cz != chunkZ) {
      chunk = world.getChunkFromChunkCoords(cx, cz);
      chunkX = cx;
      chunkZ = cz;
    }
    Block block = chunk.getBlock(x, y, z);
    // leaves are see-through on Fancy and can be hidden; blocks the player chose to hide are not cover
    return block.isOpaqueCube() && !(block instanceof BlockLeavesBase) && !HideBlocksModule.hides(block);
  }
}

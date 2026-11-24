package dev.cbui.render;

import dev.cbui.feature.hideblocks.HideBlocksModule;
import dev.cbui.Cbui;
import dev.cbui.config.GlobalOptions;
import dev.cbui.feature.staff.XRayState;
import java.util.Arrays;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLeavesBase;
import net.minecraft.block.state.IBlockState;
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
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

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
  private static Chunk chunk, farChunk;
  private static int chunkX, chunkZ, farChunkX, farChunkZ;
  /** Per block state id: 0 until the state turns up in this tick's walk (hidden blocks can change), then COVER or OPEN. */
  private static final byte[] coverByState = new byte[65536];
  private static final byte COVER = 1, OPEN = 2;
  /** Which end of the ray found the last cover and after how many steps, see {@link #clear}. */
  private static int hint;
  private static final int LEAD_LIMIT = 12;

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
      farChunk = null;
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
    Arrays.fill(coverByState, (byte) 0);
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

  /**
   * Walks the block grid along the ray. The camera's own block and the end point's block never count as cover.
   * The walk comes from both ends and stops at the first cover either side meets. Most hidden entities are hidden by
   * blocks right next to them (a cave wall) or right next to the camera (the player's own walls); walking from the
   * camera only would cross all the open air in between, for every ray of every mob in the caves below.
   * The side that found the cover for the last ray goes first for the next one, as far as it had to go then.
   */
  private static boolean clear(double fromX, double fromY, double fromZ, double toX, double toY, double toZ) {
    int x = MathHelper.floor_double(fromX), y = MathHelper.floor_double(fromY), z = MathHelper.floor_double(fromZ);
    int farX = MathHelper.floor_double(toX), farY = MathHelper.floor_double(toY), farZ = MathHelper.floor_double(toZ);
    // blocks strictly between the two end blocks
    int left = Math.abs(farX - x) + Math.abs(farY - y) + Math.abs(farZ - z) - 1;
    if (left <= 0) return true;
    double dx = toX - fromX, dy = toY - fromY, dz = toZ - fromZ;
    int stepX = dx > 0 ? 1 : -1, stepY = dy > 0 ? 1 : -1, stepZ = dz > 0 ? 1 : -1;
    double deltaX = dx == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dx);
    double deltaY = dy == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dy);
    double deltaZ = dz == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dz);
    double nextX = dx == 0 ? Double.POSITIVE_INFINITY : (dx > 0 ? x + 1 - fromX : fromX - x) * deltaX;
    double nextY = dy == 0 ? Double.POSITIVE_INFINITY : (dy > 0 ? y + 1 - fromY : fromY - y) * deltaY;
    double nextZ = dz == 0 ? Double.POSITIVE_INFINITY : (dz > 0 ? z + 1 - fromZ : fromZ - z) * deltaZ;
    // the same ray walked backwards from the end point
    double farNextX = dx == 0 ? Double.POSITIVE_INFINITY : (dx > 0 ? toX - farX : farX + 1 - toX) * deltaX;
    double farNextY = dy == 0 ? Double.POSITIVE_INFINITY : (dy > 0 ? toY - farY : farY + 1 - toY) * deltaY;
    double farNextZ = dz == 0 ? Double.POSITIVE_INFINITY : (dz > 0 ? toZ - farZ : farZ + 1 - toZ) * deltaZ;
    // lead > 0: that many steps from the entity's end alone before the camera's end joins in; lead < 0: the other way round
    int lead = hint, farSteps = 0, nearSteps = 0;
    while (true) {
      if (lead >= 0) {
        // ties in the reverse order of the camera's end (there x, then y, then z), so both ends walk the same blocks
        if (farNextZ <= farNextY && farNextZ <= farNextX) {
          farZ -= stepZ;
          farNextZ += deltaZ;
        } else if (farNextY <= farNextX) {
          farY -= stepY;
          farNextY += deltaY;
        } else {
          farX -= stepX;
          farNextX += deltaX;
        }
        farSteps++;
        if (solidFar(farX, farY, farZ)) {
          hint = Math.min(farSteps - 1, LEAD_LIMIT);
          return false;
        }
        if (--left == 0) return true;
      }
      if (lead <= 0) {
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
        nearSteps++;
        if (solid(x, y, z)) {
          hint = -Math.min(nearSteps, LEAD_LIMIT);
          return false;
        }
        if (--left == 0) return true;
      }
      if (lead > 0) lead--;
      else if (lead < 0) lead++;
    }
  }

  /** Block lookups at the camera's end of a ray. Each end keeps its own chunk, or the two would evict each other every step. */
  private static boolean solid(int x, int y, int z) {
    if (y < 0 || y > 255) return false;
    int cx = x >> 4, cz = z >> 4;
    if (chunk == null || cx != chunkX || cz != chunkZ) {
      chunk = world.getChunkFromChunkCoords(cx, cz);
      chunkX = cx;
      chunkZ = cz;
    }
    return cover(chunk, x & 15, y, z & 15);
  }

  /** Block lookups at the entity's end of a ray. */
  private static boolean solidFar(int x, int y, int z) {
    if (y < 0 || y > 255) return false;
    int cx = x >> 4, cz = z >> 4;
    if (farChunk == null || cx != farChunkX || cz != farChunkZ) {
      farChunk = world.getChunkFromChunkCoords(cx, cz);
      farChunkX = cx;
      farChunkZ = cz;
    }
    return cover(farChunk, x & 15, y, z & 15);
  }

  /** The block at chunk-local x and z. Reads the state id straight from the chunk section and judges each state once per tick. */
  private static boolean cover(Chunk chunk, int x, int y, int z) {
    ExtendedBlockStorage section = chunk.getBlockStorageArray()[y >> 4];
    if (section == null) return false;
    // the same lookup as ExtendedBlockStorage.get
    int id = section.getData()[(y & 15) << 8 | z << 4 | x];
    byte known = coverByState[id];
    if (known == 0) coverByState[id] = known = cover(Block.BLOCK_STATE_IDS.getByValue(id)) ? COVER : OPEN;
    return known == COVER;
  }

  private static boolean cover(IBlockState state) {
    // an id without a state reads as air
    if (state == null) return false;
    Block block = state.getBlock();
    // leaves are see-through on Fancy and can be hidden; blocks the player chose to hide are not cover
    return block.isOpaqueCube() && !(block instanceof BlockLeavesBase) && !HideBlocksModule.hides(block);
  }
}

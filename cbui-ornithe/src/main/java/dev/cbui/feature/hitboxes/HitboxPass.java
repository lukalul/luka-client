package dev.cbui.feature.hitboxes;

import dev.cbui.Cbui;
import dev.cbui.mixin.EntityArrowAccessor;
import dev.cbui.mixin.HitboxesFovInvoker;
import java.nio.FloatBuffer;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityWitherSkull;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/**
 * Draws every hitbox of a frame in one batch, after the world's entities. Lines are flat strips that face the camera
 * and are sized in screen pixels, so they look the same with any renderer, field of view or zoom.
 *
 * <p>All positions are relative to the render origin (the interpolated viewer position) that entities are drawn
 * against. The real camera position and facing come from the current modelview matrix, which already holds view
 * bobbing and the third-person offset.
 */
public final class HitboxPass {
  private static final int QUADS = 7;
  /** Base strip width in screen pixels for a width setting of 1. */
  private static final float PIXELS_PER_WIDTH = 2.5F;
  /** Beyond this distance lines start to thin out (with "Shrink lines far away"). */
  private static final double FULL_WIDTH_RANGE = 4.0;
  /** World length of one dash step per unit of the dash setting. */
  private static final double DASH_UNIT = 0.05;
  /** Dashes never get shorter than this on screen. */
  private static final double MIN_DASH_PIXELS = 4.0;
  private static final double NEAR = 0.05;
  private static final double LOOK_LENGTH = 2.0;
  private static final double MAX_BOX = 16.0;

  private static final HitboxPass INSTANCE = new HitboxPass();
  private static volatile boolean broken;

  private final Minecraft mc = Minecraft.getMinecraft();
  private final FloatBuffer matrix = BufferUtils.createFloatBuffer(16);

  // camera, relative to the render origin
  private double camX, camY, camZ;
  private double fwdX, fwdY, fwdZ;
  /** World size of one screen pixel at a depth of 1 block. */
  private double pixelAtOne;
  private boolean shrink;

  private WorldRenderer buffer;
  private boolean drawing;
  // GL state found before drawing, to put back afterwards
  private boolean hadBlend, hadTexture, hadLighting, hadCull, hadAlpha, hadFog, hadDepth;

  private HitboxPass() {}

  /** Called once per frame after entities and block entities are drawn. */
  public static void run(ICamera camera, float partialTicks) {
    if (broken) return;
    HitboxesModule module = HitboxesModule.active();
    if (module == null) return;
    try {
      INSTANCE.frame(module, camera, partialTicks);
    } catch (Throwable error) {
      broken = true;
      Cbui.reportOnce("hitboxes", error);
      try {
        INSTANCE.abort();
      } catch (Throwable ignored) {
        // already reported
      }
    }
  }

  private void frame(HitboxesModule module, ICamera camera, float pt) {
    EntityPlayerSP you = mc.thePlayer;
    World world = mc.theWorld;
    if (you == null || world == null || camera == null) return;
    if (module.hideWithHud.get() && mc.gameSettings.hideGUI) return;
    if (you.hasReducedDebug() || mc.gameSettings.reducedDebugInfo) return;
    boolean firstPerson = mc.gameSettings.thirdPersonView == 0;
    List<Entity> entities = world.loadedEntityList;
    if (firstPerson && entities.size() <= 1) return;
    if (!module.anythingToDraw()) return;

    RenderManager manager = mc.getRenderManager();
    double ox = manager.viewerPosX, oy = manager.viewerPosY, oz = manager.viewerPosZ;
    sampleView();
    float fov = ((HitboxesFovInvoker) mc.entityRenderer).cbui$hitboxFov(pt, true);
    pixelAtOne = 2.0 * Math.tan(Math.toRadians(fov) / 2.0) / Math.max(1, mc.displayHeight);
    shrink = module.shrinkFar.get();
    boolean toggled = module.toggled.get();
    Entity view = mc.getRenderViewEntity();
    Entity pointed = mc.pointedEntity;
    Entity mount = you.ridingEntity;
    HitboxGroup arrowRules = module.groups[HitboxesModule.ARROWS];

    for (int i = 0, n = entities.size(); i < n; i++) {
      Entity e = entities.get(i);
      if (e == null || e.isDead) continue;
      int category = HitboxesModule.categoryOf(e, you);
      String mode = module.visibilityOf(category).visibility.get();
      if (HitboxGroup.NEVER.equals(mode)) continue;
      if (HitboxGroup.WHEN_TOGGLED.equals(mode) && !toggled) continue;
      if (HitboxGroup.WHEN_AIMED.equals(mode) && e != pointed) continue;
      if (category == HitboxesModule.ARROWS && !arrowRules.stuckArrows.get()
          && e instanceof EntityArrow && ((EntityArrowAccessor) e).cbui$inGround()) continue;
      HitboxGroup look = module.lookOf(category);
      if (!look.anyPart()) continue;
      if (e == view && firstPerson) continue;
      if (e.isInvisible() || e.isInvisibleToPlayer(you)) continue;
      if (hologram(e)) continue;
      if (e != mount) {
        if (!world.isBlockLoaded(new BlockPos(e))) continue;
        if (!manager.shouldRender(e, camera, ox, oy, oz)) continue;
      }
      entity(module, look, e, e == pointed, world, ox, oy, oz, pt);
    }
    finish();
  }

  /** Server tricks that would otherwise draw boxes for text holograms or across the whole screen. */
  private static boolean hologram(Entity e) {
    if (e instanceof EntityWitherSkull && (e.riddenByEntity != null || e.ridingEntity != null)) return true;
    if (e.ridingEntity instanceof EntityWitherSkull) return true;
    if (e instanceof EntityHorse && ((EntityHorse) e).getGrowingAge() < -100000) return true;
    AxisAlignedBB box = e.getEntityBoundingBox();
    if (box == null) return true;
    double w = box.maxX - box.minX, h = box.maxY - box.minY, d = box.maxZ - box.minZ;
    return w < 0 || h < 0 || d < 0 || w > MAX_BOX || h > MAX_BOX || d > MAX_BOX;
  }

  private void entity(HitboxesModule module, HitboxGroup look, Entity e, boolean aimed, World world,
      double ox, double oy, double oz, float pt) {
    AxisAlignedBB box = e.getEntityBoundingBox();
    double grow = module.reachMargin.get() ? e.getCollisionBorderSize() : 0.0;
    // Move the box from the entity's current position to where it is drawn this frame.
    double sx = e.lastTickPosX + (e.posX - e.lastTickPosX) * pt - e.posX - ox;
    double sy = e.lastTickPosY + (e.posY - e.lastTickPosY) * pt - e.posY - oy;
    double sz = e.lastTickPosZ + (e.posZ - e.lastTickPosZ) * pt - e.posZ - oz;
    double x0 = box.minX - grow + sx, y0 = box.minY - grow + sy, z0 = box.minZ - grow + sz;
    double x1 = box.maxX + grow + sx, y1 = box.maxY + grow + sy, z1 = box.maxZ + grow + sz;
    double eyeY = e.lastTickPosY + (e.posY - e.lastTickPosY) * pt - oy + e.getEyeHeight();
    double eyeX = (x0 + x1) / 2.0, eyeZ = (z0 + z1) / 2.0;

    int state = 0;
    if (look.hitColours.get() && e instanceof EntityLivingBase && ((EntityLivingBase) e).hurtTime > 0) state = 2;
    else if (look.aimedColours.get() && aimed) state = 1;
    boolean dashed = HitboxGroup.DASHED.equals(look.pattern.get());
    float dash = look.dash.get();

    double k = look.throughModel.get() ? pullFactor(world, x0, y0, z0, x1, y1, z1, ox, oy, oz) : 1.0;
    if (k != 1.0) {
      x0 = pull(x0, camX, k); x1 = pull(x1, camX, k);
      y0 = pull(y0, camY, k); y1 = pull(y1, camY, k);
      z0 = pull(z0, camZ, k); z1 = pull(z1, camZ, k);
    }
    double ey = k != 1.0 ? pull(eyeY, camY, k) : eyeY;

    if (look.fill.get()) {
      paintSolid(x0, y0, z0, x1, y1, z1, look.pick(look.fillColour.get(), look.fillAimed.get(), look.fillHit.get(), state));
    }
    if (look.edges.get()) {
      int c = look.pick(look.edgeColour.get(), look.edgeAimed.get(), look.edgeHit.get(), state);
      float w = look.edgeWidth.get();
      line(x0, y0, z0, x1, y0, z0, w, c, dashed, dash);
      line(x1, y0, z0, x1, y0, z1, w, c, dashed, dash);
      line(x1, y0, z1, x0, y0, z1, w, c, dashed, dash);
      line(x0, y0, z1, x0, y0, z0, w, c, dashed, dash);
      line(x0, y1, z0, x1, y1, z0, w, c, dashed, dash);
      line(x1, y1, z0, x1, y1, z1, w, c, dashed, dash);
      line(x1, y1, z1, x0, y1, z1, w, c, dashed, dash);
      line(x0, y1, z1, x0, y1, z0, w, c, dashed, dash);
      line(x0, y0, z0, x0, y1, z0, w, c, dashed, dash);
      line(x1, y0, z0, x1, y1, z0, w, c, dashed, dash);
      line(x1, y0, z1, x1, y1, z1, w, c, dashed, dash);
      line(x0, y0, z1, x0, y1, z1, w, c, dashed, dash);
    }
    if (look.eye.get()) {
      int c = look.pick(look.eyeColour.get(), look.eyeAimed.get(), look.eyeHit.get(), state);
      float w = look.eyeWidth.get();
      line(x0, ey, z0, x1, ey, z0, w, c, dashed, dash);
      line(x1, ey, z0, x1, ey, z1, w, c, dashed, dash);
      line(x1, ey, z1, x0, ey, z1, w, c, dashed, dash);
      line(x0, ey, z1, x0, ey, z0, w, c, dashed, dash);
    }
    if (look.look.get()) {
      // Never pulled forward: it starts at the real eyes.
      Vec3 dir = e.getLook(pt);
      int c = look.pick(look.lookColour.get(), look.lookAimed.get(), look.lookHit.get(), state);
      line(eyeX, eyeY, eyeZ, eyeX + dir.xCoord * LOOK_LENGTH, eyeY + dir.yCoord * LOOK_LENGTH,
          eyeZ + dir.zCoord * LOOK_LENGTH, look.lookWidth.get(), c, dashed, dash);
    }
  }

  private static double pull(double value, double camera, double k) {
    return camera + (value - camera) * k;
  }

  /**
   * How far to shrink the box toward the camera so it sits in front of the entity's model. Shrinking toward the eye
   * keeps its outline on screen exactly where it was and only brings it closer. Returns 1 (no pull) when the camera is
   * too close or when the space between would cross blocks, so terrain keeps covering it correctly.
   */
  private double pullFactor(World world, double x0, double y0, double z0, double x1, double y1, double z1,
      double ox, double oy, double oz) {
    double dx = (x0 + x1) / 2.0 - camX, dy = (y0 + y1) / 2.0 - camY, dz = (z0 + z1) / 2.0 - camZ;
    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
    double w = x1 - x0, h = y1 - y0, d = z1 - z0;
    double radius = Math.sqrt(w * w + h * h + d * d) / 2.0 + 0.25;
    if (distance - radius < 0.3) return 1.0;
    double k = (distance - radius) / (distance + radius);
    double lx = Math.min(x0, pull(x0, camX, k)), hx = Math.max(x1, pull(x1, camX, k));
    double ly = Math.min(y0, pull(y0, camY, k)), hy = Math.max(y1, pull(y1, camY, k));
    double lz = Math.min(z0, pull(z0, camZ, k)), hz = Math.max(z1, pull(z1, camZ, k));
    int bx0 = floor(lx + ox + 0.01), bx1 = floor(hx + ox - 0.01);
    int by0 = floor(ly + oy + 0.01), by1 = floor(hy + oy - 0.01);
    int bz0 = floor(lz + oz + 0.01), bz1 = floor(hz + oz - 0.01);
    long cells = (long) (bx1 - bx0 + 3) * (by1 - by0 + 3) * (bz1 - bz0 + 3);
    if (cells > 1500) return 1.0;
    for (int x = bx0 - 1; x <= bx1 + 1; x++) {
      for (int y = by0 - 1; y <= by1 + 1; y++) {
        for (int z = bz0 - 1; z <= bz1 + 1; z++) {
          BlockPos pos = new BlockPos(x, y, z);
          boolean inside = x >= bx0 && x <= bx1 && y >= by0 && y <= by1 && z >= bz0 && z <= bz1;
          if (inside && !world.isAirBlock(pos)) return 1.0;
          // block entities draw past their own block, so they count one block further out
          if (world.getTileEntity(pos) != null) return 1.0;
        }
      }
    }
    return k;
  }

  private static int floor(double value) {
    int i = (int) value;
    return value < i ? i - 1 : i;
  }

  // ---------------------------------------------------------------- geometry

  private void sampleView() {
    matrix.clear();
    GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, matrix);
    float m0 = matrix.get(0), m1 = matrix.get(1), m2 = matrix.get(2);
    float m4 = matrix.get(4), m5 = matrix.get(5), m6 = matrix.get(6);
    float m8 = matrix.get(8), m9 = matrix.get(9), m10 = matrix.get(10);
    float tx = matrix.get(12), ty = matrix.get(13), tz = matrix.get(14);
    camX = -(m0 * tx + m1 * ty + m2 * tz);
    camY = -(m4 * tx + m5 * ty + m6 * tz);
    camZ = -(m8 * tx + m9 * ty + m10 * tz);
    double fx = -m2, fy = -m6, fz = -m10;
    double length = Math.sqrt(fx * fx + fy * fy + fz * fz);
    if (length < 1e-6) {
      fwdX = 0; fwdY = 0; fwdZ = 1;
    } else {
      fwdX = fx / length; fwdY = fy / length; fwdZ = fz / length;
    }
  }

  private double depth(double x, double y, double z) {
    return (x - camX) * fwdX + (y - camY) * fwdY + (z - camZ) * fwdZ;
  }

  /** Line width in screen pixels at a point. */
  private double pixels(float width, double x, double y, double z) {
    double base = width * PIXELS_PER_WIDTH;
    if (!shrink) return base;
    double dx = x - camX, dy = y - camY, dz = z - camZ;
    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
    if (distance <= FULL_WIDTH_RANGE) return base;
    return Math.max(1.0, base * FULL_WIDTH_RANGE / distance);
  }

  /** A line from one point to another, clipped to the space in front of the camera, solid or dashed. */
  private void line(double ax, double ay, double az, double bx, double by, double bz,
      float width, int colour, boolean dashed, float dashSetting) {
    if ((colour >>> 24) == 0) return;
    double da = depth(ax, ay, az), db = depth(bx, by, bz);
    if (da < NEAR && db < NEAR) return;
    if (da < NEAR) {
      double t = (NEAR - da) / (db - da);
      ax += (bx - ax) * t; ay += (by - ay) * t; az += (bz - az) * t;
    } else if (db < NEAR) {
      double t = (NEAR - db) / (da - db);
      bx += (ax - bx) * t; by += (ay - by) * t; bz += (az - bz) * t;
    }
    if (!dashed) {
      strip(ax, ay, az, bx, by, bz, width, colour);
      return;
    }
    double dx = bx - ax, dy = by - ay, dz = bz - az;
    double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
    if (length < 1e-6) return;
    double mx = (ax + bx) / 2.0, my = (ay + by) / 2.0, mz = (az + bz) / 2.0;
    double step = Math.max(dashSetting * DASH_UNIT, MIN_DASH_PIXELS * depth(mx, my, mz) * pixelAtOne);
    step = Math.max(step, length / 256.0);
    for (double s = 0; s < length; s += 2 * step) {
      double e = Math.min(s + step, length);
      double t0 = s / length, t1 = e / length;
      strip(ax + dx * t0, ay + dy * t0, az + dz * t0, ax + dx * t1, ay + dy * t1, az + dz * t1, width, colour);
    }
  }

  /** One camera-facing strip with square ends; its width is in screen pixels at each end. */
  private void strip(double ax, double ay, double az, double bx, double by, double bz, float width, int colour) {
    double dx = bx - ax, dy = by - ay, dz = bz - az;
    double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
    if (length < 1e-6) return;
    dx /= length; dy /= length; dz /= length;
    // Across the line and across the view ray: the same direction anywhere along a straight line.
    double vx = ax - camX, vy = ay - camY, vz = az - camZ;
    double sx = dy * vz - dz * vy, sy = dz * vx - dx * vz, sz = dx * vy - dy * vx;
    double side = Math.sqrt(sx * sx + sy * sy + sz * sz);
    if (side < 1e-9) return; // the line points straight at the camera
    sx /= side; sy /= side; sz /= side;
    double ha = pixels(width, ax, ay, az) * 0.5 * Math.max(NEAR, depth(ax, ay, az)) * pixelAtOne;
    double hb = pixels(width, bx, by, bz) * 0.5 * Math.max(NEAR, depth(bx, by, bz)) * pixelAtOne;
    ax -= dx * ha; ay -= dy * ha; az -= dz * ha;
    bx += dx * hb; by += dy * hb; bz += dz * hb;
    begin();
    float r = (colour >> 16 & 255) / 255F, g = (colour >> 8 & 255) / 255F, b = (colour & 255) / 255F;
    float al = (colour >>> 24) / 255F;
    buffer.pos(ax - sx * ha, ay - sy * ha, az - sz * ha).color(r, g, b, al).endVertex();
    buffer.pos(ax + sx * ha, ay + sy * ha, az + sz * ha).color(r, g, b, al).endVertex();
    buffer.pos(bx + sx * hb, by + sy * hb, bz + sz * hb).color(r, g, b, al).endVertex();
    buffer.pos(bx - sx * hb, by - sy * hb, bz - sz * hb).color(r, g, b, al).endVertex();
  }

  private void paintSolid(double x0, double y0, double z0, double x1, double y1, double z1, int colour) {
    if ((colour >>> 24) == 0) return;
    begin();
    float r = (colour >> 16 & 255) / 255F, g = (colour >> 8 & 255) / 255F, b = (colour & 255) / 255F;
    float al = (colour >>> 24) / 255F;
    face(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, b, al);
    face(x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, r, g, b, al);
    face(x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, r, g, b, al);
    face(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r, g, b, al);
    face(x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r, g, b, al);
    face(x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, r, g, b, al);
  }

  private void face(double ax, double ay, double az, double bx, double by, double bz, double cx, double cy, double cz,
      double dx, double dy, double dz, float r, float g, float b, float al) {
    buffer.pos(ax, ay, az).color(r, g, b, al).endVertex();
    buffer.pos(bx, by, bz).color(r, g, b, al).endVertex();
    buffer.pos(cx, cy, cz).color(r, g, b, al).endVertex();
    buffer.pos(dx, dy, dz).color(r, g, b, al).endVertex();
  }

  // ---------------------------------------------------------------- GL

  private void begin() {
    if (drawing) return;
    hadBlend = GL11.glIsEnabled(GL11.GL_BLEND);
    hadTexture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
    hadLighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
    hadCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
    hadAlpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
    hadFog = GL11.glIsEnabled(GL11.GL_FOG);
    hadDepth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
    GlStateManager.disableTexture2D();
    GlStateManager.disableLighting();
    GlStateManager.disableCull();
    GlStateManager.disableAlpha();
    GlStateManager.disableFog();
    GlStateManager.enableBlend();
    GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
    GlStateManager.enableDepth();
    GlStateManager.depthMask(false);
    buffer = Tessellator.getInstance().getWorldRenderer();
    buffer.begin(QUADS, DefaultVertexFormats.POSITION_COLOR);
    drawing = true;
  }

  private void finish() {
    if (!drawing) return;
    drawing = false;
    Tessellator.getInstance().draw();
    restoreState();
  }

  /** After an error: drop what was half built and put GL back. */
  private void abort() {
    if (!drawing) return;
    drawing = false;
    try {
      buffer.finishDrawing();
    } catch (Throwable ignored) {
      // nothing more to undo
    }
    restoreState();
  }

  private void restoreState() {
    GlStateManager.depthMask(true);
    if (hadDepth) GlStateManager.enableDepth(); else GlStateManager.disableDepth();
    if (hadTexture) GlStateManager.enableTexture2D(); else GlStateManager.disableTexture2D();
    if (hadLighting) GlStateManager.enableLighting(); else GlStateManager.disableLighting();
    if (hadCull) GlStateManager.enableCull(); else GlStateManager.disableCull();
    if (hadAlpha) GlStateManager.enableAlpha(); else GlStateManager.disableAlpha();
    if (hadFog) GlStateManager.enableFog(); else GlStateManager.disableFog();
    if (hadBlend) GlStateManager.enableBlend(); else GlStateManager.disableBlend();
    GlStateManager.color(1F, 1F, 1F, 1F);
  }
}

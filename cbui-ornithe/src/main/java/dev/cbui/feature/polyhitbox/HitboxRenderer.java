package dev.cbui.feature.polyhitbox;

import dev.cbui.Cbui;
import dev.cbui.feature.Features;
import dev.cbui.feature.polyhitbox.PolyHitboxModule.Cat;
import dev.cbui.feature.polyhitbox.PolyHitboxModule.Style;
import dev.cbui.mixin.PolyHitboxArrowAccess;
import dev.cbui.mixin.PolyHitboxFovAccess;
import java.nio.FloatBuffer;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

/**
 * Paints the hitboxes once the frame's entities are done. Lines are camera-facing ribbons (so their width is set in screen pixels,
 * not GL line width) and everything goes into a single quad batch. All state lives in a few reused holders, so a frame allocates
 * nothing apart from the frustum test box.
 */
public final class HitboxRenderer {
  /** Width in framebuffer pixels that one unit of line thickness stands for. */
  private static final float LINE_UNIT = 2.5f;
  private static final double NEAR = 0.05, TINY = 1.0e-12;
  private static final double RAY_LENGTH = 2.0, CULL_PAD = 0.5;
  private static final double THIN_BEYOND = 4.0, THIN_MIN_PIXELS = 1.0;
  private static final double MODEL_PAD = 0.5, TOUCH = 1.0e-4;
  private static final double DASH_UNIT = 0.005, DASH_FLOOR = 0.03, DASH_CAP = 1024.0;
  private static final double EYE_LINE_HALF = 0.01;

  private static final Eye EYE = new Eye();
  private static final Scene SCENE = new Scene();
  private static final DepthPull PULL = new DepthPull();
  private static final Mesh MESH = new Mesh();
  private static final GlFlags GL_FLAGS = new GlFlags();

  private static final double[] LO = new double[3], HI = new double[3];
  private static final double[] EYE_LO = new double[3], EYE_HI = new double[3];
  private static final double[] BLOCK_LO = new double[3], BLOCK_HI = new double[3];
  private static final double[] CORNER_A = new double[3], CORNER_B = new double[3];
  private static final double[] SIDE_A = new double[3], SIDE_B = new double[3];
  private static final double[] WINDOW = new double[2];
  private static final int[] SCAN_FROM = new int[3], SCAN_TO = new int[3];
  private static final BlockPos.MutableBlockPos PROBE = new BlockPos.MutableBlockPos();

  private static PolyHitboxModule cachedModule;
  private static boolean disabledByFailure;

  private HitboxRenderer() {}

  public static PolyHitboxModule module() {
    PolyHitboxModule m = cachedModule;
    if (m == null) {
      m = Features.get(PolyHitboxModule.class);
      cachedModule = m;
    }
    return m;
  }

  /** True while this module stands in for vanilla's hitbox renderer and its F3+B key. */
  public static boolean replacing() {
    PolyHitboxModule m = module();
    return m != null && m.enabled();
  }

  public static void render(ICamera frustum, float partialTicks) {
    if (disabledByFailure) return;
    try {
      PolyHitboxModule m = module();
      if (m == null || !m.enabled()) return;
      if (!prepareFrame(m, frustum, partialTicks)) return;
      paintFrame();
    } catch (Throwable failure) {
      disabledByFailure = true; // one report, then stay off until the next launch
      Cbui.reportOnce("hitbox renderer", failure);
    }
  }

  // ---------------------------------------------------------------------------------------------- per-frame state holders

  /** Where the camera is and which way it faces, read back from the modelview matrix. */
  private static final class Eye {
    private static final FloatBuffer MATRIX = GLAllocation.createDirectFloatBuffer(16);

    final double[] pos = new double[3];
    final double[] fwd = new double[3];
    final double[] shift = new double[3];
    /** World units covered by one thickness unit at depth 1. */
    double unit;
    float partial;
    ICamera frustum;
    private double seenFov = -1, seenHeight = -1;

    double depth(double x, double y, double z) { return x * fwd[0] + y * fwd[1] + z * fwd[2]; }

    void capture(Minecraft mc, float partialTicks, ICamera cull) {
      partial = partialTicks;
      frustum = cull;
      // 1.8.9 folds eye offset, third person and bobbing into the modelview, so the real camera has to be recovered from it
      MATRIX.clear();
      GlStateManager.getFloat(GL11.GL_MODELVIEW_MATRIX, MATRIX);
      double tx = MATRIX.get(12), ty = MATRIX.get(13), tz = MATRIX.get(14);
      RenderManager rm = mc.getRenderManager();
      double lengthSq = 0.0;
      for (int axis = 0; axis < 3; axis++) {
        int col = axis * 4;
        shift[axis] = -(MATRIX.get(col) * tx + MATRIX.get(col + 1) * ty + MATRIX.get(col + 2) * tz);
        fwd[axis] = -MATRIX.get(col + 2);
        lengthSq += fwd[axis] * fwd[axis];
      }
      pos[0] = rm.viewerPosX + shift[0];
      pos[1] = rm.viewerPosY + shift[1];
      pos[2] = rm.viewerPosZ + shift[2];
      double length = Math.sqrt(lengthSq);
      if (length < 1e-9) length = 1.0;
      for (int axis = 0; axis < 3; axis++) fwd[axis] /= length;
      refreshUnit(mc);
    }

    private void refreshUnit(Minecraft mc) {
      double fov = ((PolyHitboxFovAccess) mc.entityRenderer).cbui$fov(partial, true);
      double height = mc.displayHeight;
      if (fov == seenFov && height == seenHeight) return;
      seenFov = fov;
      seenHeight = height;
      double halfTan = Math.tan(Math.toRadians(fov) * 0.5);
      unit = halfTan > 0.0 && height > 0.0 ? LINE_UNIT * halfTan / height : 0.0;
    }
  }

  /** What the player is looking at and which module instance drives this frame. */
  private static final class Scene {
    PolyHitboxModule module;
    Entity hovered, firstPersonSelf;
    EntityPlayer viewer;
    boolean toggled;
  }

  /** "Draw over entity": a depth remap that pulls the boxes toward the camera so the model does not hide them. */
  private static final class DepthPull {
    boolean active;
    double invTarget, invFar, squash = 1.0;

    double factor(double x, double y, double z) {
      double depth = EYE.depth(x, y, z);
      if (depth <= NEAR) return 1.0;
      return 1.0 / ((invTarget + (1.0 / depth - invFar) * squash) * depth);
    }
  }

  /** The vertex batch; quads are staged in {@link #q} as four xyz corners, then pushed through the depth pull. */
  private static final class Mesh {
    WorldRenderer sink;
    final double[] q = new double[12];

    void emitQuad(int argb) {
      int red = argb >> 16 & 255, green = argb >> 8 & 255, blue = argb & 255, alpha = argb >>> 24;
      for (int i = 0; i < 12; i += 3) {
        double x = q[i], y = q[i + 1], z = q[i + 2];
        double k = PULL.active ? PULL.factor(x, y, z) : 1.0;
        sink.pos(x * k, y * k, z * k).color(red, green, blue, alpha).endVertex();
      }
    }
  }

  /** The handful of GL switches the pass touches, so they can be put back exactly as found. */
  private static final class GlFlags {
    boolean lighting, cull, alpha, blend, texture, depth, depthWrite;

    void capture() {
      lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
      cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
      alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
      blend = GL11.glIsEnabled(GL11.GL_BLEND);
      texture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
      depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
      depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
    }

    void restore() {
      if (!blend) GlStateManager.disableBlend();
      if (alpha) GlStateManager.enableAlpha();
      if (cull) GlStateManager.enableCull();
      if (lighting) GlStateManager.enableLighting();
      if (texture) GlStateManager.enableTexture2D();
      if (!depth) GlStateManager.disableDepth();
      GlStateManager.depthMask(depthWrite);
      GlStateManager.color(1f, 1f, 1f, 1f);
    }
  }

  // ---------------------------------------------------------------------------------------------- frame setup and GL pass

  private static boolean prepareFrame(PolyHitboxModule m, ICamera frustum, float partialTicks) {
    Minecraft mc = Minecraft.getMinecraft();
    if (mc.theWorld == null || mc.thePlayer == null) return false;
    if (mc.thePlayer.hasReducedDebug() || mc.gameSettings.reducedDebugInfo) return false;
    if (m.hideInF1.get() && mc.gameSettings.hideGUI) return false;
    if (!m.anythingVisible()) return false;
    Entity camera = mc.getRenderViewEntity();
    if (camera == null) return false;
    List<Entity> everyone = mc.theWorld.loadedEntityList;
    boolean firstPerson = mc.gameSettings.thirdPersonView == 0;
    if (firstPerson && everyone.size() <= 1 && (everyone.isEmpty() || everyone.get(0) == camera)) return false;

    SCENE.module = m;
    SCENE.hovered = mc.pointedEntity;
    SCENE.viewer = mc.thePlayer;
    SCENE.firstPersonSelf = firstPerson ? camera : null;
    SCENE.toggled = m.toggled.get();
    EYE.capture(mc, partialTicks, frustum);
    return true;
  }

  private static void paintFrame() {
    Tessellator tessellator = Tessellator.getInstance();
    MESH.sink = tessellator.getWorldRenderer();
    GL_FLAGS.capture();
    GlStateManager.pushMatrix();
    try {
      GlStateManager.translate(EYE.shift[0], EYE.shift[1], EYE.shift[2]);
      GlStateManager.enableDepth();
      GlStateManager.depthFunc(515);
      GlStateManager.depthMask(false);
      GlStateManager.disableTexture2D();
      GlStateManager.disableLighting();
      GlStateManager.disableCull();
      GlStateManager.disableAlpha();
      GlStateManager.enableBlend();
      GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
      MESH.sink.begin(7, DefaultVertexFormats.POSITION_COLOR);
      boolean complete = false;
      try {
        visitEntities();
        complete = true;
      } finally {
        if (complete) tessellator.draw();
        else MESH.sink.finishDrawing();
      }
    } finally {
      GlStateManager.popMatrix();
      GL_FLAGS.restore();
      MESH.sink = null;
    }
  }

  // ---------------------------------------------------------------------------------------------- choosing what to draw

  private static void visitEntities() {
    Minecraft mc = Minecraft.getMinecraft();
    World world = mc.theWorld;
    PolyHitboxModule m = SCENE.module;
    boolean showGroundedArrows = m.style(Cat.ARROW).showGrounded.get();
    RenderManager rm = mc.getRenderManager();
    List<Entity> entities = world.loadedEntityList;
    for (int i = 0; i < entities.size(); i++) {
      Entity entity = entities.get(i);
      if (entity == null || entity == SCENE.firstPersonSelf || entity.isInvisible()) continue;
      if (!showGroundedArrows && entity instanceof EntityArrow && ((PolyHitboxArrowAccess) entity).cbui$inGround()) continue;
      Style matched = m.match(entity);
      Style look = m.visualsOf(matched);
      if (!look.side.get() && !look.outline.get() && !look.eye.get() && !look.viewRay.get()) continue;
      if (!conditionMet(m.logicOf(matched).condition(), entity)) continue;
      if (outOfView(entity, look)) continue;
      if (!vanillaWouldRender(rm, world, entity)) continue;
      if (entity.isInvisibleToPlayer(SCENE.viewer)) continue;
      paintEntity(entity, look);
    }
  }

  private static boolean conditionMet(int condition, Entity entity) {
    switch (condition) {
      case PolyHitboxModule.ALWAYS: return true;
      case PolyHitboxModule.TOGGLED: return SCENE.toggled;
      case PolyHitboxModule.HOVERED: return entity == SCENE.hovered;
      default: return false;
    }
  }

  /** Mirrors vanilla's own decision to render the entity (distance, frustum, chunk loaded), with riders always allowed. */
  private static boolean vanillaWouldRender(RenderManager rm, World world, Entity entity) {
    boolean accepted = rm.shouldRender(entity, EYE.frustum, rm.viewerPosX, rm.viewerPosY, rm.viewerPosZ);
    if (!accepted && !isRiddenBy(entity, SCENE.viewer)) return false;
    int y = Math.max(0, Math.min(255, (int) Math.floor(entity.posY)));
    PROBE.set((int) Math.floor(entity.posX), y, (int) Math.floor(entity.posZ));
    return world.isBlockLoaded(PROBE);
  }

  private static boolean isRiddenBy(Entity mount, Entity candidate) {
    for (Entity rider = mount.riddenByEntity; rider != null; rider = rider.riddenByEntity) {
      if (rider == candidate) return true;
    }
    return false;
  }

  private static boolean outOfView(Entity entity, Style look) {
    AxisAlignedBB bb = entity.getEntityBoundingBox();
    if (Double.isNaN(bb.minX + bb.minY + bb.minZ + bb.maxX + bb.maxY + bb.maxZ)) return true;
    double cx = (bb.minX + bb.maxX) * 0.5 - EYE.pos[0];
    double cy = (bb.minY + bb.maxY) * 0.5 - EYE.pos[1];
    double cz = (bb.minZ + bb.maxZ) * 0.5 - EYE.pos[2];
    if (!entity.isInRangeToRenderDist(cx * cx + cy * cy + cz * cz)) return true;
    double reach = CULL_PAD + (look.viewRay.get() ? RAY_LENGTH : 0.0);
    double hx = (bb.maxX - bb.minX) * 0.5, hy = (bb.maxY - bb.minY) * 0.5, hz = (bb.maxZ - bb.minZ) * 0.5;
    double bound = Math.sqrt(hx * hx + hy * hy + hz * hz) + reach;
    if (EYE.depth(cx, cy, cz) < -bound) return true;
    return EYE.frustum != null && !EYE.frustum.isBoundingBoxInFrustum(bb.expand(reach, reach, reach));
  }

  // ---------------------------------------------------------------------------------------------- drawing one entity

  private static double lerp(double from, double to, double t) { return from + t * (to - from); }

  private static int tint(boolean hurt, boolean hovered, int base, int hoverTint, int hurtTint) {
    return hurt ? hurtTint : hovered ? hoverTint : base;
  }

  private static void paintEntity(Entity entity, Style s) {
    float t = EYE.partial;
    double ix = lerp(entity.lastTickPosX, entity.posX, t);
    double iy = lerp(entity.lastTickPosY, entity.posY, t);
    double iz = lerp(entity.lastTickPosZ, entity.posZ, t);
    double grow = SCENE.module.accurateHitboxes.get() ? entity.getCollisionBorderSize() : 0.0;
    AxisAlignedBB bb = entity.getEntityBoundingBox();
    double ox = ix - entity.posX - EYE.pos[0];
    double oy = iy - entity.posY - EYE.pos[1];
    double oz = iz - entity.posZ - EYE.pos[2];
    LO[0] = bb.minX - grow + ox; LO[1] = bb.minY - grow + oy; LO[2] = bb.minZ - grow + oz;
    HI[0] = bb.maxX + grow + ox; HI[1] = bb.maxY + grow + oy; HI[2] = bb.maxZ + grow + oz;
    double eyeLine = LO[1] + grow + entity.getEyeHeight();

    boolean hovered = entity == SCENE.hovered && s.hoverColor.get();
    boolean hurt = s.iframeColor.get() && entity instanceof EntityLivingBase && ((EntityLivingBase) entity).hurtTime > 0;

    PULL.active = s.drawOver.get() && computePull(s);
    if (s.side.get()) {
      fillBox(LO, HI, tint(hurt, hovered, s.sideColor.get(), s.sideHover.get(), s.sideIframe.get()));
    }
    if (s.outline.get()) {
      strokeBox(s, LO, HI, tint(hurt, hovered, s.outlineColor.get(), s.outlineHover.get(), s.outlineIframe.get()),
          s.outlineThickness.get());
    }
    if (s.eye.get()) {
      EYE_LO[0] = LO[0]; EYE_LO[1] = eyeLine - EYE_LINE_HALF; EYE_LO[2] = LO[2];
      EYE_HI[0] = HI[0]; EYE_HI[1] = eyeLine + EYE_LINE_HALF; EYE_HI[2] = HI[2];
      strokeBox(s, EYE_LO, EYE_HI, tint(hurt, hovered, s.eyeColor.get(), s.eyeHover.get(), s.eyeIframe.get()),
          s.eyeThickness.get());
    }
    if (s.viewRay.get()) {
      PULL.active = false;
      Vec3 gaze = entity.getLook(t);
      double fromX = ix - EYE.pos[0], fromZ = iz - EYE.pos[2];
      strokeEdge(s, fromX, eyeLine, fromZ,
          fromX + gaze.xCoord * RAY_LENGTH, eyeLine + gaze.yCoord * RAY_LENGTH, fromZ + gaze.zCoord * RAY_LENGTH,
          tint(hurt, hovered, s.rayColor.get(), s.rayHover.get(), s.rayIframe.get()), s.rayThickness.get());
    }
  }

  // ---------------------------------------------------------------------------------------------- box and line geometry

  /** Corner {@code index} of the box: bit 0 picks x, bit 1 picks y, bit 2 picks z (clear = low side, set = high side). */
  private static void cornerOf(double[] lo, double[] hi, int index, double[] out) {
    for (int axis = 0; axis < 3; axis++) out[axis] = (index >> axis & 1) == 0 ? lo[axis] : hi[axis];
  }

  private static void fillBox(double[] lo, double[] hi, int argb) {
    double[] q = MESH.q;
    for (int axis = 0; axis < 3; axis++) {
      int u = (axis + 1) % 3, v = (axis + 2) % 3;
      for (int face = 0; face < 2; face++) {
        double plane = face == 0 ? lo[axis] : hi[axis];
        for (int k = 0; k < 4; k++) {
          int at = k * 3;
          q[at + axis] = plane;
          q[at + u] = (k == 1 || k == 2) ? hi[u] : lo[u];
          q[at + v] = k >= 2 ? hi[v] : lo[v];
        }
        MESH.emitQuad(argb);
      }
    }
  }

  /** The twelve edges: every corner with a clear bit connects to the corner with that bit set. */
  private static void strokeBox(Style s, double[] lo, double[] hi, int argb, float thickness) {
    for (int corner = 0; corner < 8; corner++) {
      for (int axis = 0; axis < 3; axis++) {
        int bit = 1 << axis;
        if ((corner & bit) != 0) continue;
        cornerOf(lo, hi, corner, CORNER_A);
        cornerOf(lo, hi, corner | bit, CORNER_B);
        strokeEdge(s, CORNER_A[0], CORNER_A[1], CORNER_A[2], CORNER_B[0], CORNER_B[1], CORNER_B[2], argb, thickness);
      }
    }
  }

  private static void strokeEdge(Style s, double ax, double ay, double az, double bx, double by, double bz, int argb,
      float thickness) {
    if (!s.dashed()) {
      strokeRibbon(ax, ay, az, bx, by, bz, thickness, argb);
      return;
    }
    double dx = bx - ax, dy = by - ay, dz = bz - az;
    double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
    if (!(length >= 1.0e-6) || Double.isInfinite(length)) return;
    double dash = Math.max(Math.max(s.dashFactor.get() * DASH_UNIT, DASH_FLOOR), length / DASH_CAP);
    for (double start = 0.0; start < length; start += dash * 2.0) {
      double f0 = start / length, f1 = Math.min(start + dash, length) / length;
      strokeRibbon(ax + dx * f0, ay + dy * f0, az + dz * f0, ax + dx * f1, ay + dy * f1, az + dz * f1, thickness, argb);
    }
  }

  /** A flat strip along the segment that always faces the camera; the part behind the near plane is trimmed off. */
  private static void strokeRibbon(double sx, double sy, double sz, double ex, double ey, double ez, float thickness, int argb) {
    double depthStart = EYE.depth(sx, sy, sz), depthEnd = EYE.depth(ex, ey, ez);
    if (depthStart < NEAR && depthEnd < NEAR) return;
    double ax = sx, ay = sy, az = sz, bx = ex, by = ey, bz = ez;
    if (depthStart < NEAR) {
      double f = (NEAR - depthStart) / (depthEnd - depthStart);
      ax = sx + (ex - sx) * f; ay = sy + (ey - sy) * f; az = sz + (ez - sz) * f;
    } else if (depthEnd < NEAR) {
      double f = (NEAR - depthEnd) / (depthStart - depthEnd);
      bx = ex + (sx - ex) * f; by = ey + (sy - ey) * f; bz = ez + (sz - ez) * f;
    }
    double dx = bx - ax, dy = by - ay, dz = bz - az;
    if (dx * dx + dy * dy + dz * dz < TINY) return;
    sideVector(dx, dy, dz, ax, ay, az, thickness, SIDE_A);
    sideVector(dx, dy, dz, bx, by, bz, thickness, SIDE_B);
    double[] q = MESH.q;
    q[0] = ax + SIDE_A[0]; q[1] = ay + SIDE_A[1]; q[2] = az + SIDE_A[2];
    q[3] = bx + SIDE_B[0]; q[4] = by + SIDE_B[1]; q[5] = bz + SIDE_B[2];
    q[6] = bx - SIDE_B[0]; q[7] = by - SIDE_B[1]; q[8] = bz - SIDE_B[2];
    q[9] = ax - SIDE_A[0]; q[10] = ay - SIDE_A[1]; q[11] = az - SIDE_A[2];
    MESH.emitQuad(argb);
  }

  /** Half-width vector of the ribbon at point v for a segment with direction d: perpendicular to d, flattened into the view plane. */
  private static void sideVector(double dx, double dy, double dz, double vx, double vy, double vz, float thickness, double[] out) {
    double nx = dy * vz - dz * vy, ny = dz * vx - dx * vz, nz = dx * vy - dy * vx;
    double[] f = EYE.fwd;
    double along = nx * f[0] + ny * f[1] + nz * f[2];
    double cx = nx - along * f[0], cy = ny - along * f[1], cz = nz - along * f[2];
    double lengthSq = cx * cx + cy * cy + cz * cz;
    if (lengthSq < TINY) {
      out[0] = out[1] = out[2] = 0.0;
      return;
    }
    double depth = Math.max(EYE.depth(vx, vy, vz), NEAR);
    if (SCENE.module.thinWithDistance.get()) {
      double floor = Math.min(1.0, THIN_MIN_PIXELS / (thickness * LINE_UNIT));
      depth = Math.max(Math.min(depth, THIN_BEYOND), depth * floor);
    }
    double k = thickness * EYE.unit * depth / Math.sqrt(lengthSq);
    out[0] = cx * k;
    out[1] = cy * k;
    out[2] = cz * k;
  }

  // ---------------------------------------------------------------------------------------------- "draw over entity"

  /**
   * Sets up the depth pull for the box in {@link #LO}/{@link #HI}. Returns false when the pull is pointless (box already in front of
   * the model) or when it would drag the box through solid terrain.
   */
  private static boolean computePull(Style s) {
    float widest = 0f;
    if (s.outline.get()) widest = Math.max(widest, s.outlineThickness.get());
    if (s.eye.get()) widest = Math.max(widest, s.eyeThickness.get());
    double[] f = EYE.fwd;
    double mid = 0.5 * ((LO[0] + HI[0]) * f[0] + (LO[1] + HI[1]) * f[1] + (LO[2] + HI[2]) * f[2]);
    double radius = 0.5 * ((HI[0] - LO[0]) * Math.abs(f[0]) + (HI[1] - LO[1]) * Math.abs(f[1]) + (HI[2] - LO[2]) * Math.abs(f[2]));
    double far = mid + radius;
    double pad = Math.max(widest * EYE.unit * far, 0.0);
    double boxFar = far + pad;
    double boxNear = mid - radius - pad;
    double modelNear = boxNear - MODEL_PAD * (Math.abs(f[0]) + Math.abs(f[1]) + Math.abs(f[2]));
    double target = Math.max(modelNear, NEAR);
    if (boxFar <= target) return false;
    PULL.invTarget = 1.0 / target;
    PULL.invFar = 1.0 / boxFar;
    double span = 1.0 / Math.max(boxNear, NEAR) - PULL.invFar;
    PULL.squash = span > 0.0 ? Math.min(1.0, (1.0 / NEAR - PULL.invTarget) / span) : 1.0;
    return !pullHitsTerrain(target / boxFar);
  }

  /** Does shrinking the box toward the camera (down to {@code minScale}) push it into any solid block on the way? */
  private static boolean pullHitsTerrain(double minScale) {
    World world = Minecraft.getMinecraft().theWorld;
    if (world == null) return true;
    for (int axis = 0; axis < 3; axis++) {
      SCAN_FROM[axis] = (int) Math.floor(Math.min(LO[axis], LO[axis] * minScale) + EYE.pos[axis]) - 1;
      SCAN_TO[axis] = (int) Math.floor(Math.max(HI[axis], HI[axis] * minScale) + EYE.pos[axis]) + 1;
    }
    for (int x = SCAN_FROM[0]; x <= SCAN_TO[0]; x++) {
      for (int y = SCAN_FROM[1]; y <= SCAN_TO[1]; y++) {
        for (int z = SCAN_FROM[2]; z <= SCAN_TO[2]; z++) {
          if (loadSolidBounds(world, x, y, z) && boxScaleMeetsBlock(minScale)) return true;
        }
      }
    }
    return false;
  }

  /** Fills {@link #BLOCK_LO}/{@link #BLOCK_HI} (camera-relative, slightly inset) for a solid block; false for air/unloaded. */
  private static boolean loadSolidBounds(World world, int x, int y, int z) {
    if (y < 0 || y > 255) return false;
    PROBE.set(x, y, z);
    if (!world.isBlockLoaded(PROBE) || world.isAirBlock(PROBE)) return false;
    // blocks with a tile entity are treated as three blocks wide, since their models can spill past the cell
    double reachLow = 0.0, reachHigh = 1.0;
    if (world.getTileEntity(PROBE) != null) {
      reachLow = -1.0;
      reachHigh = 2.0;
    }
    BLOCK_LO[0] = x + reachLow - EYE.pos[0] + TOUCH;
    BLOCK_LO[1] = y + reachLow - EYE.pos[1] + TOUCH;
    BLOCK_LO[2] = z + reachLow - EYE.pos[2] + TOUCH;
    BLOCK_HI[0] = x + reachHigh - EYE.pos[0] - TOUCH;
    BLOCK_HI[1] = y + reachHigh - EYE.pos[1] - TOUCH;
    BLOCK_HI[2] = z + reachHigh - EYE.pos[2] - TOUCH;
    return true;
  }

  /**
   * Is there a scale factor s in [1, 1/minScale] at which the box (scaled about the camera) overlaps the block? Each box face gives
   * one inequality in s per axis; the answer is whether the surviving interval is non-empty.
   */
  static boolean boxScaleMeetsBlock(double minScale) {
    WINDOW[0] = 1.0;
    WINDOW[1] = 1.0 / minScale;
    for (int axis = 0; axis < 3; axis++) {
      if (!constrain(BLOCK_LO[axis], HI[axis], true)) return false;
      if (!constrain(BLOCK_HI[axis], LO[axis], false)) return false;
    }
    return WINDOW[0] < WINDOW[1];
  }

  /** Narrows {@link #WINDOW} by the condition {@code s * coef < limit} (below) or {@code s * coef > limit}; false if unsatisfiable. */
  private static boolean constrain(double coef, double limit, boolean below) {
    if (coef == 0.0) return below ? limit > 0.0 : limit < 0.0;
    boolean upperBound = (coef > 0.0) == below;
    double edge = limit / coef;
    if (upperBound) WINDOW[1] = Math.min(WINDOW[1], edge);
    else WINDOW[0] = Math.max(WINDOW[0], edge);
    return true;
  }
}

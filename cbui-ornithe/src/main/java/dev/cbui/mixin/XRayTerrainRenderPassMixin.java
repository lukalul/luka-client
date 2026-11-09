package dev.cbui.mixin;

import dev.cbui.feature.staff.XRayState;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Staff X-Ray, drawing side. Every terrain pass except "translucent" is drawn with a constant alpha (the Block opacity) and without depth writes,
 * so the terrain is faint and does not hide what is behind it; the translucent pass keeps its own state and carries the ore blocks. The GL state
 * is read back after the pass has set itself up and written back exactly before it clears itself.
 */
@Pseudo
@Mixin(targets = "org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass", remap = false)
public abstract class XRayTerrainRenderPassMixin {
  private static final int GL_BLEND_COLOR = 0x8005, GL_CONSTANT_ALPHA = 0x8003, GL_ONE_MINUS_CONSTANT_ALPHA = 0x8004;
  private static final int GL_BLEND_SRC_RGB = 0x80C9, GL_BLEND_DST_RGB = 0x80C8, GL_BLEND_SRC_ALPHA = 0x80CB, GL_BLEND_DST_ALPHA = 0x80CA;
  private static final int GL_DEPTH_WRITEMASK = 0x0B72;
  private static final IntBuffer INTS = BufferUtils.createIntBuffer(16);
  private static final FloatBuffer FLOATS = BufferUtils.createFloatBuffer(16);
  private static boolean cbui$applied;
  private static boolean cbui$blend, cbui$depth;
  private static int cbui$srcRgb, cbui$dstRgb, cbui$srcA, cbui$dstA;
  private static final float[] cbui$color = new float[4];

  @Shadow private String name;

  private static int cbui$int(int pname) {
    INTS.clear();
    GL11.glGetInteger(pname, INTS);
    return INTS.get(0);
  }

  @Inject(method = "startDrawing", at = @At("TAIL"), require = 0)
  private void cbui$faint(CallbackInfo ci) {
    cbui$applied = false;
    if (!XRayState.active || "translucent".equals(name)) return;
    try {
      cbui$blend = GL11.glIsEnabled(GL11.GL_BLEND);
      cbui$srcRgb = cbui$int(GL_BLEND_SRC_RGB);
      cbui$dstRgb = cbui$int(GL_BLEND_DST_RGB);
      cbui$srcA = cbui$int(GL_BLEND_SRC_ALPHA);
      cbui$dstA = cbui$int(GL_BLEND_DST_ALPHA);
      cbui$depth = cbui$int(GL_DEPTH_WRITEMASK) != 0;
      FLOATS.clear();
      GL11.glGetFloat(GL_BLEND_COLOR, FLOATS);
      for (int i = 0; i < 4; i++) cbui$color[i] = FLOATS.get(i);
      cbui$applied = true;
      GL11.glEnable(GL11.GL_BLEND);
      GL14.glBlendColor(0f, 0f, 0f, XRayState.opacity);
      GL14.glBlendFuncSeparate(GL_CONSTANT_ALPHA, GL_ONE_MINUS_CONSTANT_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
      GL11.glDepthMask(false);
    } catch (Throwable error) {
      cbui$restore();
    }
  }

  @Inject(method = "endDrawing", at = @At("HEAD"), require = 0)
  private void cbui$solid(CallbackInfo ci) {
    cbui$restore();
  }

  private static void cbui$restore() {
    if (!cbui$applied) return;
    cbui$applied = false;
    try {
      GL14.glBlendFuncSeparate(cbui$srcRgb, cbui$dstRgb, cbui$srcA, cbui$dstA);
      GL14.glBlendColor(cbui$color[0], cbui$color[1], cbui$color[2], cbui$color[3]);
      if (cbui$blend) GL11.glEnable(GL11.GL_BLEND); else GL11.glDisable(GL11.GL_BLEND);
      GL11.glDepthMask(cbui$depth);
    } catch (Throwable ignored) {
      // nothing more can be done
    }
  }
}

package dev.cbui.feature.crashguard;

import dev.luka.compat.GlStateManager;
import dev.luka.compat.VertexBuffer;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;

/**
 * Puts rendering back into a state where a screen can be drawn after a frame died half way. Every step is on its
 * own, because on the Vulkan renderers some of them do not apply and may fail.
 */
final class GlRescue {
  private GlRescue() {}

  static void reset(Minecraft mc) {
    step("vertex batch", () -> VertexBuffer.INSTANCE.finishDrawing());
    step("framebuffer", () -> mc.getFramebuffer().unbindFramebuffer());
    step("matrix stacks", () -> {
      unwind(GL11.GL_TEXTURE, GL11.GL_TEXTURE_STACK_DEPTH);
      unwind(GL11.GL_PROJECTION, GL11.GL_PROJECTION_STACK_DEPTH);
      unwind(GL11.GL_MODELVIEW, GL11.GL_MODELVIEW_STACK_DEPTH);
    });
    step("render state", () -> {
      GlStateManager.color(1f, 1f, 1f, 1f);
      GlStateManager.disableLighting();
      GlStateManager.disableFog();
      GlStateManager.enableTexture2D();
      GlStateManager.disableBlend();
      GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
      GlStateManager.enableAlpha();
      GlStateManager.depthMask(true);
      GlStateManager.colorMask(true, true, true, true);
    });
    step("post shader", () -> {
      if (mc.entityRenderer != null) mc.entityRenderer.deactivateShader();
    });
    step("profiler", () -> mc.mcProfiler.clearProfiling());
    step("gl errors", () -> {
      for (int i = 0; i < 64 && GL11.glGetError() != GL11.GL_NO_ERROR; i++) {
        // drain the error queue
      }
    });
  }

  private static void unwind(int mode, int depthQuery) {
    GL11.glMatrixMode(mode);
    int depth = GL11.glGetInteger(depthQuery);
    for (int i = 1; i < depth && i < 64; i++) GL11.glPopMatrix();
  }

  private static void step(String what, Runnable action) {
    try {
      action.run();
    } catch (Throwable ignored) {
      // not drawing, no GL context or a renderer without this state: nothing to undo
    }
  }
}

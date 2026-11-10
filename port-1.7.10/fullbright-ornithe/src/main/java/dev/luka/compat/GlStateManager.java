package dev.luka.compat;

import java.nio.FloatBuffer;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/** Minecraft 1.8's GlStateManager API for 1.7.10, which has no such class. Calls go straight to OpenGL. */
public final class GlStateManager {
  private GlStateManager() { }

  public static void pushMatrix() { GL11.glPushMatrix(); }
  public static void popMatrix() { GL11.glPopMatrix(); }
  public static void matrixMode(int mode) { GL11.glMatrixMode(mode); }
  public static void loadIdentity() { GL11.glLoadIdentity(); }
  public static void translate(float x, float y, float z) { GL11.glTranslatef(x, y, z); }
  public static void translate(double x, double y, double z) { GL11.glTranslated(x, y, z); }
  public static void scale(float x, float y, float z) { GL11.glScalef(x, y, z); }
  public static void scale(double x, double y, double z) { GL11.glScaled(x, y, z); }
  public static void rotate(float angle, float x, float y, float z) { GL11.glRotatef(angle, x, y, z); }
  public static void multMatrix(FloatBuffer matrix) { GL11.glMultMatrix(matrix); }
  public static void getFloat(int name, FloatBuffer out) { GL11.glGetFloat(name, out); }
  public static void ortho(double left, double right, double bottom, double top, double near, double far) { GL11.glOrtho(left, right, bottom, top, near, far); }
  public static void viewport(int x, int y, int width, int height) { GL11.glViewport(x, y, width, height); }

  public static void color(float r, float g, float b, float a) { GL11.glColor4f(r, g, b, a); }
  public static void color(float r, float g, float b) { GL11.glColor4f(r, g, b, 1F); }
  public static void resetColor() { }
  public static void clearColor(float r, float g, float b, float a) { GL11.glClearColor(r, g, b, a); }
  public static void colorMask(boolean r, boolean g, boolean b, boolean a) { GL11.glColorMask(r, g, b, a); }
  public static void shadeModel(int mode) { GL11.glShadeModel(mode); }

  public static void enableBlend() { GL11.glEnable(GL11.GL_BLEND); }
  public static void disableBlend() { GL11.glDisable(GL11.GL_BLEND); }
  public static void blendFunc(int source, int dest) { GL11.glBlendFunc(source, dest); }
  public static void tryBlendFuncSeparate(int sourceRgb, int destRgb, int sourceAlpha, int destAlpha) { OpenGlHelper.glBlendFunc(sourceRgb, destRgb, sourceAlpha, destAlpha); }

  public static void enableAlpha() { GL11.glEnable(GL11.GL_ALPHA_TEST); }
  public static void disableAlpha() { GL11.glDisable(GL11.GL_ALPHA_TEST); }
  public static void alphaFunc(int func, float reference) { GL11.glAlphaFunc(func, reference); }

  public static void enableDepth() { GL11.glEnable(GL11.GL_DEPTH_TEST); }
  public static void disableDepth() { GL11.glDisable(GL11.GL_DEPTH_TEST); }
  public static void depthFunc(int func) { GL11.glDepthFunc(func); }
  public static void depthMask(boolean mask) { GL11.glDepthMask(mask); }

  public static void enableCull() { GL11.glEnable(GL11.GL_CULL_FACE); }
  public static void disableCull() { GL11.glDisable(GL11.GL_CULL_FACE); }
  public static void enableLighting() { GL11.glEnable(GL11.GL_LIGHTING); }
  public static void disableLighting() { GL11.glDisable(GL11.GL_LIGHTING); }
  public static void enableColorMaterial() { GL11.glEnable(GL11.GL_COLOR_MATERIAL); }
  public static void disableColorMaterial() { GL11.glDisable(GL11.GL_COLOR_MATERIAL); }
  public static void enableRescaleNormal() { GL11.glEnable(GL12.GL_RESCALE_NORMAL); }
  public static void disableRescaleNormal() { GL11.glDisable(GL12.GL_RESCALE_NORMAL); }
  public static void enablePolygonOffset() { GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL); }
  public static void disablePolygonOffset() { GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL); }
  public static void doPolygonOffset(float factor, float units) { GL11.glPolygonOffset(factor, units); }

  public static void enableFog() { GL11.glEnable(GL11.GL_FOG); }
  public static void disableFog() { GL11.glDisable(GL11.GL_FOG); }
  public static void setFogDensity(float density) { GL11.glFogf(GL11.GL_FOG_DENSITY, density); }
  public static void setFogStart(float start) { GL11.glFogf(GL11.GL_FOG_START, start); }
  public static void setFogEnd(float end) { GL11.glFogf(GL11.GL_FOG_END, end); }

  public static void enableTexture2D() { GL11.glEnable(GL11.GL_TEXTURE_2D); }
  public static void disableTexture2D() { GL11.glDisable(GL11.GL_TEXTURE_2D); }
  public static void bindTexture(int texture) { GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture); }
  public static void deleteTexture(int texture) { GL11.glDeleteTextures(texture); }
  public static void setActiveTexture(int unit) { OpenGlHelper.setActiveTexture(unit); }
}

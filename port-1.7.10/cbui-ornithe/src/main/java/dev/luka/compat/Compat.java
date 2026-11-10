package dev.luka.compat;

import dev.cbui.mixin.GuiTextFieldAccessor;
import dev.cbui.mixin.TessellatorAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiPlayerInfo;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

/** Small 1.7.10 stand-ins for calls that changed between Minecraft 1.7.10 and 1.8. */
public final class Compat {
  private Compat() {}

  public static ScaledResolution scaled(Minecraft mc) {
    return new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
  }

  /** 1.7.10's FontRenderer only takes int positions: the fraction is applied with the matrix so text keeps its exact place. */
  public static int drawString(FontRenderer font, String text, float x, float y, int color, boolean shadow) {
    int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
    float dx = x - ix, dy = y - iy;
    boolean moved = dx != 0F || dy != 0F;
    if (moved) { GL11.glPushMatrix(); GL11.glTranslatef(dx, dy, 0F); }
    int end = font.drawString(text, ix, iy, color, shadow);
    if (moved) GL11.glPopMatrix();
    return end;
  }

  /** The horizontal angle of a look vector for any entity (only living entities answer getLook in 1.7.10). */
  public static Vec3 look(Entity entity, float partialTicks) {
    if (entity instanceof EntityLivingBase) return ((EntityLivingBase) entity).getLook(partialTicks);
    float yaw = entity.prevRotationYaw + (entity.rotationYaw - entity.prevRotationYaw) * partialTicks;
    float pitch = entity.prevRotationPitch + (entity.rotationPitch - entity.prevRotationPitch) * partialTicks;
    float cosYaw = MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI);
    float sinYaw = MathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);
    float cosPitch = -MathHelper.cos(-pitch * 0.017453292F);
    float sinPitch = MathHelper.sin(-pitch * 0.017453292F);
    return Vec3.createVectorHelper(sinYaw * cosPitch, sinPitch, cosYaw * cosPitch);
  }

  public static void setPosition(GuiTextField field, int x, int y) {
    ((GuiTextFieldAccessor) field).cbui$setX(x);
    ((GuiTextFieldAccessor) field).cbui$setY(y);
  }

  public static int x(GuiTextField field) { return ((GuiTextFieldAccessor) field).cbui$getX(); }

  public static int y(GuiTextField field) { return ((GuiTextFieldAccessor) field).cbui$getY(); }

  private static final RenderItem ITEMS = new RenderItem();

  public static void renderItem(Minecraft mc, ItemStack stack, int x, int y) {
    ITEMS.renderItemAndEffectIntoGUI(mc.fontRendererObj, mc.getTextureManager(), stack, x, y);
  }

  /** The frame rate from the debug text ("123 fps, 45 chunk updates"); 1.7.10 keeps the number itself private. */
  public static int fps(Minecraft mc) {
    String debug = mc.debug;
    if (debug == null) return 0;
    int cut = debug.indexOf(' ');
    if (cut <= 0) return 0;
    try { return Integer.parseInt(debug.substring(0, cut)); } catch (NumberFormatException e) { return 0; }
  }

  /** A player's latency from the tab list, or -1 when the name is not in it. */
  public static int ping(NetHandlerPlayClient net, String name) {
    for (Object entry : net.playerInfoList) {
      GuiPlayerInfo info = (GuiPlayerInfo) entry;
      if (info.name.equals(name)) return info.responseTime;
    }
    return -1;
  }

  /** Ends the Tessellator's current primitive without drawing it. */
  public static void discardTessellator() {
    TessellatorAccessor tessellator = (TessellatorAccessor) Tessellator.instance;
    tessellator.cbui$reset();
    tessellator.cbui$setDrawing(false);
  }
}

package dev.cbui.gui;

import dev.cbui.Cbui;
import dev.cbui.cosmetics.Capes;
import dev.cbui.font.Fonts;
import dev.cbui.render.Draw;
import dev.cbui.social.CapeArt;
import dev.cbui.social.LukaNet;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** The cape picker: a searchable grid of every cape in the library, click one to wear it. */
public final class CosmeticsScreen extends GuiScreen {
  private static final int CELL_W = 74, CELL_H = 84, GAP = 6, THUMB_W = 66, THUMB_H = 51;
  private static final ExecutorService LOADER = Executors.newFixedThreadPool(2, r -> {
    Thread t = new Thread(r, "luka-cape-thumbs");
    t.setDaemon(true);
    return t;
  });

  private final GuiScreen parent;
  private GuiTextField search;
  /** One cape in the grid, whichever list it came from. */
  private static final class Item {
    final String id, name, search, note;
    final boolean animated, locked;

    Item(String id, String name, String note, boolean animated, boolean locked) {
      this.id = id;
      this.name = name;
      this.note = note;
      this.animated = animated;
      this.locked = locked;
      this.search = (name + " " + id).toLowerCase(java.util.Locale.ROOT);
    }
  }

  /** True lists the capes of the luka account (from the service); false the cape library in the game folder. */
  private boolean account;
  private final java.util.Set<String> libraryAsked = new java.util.HashSet<>();
  private List<Item> shown = new ArrayList<>();
  private String lastQuery = "\u0000";
  private int lastCount = -1;
  private float scroll, scrollTarget;
  private final Map<String, ResourceLocation> thumbs = new HashMap<>();
  private final Map<String, BufferedImage> decoded = new ConcurrentHashMap<>();
  private final Map<String, Boolean> requested = new HashMap<>();
  private static final int PREVIEW_W = 132;
  private int px, py, pw, ph, gridTop, gridBottom, columns;
  private String hoveredId = "";
  /** 0 all, 1 animated, 2 still. */
  private int filter;
  private float spin = 180f; // the player is shown from behind; dragging turns it
  private boolean dragging;
  private int dragX;

  public CosmeticsScreen(GuiScreen parent) {
    this.parent = parent;
  }

  @Override
  public void initGui() {
    Keyboard.enableRepeatEvents(true);
    search = new GuiTextField(0, fontRendererObj, 0, 0, 150, 14);
    search.setMaxStringLength(40);
    search.setFocused(true);
    Capes.entries(); // starts loading the library
    account = LukaNet.connected() && !Capes.library().isDirectory();
    LukaNet.refreshCapes();
  }

  private void setAccount(boolean value) {
    if (account == value) return;
    account = value;
    thumbs.clear();
    decoded.clear();
    requested.clear();
    libraryAsked.clear();
    lastQuery = "\u0000";
    if (value) LukaNet.refreshCapes();
  }

  /** The id of the cape being worn, in the list on screen ("" when none). */
  private String activeId() {
    if (!account) return Capes.selected();
    String pick = LukaNet.selectedCape();
    if ("none".equals(pick)) return "";
    if (!pick.isEmpty()) return pick;
    LukaNet.Info me = LukaNet.self();
    return me == null ? "" : me.cape;
  }

  private String libraryStatus() {
    Capes.Entry worn = Capes.find(Capes.selected());
    return !Capes.ready() ? "Loading the cape library..." : Capes.entries().isEmpty() ? "No capes found in " + Capes.library()
        : worn != null ? "Wearing: " + worn.name : "No cape. " + Capes.entries().size() + " available";
  }

  private String accountStatus() {
    if (!LukaNet.connected()) return "Not connected to luka online (" + LukaNet.status() + ")";
    if (!LukaNet.capesLoaded()) return "Loading your capes...";
    String id = activeId();
    String name = "";
    for (LukaNet.CapeEntry c : LukaNet.capes()) if (c.id.equals(id)) name = c.name;
    String action = LukaNet.lastAction();
    return (id.isEmpty() ? "No cape" : "Wearing: " + name) + (action.isEmpty() || "...".equals(action) ? "" : "   " + action);
  }

  @Override
  public void onGuiClosed() {
    Keyboard.enableRepeatEvents(false);
  }

  private void layoutPanel() {
    pw = Math.min(width - 24, 640);
    ph = height - 24;
    px = (width - pw) / 2;
    py = 12;
    gridTop = py + 64;
    gridBottom = py + ph - 8;
    columns = Math.max(1, (pw - 24 - PREVIEW_W + GAP) / (CELL_W + GAP));
  }

  private void refilter() {
    String q = search.getText().trim().toLowerCase(java.util.Locale.ROOT);
    List<Item> all = new ArrayList<>();
    if (account) {
      for (LukaNet.CapeEntry c : LukaNet.capes()) {
        boolean rank = "rank".equals(c.kind);
        all.add(new Item(c.id, c.name, c.owned ? (rank ? "rank cape" : "owned") : (rank ? "rank only" : "locked"), c.animated, !c.owned));
      }
    } else {
      for (Capes.Entry e : Capes.entries()) all.add(new Item(e.id, e.name, "", e.frames > 1 && e.motion >= 3.0, false));
    }
    String key = q + "|" + filter + "|" + account;
    if (key.equals(lastQuery) && all.size() == lastCount) return;
    lastQuery = key;
    lastCount = all.size();
    shown = new ArrayList<>();
    for (Item e : all) {
      if (filter == 1 && !e.animated) continue;
      if (filter == 2 && e.animated) continue;
      if (q.isEmpty() || e.search.contains(q)) shown.add(e);
    }
    scrollTarget = 0;
  }

  @Override
  public void drawScreen(int mouseX, int mouseY, float partialTicks) {
    try {
      layoutPanel();
      refilter();
      drawGradientRect(0, 0, width, height, 0xB0100C13, 0xE017121C);
      Draw.rounded(px - 1, py - 1, px + pw + 1, py + ph + 1, 8, 0xFF3A2C52);
      Draw.rounded(px, py, px + pw, py + ph, 8, 0xF50B0910);
      Fonts.bold(14).drawString("COSMETICS", px + 12, py + 10, 0xFFD9C2FF);
      Fonts.regular(10).drawString(account ? accountStatus() : libraryStatus(), px + 12, py + 49, 0xFF8C849A);
      // which list: the cape library in the game folder, or the capes of the luka account
      String[] modes = {"LIBRARY", "ACCOUNT"};
      int mx0 = px + 12;
      for (int i = 0; i < modes.length; i++) {
        int mw = Fonts.bold(9).getStringWidth(modes[i]) + 16;
        boolean on = (i == 1) == account, over = Draw.inside(mouseX, mouseY, mx0, py + 31, mw, 15);
        Draw.rounded(mx0, py + 31, mx0 + mw, py + 46, 5, on ? 0xFF9D4EDD : over ? 0xFF3A2C52 : 0xFF2A2037);
        Fonts.bold(9).drawString(modes[i], mx0 + 8, py + 35, on ? 0xFFFFFFFF : 0xFFB8AFC9);
        mx0 += mw + 4;
      }
      // search box and remove button
      search.xPosition = px + pw - 12 - 150 - 74;
      search.yPosition = py + 12;
      search.drawTextBox();
      if (search.getText().isEmpty() && !search.isFocused()) Fonts.regular(10).drawString("Search", search.xPosition + 4, search.yPosition + 3, 0xFF5E5670);
      boolean removeHover = Draw.inside(mouseX, mouseY, px + pw - 12 - 68, py + 10, 68, 18);
      Draw.rounded(px + pw - 12 - 68, py + 10, px + pw - 12, py + 28, 5, removeHover ? 0xFF3A2C52 : 0xFF2A2037);
      Fonts.bold(9).drawCentered("NO CAPE", px + pw - 12 - 34, py + 15, 0xFFD9C2FF);
      String[] chips = {"ALL", "ANIMATED", "STILL"};
      int cxp = px + pw - 12;
      for (int i = chips.length - 1; i >= 0; i--) {
        int cw = Fonts.bold(9).getStringWidth(chips[i]) + 16;
        cxp -= cw;
        boolean on = filter == i, over = Draw.inside(mouseX, mouseY, cxp, py + 31, cw, 15);
        Draw.rounded(cxp, py + 31, cxp + cw, py + 46, 5, on ? 0xFF9D4EDD : over ? 0xFF3A2C52 : 0xFF2A2037);
        Fonts.bold(9).drawString(chips[i], cxp + 8, py + 35, on ? 0xFFFFFFFF : 0xFFB8AFC9);
        cxp -= 4;
      }

      // grid
      int rows = (shown.size() + columns - 1) / columns;
      int content = rows * (CELL_H + GAP);
      int viewport = gridBottom - gridTop;
      scrollTarget = Math.max(0, Math.min(Math.max(0, content - viewport), scrollTarget));
      scroll += (scrollTarget - scroll) * 0.5f;
      UiWidgets.beginClip(px + 4, gridTop, pw - 8, viewport); // GUI units (beginClipPx is for the pixel-laid-out luka menu)
      int x0 = px + 12;
      int budget = 3; // thumbnails turned into textures this frame
      String wornId = activeId();
      for (int i = 0; i < shown.size(); i++) {
        int row = i / columns, col = i % columns;
        float cy = gridTop + row * (CELL_H + GAP) - scroll;
        if (cy + CELL_H < gridTop || cy > gridBottom) continue;
        float cx = x0 + col * (CELL_W + GAP);
        Item e = shown.get(i);
        boolean hover = Draw.inside(mouseX, mouseY, cx, cy, CELL_W, CELL_H) && mouseY >= gridTop && mouseY < gridBottom;
        if (hover) hoveredId = e.id;
        boolean active = e.id.equals(wornId);
        Draw.rounded(cx, cy, cx + CELL_W, cy + CELL_H, 6, active ? 0xFF3B2A57 : hover && !e.locked ? 0xFF2A2037 : 0xFF1B1524);
        if (active) Draw.rounded(cx, cy + CELL_H - 3, cx + CELL_W, cy + CELL_H, 2, 0xFF9D4EDD);
        ResourceLocation thumb = thumbs.get(e.id);
        if (thumb == null) {
          BufferedImage image = decoded.remove(e.id);
          if (image != null && budget-- > 0) {
            DynamicTexture t = new DynamicTexture(image);
            thumb = mc.getTextureManager().getDynamicTextureLocation("luka_thumb", t);
            thumbs.put(e.id, thumb);
          } else if (image != null) {
            decoded.put(e.id, image);
          } else if (account) {
            if (requested.put(e.id, Boolean.TRUE) == null) CapeArt.load(e.id, decoded);
          } else if (requested.put(e.id, Boolean.TRUE) == null && libraryAsked.add(e.id)) {
            final String id = e.id;
            LOADER.execute(() -> {
              try {
                decoded.put(id, ImageIO.read(new File(new File(new File(Capes.library(), "capes"), id), "thumb.png")));
              } catch (Throwable ignored) {
                // no thumbnail: the cell stays empty
              }
            });
          }
        }
        if (thumb != null) Draw.texture(thumb, cx + 4, cy + 4, THUMB_W, THUMB_H, e.locked ? .4f : 1f);
        String label = ellipsize(e.name, CELL_W - 6);
        Fonts.regular(9).drawString(label, cx + 4, cy + 4 + THUMB_H + 5, active ? 0xFFFFFFFF : e.locked ? 0xFF6F6580 : 0xFFB8AFC9);
        String sub = e.locked ? e.note : e.animated ? "animated" : account ? e.note : "";
        if (!sub.isEmpty()) Fonts.regular(8).drawString(sub, cx + 4, cy + 4 + THUMB_H + 16, e.locked ? 0xFF8C5A6A : 0xFF8C6AC8);
      }
      UiWidgets.endClip();
      drawPreview(mouseX, mouseY, partialTicks);
      if (content > viewport) {
        float frac = scroll / (content - viewport);
        int barH = Math.max(20, (int) ((float) viewport * viewport / content));
        int by = gridTop + (int) ((viewport - barH) * frac);
        Draw.rounded(px + pw - PREVIEW_W - 16, by, px + pw - PREVIEW_W - 13, by + barH, 1.5f, 0xFF3A2C52);
      }
    } catch (Throwable error) {
      Cbui.reportOnce("cosmetics screen", error);
      mc.displayGuiScreen(parent);
    }
  }

  /** The right column: the player wearing the cape (in a world), or the cape itself (on the title screen). */
  private void drawPreview(int mouseX, int mouseY, float partialTicks) {
    int bx = px + pw - PREVIEW_W - 8, by = gridTop, bw = PREVIEW_W, bh = gridBottom - gridTop;
    Draw.rounded(bx, by, bx + bw, by + bh, 6, 0xFF120E19);
    Fonts.bold(10).drawString("PREVIEW", bx + 8, by + 8, 0xFFD9C2FF);
    String id = account ? activeId() : Capes.selected();
    if (mc.thePlayer != null && mc.theWorld != null) {
      if (id.isEmpty()) {
        Fonts.regular(9).drawString("No cape chosen", bx + 8, by + 24, 0xFF6F6580);
      }
      drawPlayer(bx + bw / 2, by + bh - 24, Math.min(70, bh / 3), spin);
      Fonts.regular(8).drawString("Drag to turn", bx + 8, by + 22, 0xFF6F6580);
    } else {
      ResourceLocation worn = account ? dev.cbui.social.RemoteCapes.textureFor(java.util.UUID.fromString(LukaNet.self() == null ? "00000000-0000-0000-0000-000000000000" : LukaNet.self().uuid)) : Capes.current();
      if (worn != null) {
        // the outer face of the cape: 10 x 16 of the 22 x 17 cape area
        region(worn, bx + (bw - 80) / 2f, by + 28, 80, 128, 1f / 22f, 1f / 17f, 11f / 22f, 17f / 17f);
        String shownName = null;
        for (Item it : shown) if (it.id.equals(id)) shownName = it.name;
        if (shownName != null) Fonts.regular(9).drawCentered(ellipsize(shownName, bw - 12), bx + bw / 2f, by + 164, 0xFFB8AFC9);
      } else {
        Fonts.regular(9).drawString("No cape chosen", bx + 8, by + 24, 0xFF6F6580);
      }
    }
    // the cape under the pointer, from its thumbnail
    ResourceLocation hovered = hoveredId.isEmpty() ? null : thumbs.get(hoveredId);
    if (hovered != null && !hoveredId.equals(id)) {
      Fonts.regular(8).drawString("UNDER THE POINTER", bx + 8, by + bh - 54, 0xFF6F6580);
      region(hovered, bx + 8, by + bh - 44, 30, 40, 3f / 66f, 3f / 51f, 33f / 66f, 51f / 51f);
    }
    hoveredId = "";
  }

  private static void region(ResourceLocation texture, float x, float y, float w, float h, float u0, float v0, float u1, float v1) {
    net.minecraft.client.Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
    net.minecraft.client.renderer.GlStateManager.enableTexture2D();
    net.minecraft.client.renderer.GlStateManager.enableBlend();
    net.minecraft.client.renderer.GlStateManager.color(1f, 1f, 1f, 1f);
    net.minecraft.client.renderer.Tessellator tess = net.minecraft.client.renderer.Tessellator.getInstance();
    net.minecraft.client.renderer.WorldRenderer wr = tess.getWorldRenderer();
    wr.begin(7, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_TEX);
    wr.pos(x, y + h, 0).tex(u0, v1).endVertex();
    wr.pos(x + w, y + h, 0).tex(u1, v1).endVertex();
    wr.pos(x + w, y, 0).tex(u1, v0).endVertex();
    wr.pos(x, y, 0).tex(u0, v0).endVertex();
    tess.draw();
  }

  /** The local player, turning slowly, so the cape is seen from every side (the same drawing the inventory uses). */
  private void drawPlayer(int x, int y, int scale, float spin) {
    net.minecraft.client.entity.EntityPlayerSP ent = mc.thePlayer;
    net.minecraft.client.renderer.GlStateManager.enableColorMaterial();
    net.minecraft.client.renderer.GlStateManager.pushMatrix();
    net.minecraft.client.renderer.GlStateManager.translate((float) x, (float) y, 50.0F);
    net.minecraft.client.renderer.GlStateManager.scale((float) -scale, (float) scale, (float) scale);
    net.minecraft.client.renderer.GlStateManager.rotate(180.0F, 0.0F, 0.0F, 1.0F);
    float bodyYaw = ent.renderYawOffset, yaw = ent.rotationYaw, pitch = ent.rotationPitch, prevHead = ent.prevRotationYawHead, head = ent.rotationYawHead;
    float prevBody = ent.prevRenderYawOffset, prevYaw = ent.prevRotationYaw, prevPitch = ent.prevRotationPitch;
    net.minecraft.client.renderer.GlStateManager.rotate(135.0F, 0.0F, 1.0F, 0.0F);
    net.minecraft.client.renderer.RenderHelper.enableStandardItemLighting();
    net.minecraft.client.renderer.GlStateManager.rotate(-135.0F, 0.0F, 1.0F, 0.0F);
    ent.renderYawOffset = ent.prevRenderYawOffset = spin;
    ent.rotationYaw = ent.prevRotationYaw = spin;
    ent.rotationPitch = ent.prevRotationPitch = 0.0F;
    ent.rotationYawHead = ent.prevRotationYawHead = spin;
    net.minecraft.client.renderer.GlStateManager.translate(0.0F, 0.0F, 0.0F);
    net.minecraft.client.renderer.entity.RenderManager rm = mc.getRenderManager();
    rm.setPlayerViewY(180.0F);
    rm.setRenderShadow(false);
    rm.renderEntityWithPosYaw(ent, 0.0D, 0.0D, 0.0D, 0.0F, 1.0F);
    rm.setRenderShadow(true);
    ent.renderYawOffset = bodyYaw;
    ent.prevRenderYawOffset = prevBody;
    ent.rotationYaw = yaw;
    ent.prevRotationYaw = prevYaw;
    ent.rotationPitch = pitch;
    ent.prevRotationPitch = prevPitch;
    ent.prevRotationYawHead = prevHead;
    ent.rotationYawHead = head;
    net.minecraft.client.renderer.GlStateManager.popMatrix();
    net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
    net.minecraft.client.renderer.GlStateManager.disableRescaleNormal();
    net.minecraft.client.renderer.GlStateManager.setActiveTexture(net.minecraft.client.renderer.OpenGlHelper.lightmapTexUnit);
    net.minecraft.client.renderer.GlStateManager.disableTexture2D();
    net.minecraft.client.renderer.GlStateManager.setActiveTexture(net.minecraft.client.renderer.OpenGlHelper.defaultTexUnit);
  }

  private static String ellipsize(String text, int width) {
    if (Fonts.regular(9).getStringWidth(text) <= width) return text;
    String out = text;
    while (out.length() > 1 && Fonts.regular(9).getStringWidth(out + "..") > width) out = out.substring(0, out.length() - 1);
    return out + "..";
  }

  @Override
  protected void mouseClicked(int mouseX, int mouseY, int button) {
    try {
      if (button != 0) return;
      layoutPanel();
      search.mouseClicked(mouseX, mouseY, button);
      int mx0 = px + 12;
      String[] modes = {"LIBRARY", "ACCOUNT"};
      for (int i = 0; i < modes.length; i++) {
        int mw = Fonts.bold(9).getStringWidth(modes[i]) + 16;
        if (Draw.inside(mouseX, mouseY, mx0, py + 31, mw, 15)) {
          setAccount(i == 1);
          return;
        }
        mx0 += mw + 4;
      }
      if (Draw.inside(mouseX, mouseY, px + pw - 12 - 68, py + 10, 68, 18)) {
        if (account) {
          Capes.select("");
          LukaNet.equipCape("none");
        } else {
          Capes.select("");
        }
        return;
      }
      String[] chips = {"ALL", "ANIMATED", "STILL"};
      int cxp = px + pw - 12;
      for (int i = chips.length - 1; i >= 0; i--) {
        int cw = Fonts.bold(9).getStringWidth(chips[i]) + 16;
        cxp -= cw;
        if (Draw.inside(mouseX, mouseY, cxp, py + 31, cw, 15)) {
          filter = i;
          return;
        }
        cxp -= 4;
      }
      if (Draw.inside(mouseX, mouseY, px + pw - PREVIEW_W - 8, gridTop, PREVIEW_W, gridBottom - gridTop)) {
        dragging = true;
        dragX = mouseX;
        return;
      }
      if (mouseY < gridTop || mouseY >= gridBottom) return;
      int x0 = px + 12;
      for (int i = 0; i < shown.size(); i++) {
        int row = i / columns, col = i % columns;
        float cy = gridTop + row * (CELL_H + GAP) - scroll;
        float cx = x0 + col * (CELL_W + GAP);
        if (Draw.inside(mouseX, mouseY, cx, cy, CELL_W, CELL_H)) {
          Item item = shown.get(i);
          if (account) {
            if (item.locked) return;
            Capes.select(""); // the account cape shows instead of one from the library
            LukaNet.equipCape(item.id);
          } else {
            Capes.select(item.id);
          }
          return;
        }
      }
    } catch (Throwable error) {
      Cbui.reportOnce("cosmetics click", error);
    }
  }

  @Override
  protected void mouseClickMove(int mouseX, int mouseY, int button, long timeSinceLastClick) {
    if (dragging) {
      spin += (mouseX - dragX) * 1.6f;
      dragX = mouseX;
    }
  }

  @Override
  protected void mouseReleased(int mouseX, int mouseY, int state) {
    dragging = false;
  }

  @Override
  public void handleMouseInput() {
    super.handleMouseInput();
    int wheel = Mouse.getEventDWheel();
    if (wheel != 0) scrollTarget -= Integer.signum(wheel) * (CELL_H + GAP) * 1.5f; // a row and a half per notch
  }

  @Override
  protected void keyTyped(char typedChar, int keyCode) {
    if (keyCode == Keyboard.KEY_NEXT) scrollTarget += gridBottom - gridTop - CELL_H;
    if (keyCode == Keyboard.KEY_PRIOR) scrollTarget -= gridBottom - gridTop - CELL_H;
    if (keyCode == Keyboard.KEY_ESCAPE) {
      mc.displayGuiScreen(parent);
      return;
    }
    search.textboxKeyTyped(typedChar, keyCode);
  }

  @Override
  public void updateScreen() {
    if (search != null) search.updateCursorCounter();
  }

  @Override
  public boolean doesGuiPauseGame() {
    return false;
  }
}

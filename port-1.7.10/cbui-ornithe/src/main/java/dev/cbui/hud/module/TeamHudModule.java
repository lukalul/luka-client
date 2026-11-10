package dev.cbui.hud.module;

import dev.cbui.hud.AbstractHudModule;
import dev.cbui.hud.Anchor;
import dev.cbui.hud.Setting;
import dev.cbui.render.Draw;
import dev.cbui.social.LukaNet;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.gui.Gui;
import dev.luka.compat.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * A live list of your teammates: face, name, health bar, armour left, distance and an arrow that points at them.
 * A teammate is someone on your scoreboard team (when the server uses small teams), a luka friend, a name you typed,
 * or a member the server reports through the Apollo team view (those can be shown even beyond render distance).
 */
public final class TeamHudModule extends AbstractHudModule {
  private static final int FACE = 9, ROW = 12, BAR_W = 34, BAR_H = 5, ARROW = 9, MAX_TEAM_SIZE = 30;

  private final Setting.Toggle useTeam = grouped("Who counts", add(new Setting.Toggle("Scoreboard team", true, "People on your scoreboard team. Ignored when the team is huge (the server puts everyone in one)")));
  private final Setting.Toggle useFriends = grouped("Who counts", add(new Setting.Toggle("luka friends", true, "Your luka friends while they are near you in the world")));
  private final Setting.Toggle useApollo = grouped("Who counts", add(new Setting.Toggle("Server team view", true, "Members the server reports itself (also shown beyond render distance, without health)")));
  private final Setting.Text names = grouped("Who counts", add(new Setting.Text("Extra names", "", "Names to always treat as teammates, separated by commas")));
  private final Setting.Slider maxRows = grouped("Display", add(new Setting.Slider("Max rows", 6, 1, 12, 1, "How many teammates to list at most")));
  private final Setting.Choice sort = grouped("Display", add(Setting.tip(new Setting.Choice("Sort by", "Distance", "Distance", "Health", "Name"), "Who comes first in the list")));
  private final Setting.Toggle showArmour = grouped("Display", add(new Setting.Toggle("Show armour", true, "The average durability left on their worn armour")));
  private final Setting.Toggle showDistance = grouped("Display", add(new Setting.Toggle("Show distance", true, "How many blocks away they are")));
  private final Setting.Toggle showArrow = grouped("Display", add(new Setting.Toggle("Show arrow", true, "An arrow that points toward them from where you are looking")));
  private final Setting.Color lowColor = grouped("Display", adv(new Setting.Color("Low health color", 0xFFFF5555, "Colour of the health bar when someone is nearly dead")));
  private final Setting.Color fullColor = grouped("Display", adv(new Setting.Color("Full health color", 0xFF55FF55, "Colour of the health bar when someone is healthy")));

  /** One listed teammate; arrays are reused between refreshes only through {@link #rows}'s replacement. */
  private static final class Row {
    String name;
    ResourceLocation skin;
    float health = -1, max = 20;
    float armour = -1;
    int distance;
    float bearing;
    boolean far;
  }

  private String lastRaw;
  private Set<String> lastExtra = new HashSet<String>();
  private List<Row> rows = new ArrayList<Row>();
  private int tickCounter;
  private int nameWidth = 40;
  private boolean lastArmour, lastDistance, lastArrow;

  // reflection into envoy's team state (optional mod)
  private static boolean apolloTried;
  private static Method apolloMembers;
  private static Field apolloUuid, apolloX, apolloY, apolloZ;

  public TeamHudModule() {
    super("Team HUD", false, Anchor.CENTER_LEFT, 5, -40, 120, 40);
    background.set(true);
  }

  @Override public void tick() {
    if (mc.thePlayer == null || mc.theWorld == null) {
      if (!rows.isEmpty()) rows = new ArrayList<Row>();
      return;
    }
    // the list is rebuilt a few times a second: players do not change team every tick
    if (tickCounter++ % 4 != 0 && lastArmour == showArmour.get() && lastDistance == showDistance.get() && lastArrow == showArrow.get()) return;
    lastArmour = showArmour.get();
    lastDistance = showDistance.get();
    lastArrow = showArrow.get();
    rebuild();
  }

  private void rebuild() {
    List<Row> out = new ArrayList<Row>();
    Set<UUID> seen = new HashSet<UUID>();
    String raw = names.get();
    if (!raw.equals(lastRaw)) {
      lastRaw = raw;
      lastExtra = splitNames(raw);
    }
    Set<String> extra = lastExtra;
    Set<String> friendNames = new HashSet<String>();
    Set<String> friendIds = new HashSet<String>();
    if (useFriends.get()) {
      LukaNet.FriendsData fd = LukaNet.friends();
      if (fd != null && fd.friends != null) {
        for (LukaNet.Person p : fd.friends) {
          if (p.name != null) friendNames.add(p.name.toLowerCase(Locale.ROOT));
          if (p.uuid != null) friendIds.add(p.uuid.replace("-", "").toLowerCase(Locale.ROOT));
        }
      }
    }
    boolean teamOk = useTeam.get() && mc.thePlayer.getTeam() != null && teamSize(mc.thePlayer.getTeam()) <= MAX_TEAM_SIZE;
    for (Object o : mc.theWorld.playerEntities) {
      if (!(o instanceof EntityPlayer)) continue;
      EntityPlayer p = (EntityPlayer) o;
      if (p == mc.thePlayer || p.isInvisible() && p.getTeam() == null) continue;
      String lower = p.getCommandSenderName().toLowerCase(Locale.ROOT);
      boolean mate = extra.contains(lower)
          || friendNames.contains(lower)
          || friendIds.contains(p.getUniqueID().toString().replace("-", "").toLowerCase(Locale.ROOT))
          || teamOk && p.getTeam() != null && p.isOnSameTeam(mc.thePlayer);
      if (!mate) continue;
      Row r = new Row();
      r.name = p.getCommandSenderName();
      r.skin = p instanceof net.minecraft.client.entity.AbstractClientPlayer ? ((net.minecraft.client.entity.AbstractClientPlayer) p).getLocationSkin() : null;
      r.health = p.getHealth() + p.getAbsorptionAmount();
      r.max = Math.max(1, p.getMaxHealth());
      r.armour = armour(p);
      r.distance = (int) p.getDistanceToEntity(mc.thePlayer);
      r.bearing = bearing(p.posX - mc.thePlayer.posX, p.posZ - mc.thePlayer.posZ);
      out.add(r);
      seen.add(p.getUniqueID());
    }
    if (useApollo.get()) addApollo(out, seen);
    sortRows(out);
    int cap = Math.round(maxRows.get());
    while (out.size() > cap) out.remove(out.size() - 1);
    rows = out;
    layout();
  }

  private static int teamSize(Team team) {
    try {
      return team instanceof net.minecraft.scoreboard.ScorePlayerTeam ? ((net.minecraft.scoreboard.ScorePlayerTeam) team).getMembershipCollection().size() : 0;
    } catch (RuntimeException e) {
      return 0;
    }
  }

  private static Set<String> splitNames(String raw) {
    Set<String> set = new HashSet<String>();
    if (raw == null) return set;
    for (String part : raw.split("[,;\\s]+")) if (!part.isEmpty()) set.add(part.toLowerCase(Locale.ROOT));
    return set;
  }

  /** Average durability left over the worn armour pieces, 0..1, or -1 when nothing is worn. */
  private static float armour(EntityPlayer p) {
    float sum = 0;
    int n = 0;
    for (int i = 0; i < 4; i++) {
      ItemStack s = p.getCurrentArmor(i);
      if (s == null) continue;
      int max = s.getMaxDurability();
      sum += max <= 0 ? 1f : (max - s.getCurrentDurability()) / (float) max;
      n++;
    }
    return n == 0 ? -1 : sum / n;
  }

  /** Degrees to turn from straight ahead to face the offset (positive = to the right). */
  private float bearing(double dx, double dz) {
    double target = Math.toDegrees(Math.atan2(-dx, dz));
    double rel = target - mc.thePlayer.rotationYaw;
    rel = ((rel % 360) + 540) % 360 - 180;
    return (float) rel;
  }

  private void sortRows(List<Row> list) {
    final String mode = sort.get();
    java.util.Collections.sort(list, new java.util.Comparator<Row>() {
      @Override public int compare(Row a, Row b) {
        if (a.far != b.far) return a.far ? 1 : -1;
        if ("Health".equals(mode)) return Float.compare(a.health < 0 ? 99 : a.health, b.health < 0 ? 99 : b.health);
        if ("Name".equals(mode)) return a.name.compareToIgnoreCase(b.name);
        return Integer.compare(a.distance, b.distance);
      }
    });
  }

  // ---- Apollo team view (envoy), optional -------------------------------------------------------------------------

  private void addApollo(List<Row> out, Set<UUID> seen) {
    if (!apolloTried) {
      apolloTried = true;
      try {
        Class<?> state = Class.forName("dev.envoy.module.team.TeamState");
        apolloMembers = state.getMethod("members");
        Class<?> member = Class.forName("dev.envoy.module.team.TeamState$Member");
        apolloUuid = member.getField("uuid");
        apolloX = member.getField("x");
        apolloY = member.getField("y");
        apolloZ = member.getField("z");
      } catch (Throwable t) {
        apolloMembers = null;
      }
    }
    if (apolloMembers == null) return;
    try {
      List<?> members = (List<?>) apolloMembers.invoke(null);
      for (int i = 0, n = members.size(); i < n; i++) {
        Object m = members.get(i);
        UUID id = (UUID) apolloUuid.get(m);
        if (id == null || id.equals(mc.thePlayer.getUniqueID()) || seen.contains(id)) continue;
        double dx = apolloX.getDouble(m) - mc.thePlayer.posX, dy = apolloY.getDouble(m) - mc.thePlayer.posY, dz = apolloZ.getDouble(m) - mc.thePlayer.posZ;
        // the 1.7.10 tab list only knows names, so a teammate out of render range is shown by id without a head
        Row r = new Row();
        r.name = id.toString().substring(0, 8);
        r.skin = null;
        r.distance = (int) Math.sqrt(dx * dx + dy * dy + dz * dz);
        r.bearing = bearing(dx, dz);
        r.far = true;
        out.add(r);
        seen.add(id);
      }
    } catch (Throwable t) {
      apolloMembers = null;
    }
  }

  // ---- layout and drawing -----------------------------------------------------------------------------------------

  private void layout() {
    int widest = 24;
    for (Row r : rows) widest = Math.max(widest, textWidth(r.name));
    nameWidth = Math.min(80, widest);
    resizeFor(rows.size());
  }

  private void resizeFor(int count) {
    int w = FACE + 3 + nameWidth + 4 + BAR_W;
    if (showArmour.get()) w += 4 + textWidth("100%");
    if (showDistance.get()) w += 4 + textWidth("999m");
    if (showArrow.get()) w += 4 + ARROW;
    size(w, Math.max(1, count) * ROW - 2);
  }

  @Override protected boolean nothingToShow() {
    return rows.isEmpty();
  }

  @Override protected void draw(float partialTicks, boolean editor) {
    List<Row> list = rows;
    if (list.isEmpty()) {
      if (!editor) return;
      list = samples();
      nameWidth = Math.max(nameWidth, 30);
      resizeFor(list.size());
    }
    float w = contentWidth();
    for (int i = 0; i < list.size(); i++) drawRow(list.get(i), 0, i * ROW, w);
  }

  private List<Row> samples() {
    List<Row> out = new ArrayList<Row>();
    String[] n = {"Kira", "Mochi", "Vex"};
    float[] hp = {20, 12, 5};
    float[] ar = {.95f, .6f, .2f};
    for (int i = 0; i < n.length; i++) {
      Row r = new Row();
      r.name = n[i];
      r.health = hp[i];
      r.armour = ar[i];
      r.distance = 6 + i * 11;
      r.bearing = -70 + i * 80;
      out.add(r);
    }
    return out;
  }

  private void drawRow(Row r, float x, float y, float width) {
    float cx = x;
    // face
    if (r.skin != null) {
      mc.getTextureManager().bindTexture(r.skin);
      GlStateManager.color(1, 1, 1, r.far ? .6f : 1f);
      Gui.drawScaledCustomSizeModalRect(Math.round(cx), Math.round(y + 1), 8, 8, 8, 8, FACE, FACE, 64, 64);
      Gui.drawScaledCustomSizeModalRect(Math.round(cx), Math.round(y + 1), 40, 8, 8, 8, FACE, FACE, 64, 64);
      GlStateManager.color(1, 1, 1, 1);
    } else {
      Draw.rect(cx, y + 1, cx + FACE, y + 1 + FACE, 0xFF55486B);
    }
    cx += FACE + 3;
    int color = r.far ? 0xFFA0A8B5 : textColor.get();
    text(r.name, cx, y + (ROW - 2 - lineHeight()) / 2f + 1, color);
    cx += nameWidth + 4;
    // health bar
    float barY = y + (ROW - 2 - BAR_H) / 2f + 1;
    Draw.rect(cx, barY, cx + BAR_W, barY + BAR_H, 0x90000000);
    if (r.health >= 0) {
      float frac = Math.max(0f, Math.min(1f, r.health / r.max));
      Draw.rect(cx, barY, cx + BAR_W * frac, barY + BAR_H, Draw.lerpColor(lowColor.get(), fullColor.get(), frac));
    } else {
      Draw.rect(cx, barY, cx + BAR_W, barY + BAR_H, 0x40FFFFFF);
    }
    cx += BAR_W + 4;
    if (showArmour.get()) {
      String a = r.armour < 0 ? "--" : Math.round(r.armour * 100) + "%";
      int ac = r.armour < 0 ? 0xFF7A8290 : Draw.lerpColor(lowColor.get(), fullColor.get(), r.armour);
      text(a, cx, y + (ROW - 2 - lineHeight()) / 2f + 1, ac);
      cx += textWidth("100%") + 4;
    }
    if (showDistance.get()) {
      text(r.distance + "m", cx, y + (ROW - 2 - lineHeight()) / 2f + 1, 0xFFC9B8F0);
      cx += textWidth("999m") + 4;
    }
    if (showArrow.get()) arrow(cx + ARROW / 2f, y + (ROW - 2) / 2f + 1, r.bearing, r.far ? 0xFFA0A8B5 : 0xFF6BE675);
  }

  /** A small arrow centred on (x, y), turned by {@code degrees} clockwise from pointing up. */
  private static void arrow(float x, float y, float degrees, int argb) {
    GlStateManager.pushMatrix();
    GlStateManager.translate(x, y, 0);
    GlStateManager.rotate(degrees, 0, 0, 1);
    GlStateManager.disableTexture2D();
    GlStateManager.enableBlend();
    GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
    GlStateManager.color(((argb >> 16) & 255) / 255f, ((argb >> 8) & 255) / 255f, (argb & 255) / 255f, 1f);
    GL11.glBegin(GL11.GL_TRIANGLES);
    GL11.glVertex2f(0, -4f);
    GL11.glVertex2f(3.2f, 3.4f);
    GL11.glVertex2f(0, 1.6f);
    GL11.glVertex2f(0, -4f);
    GL11.glVertex2f(0, 1.6f);
    GL11.glVertex2f(-3.2f, 3.4f);
    GL11.glEnd();
    GlStateManager.enableTexture2D();
    GlStateManager.color(1, 1, 1, 1);
    GlStateManager.popMatrix();
  }
}

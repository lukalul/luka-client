package dev.cbui.feature.labels;

import dev.cbui.Cbui;
import dev.cbui.feature.FeatureModule;
import dev.cbui.feature.Features;
import dev.cbui.hud.Setting;
import java.util.List;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumChatFormatting;

/**
 * Card "Nametags": restyles the labels vanilla draws over entities. While on, vanilla's label routine is handed to
 * {@link LabelPainter}; while off nothing here runs and labels stay vanilla. Labels of primed TNT are never touched.
 */
public final class LabelsModule extends FeatureModule {
  private final Setting.Toggle hideAll = add(new Setting.Toggle("Hide all nametags", false, "No labels over any entity"));
  private final Setting.Slider range = add(new Setting.Slider("Draw range", 64, 1, 64, 1,
      "Labels further away than this many blocks are skipped"));
  final Setting.Slider size = add(new Setting.Slider("Size", 1, 0.25f, 3, 0.05f, "1 is the vanilla size"));
  final Setting.Slider shift = add(new Setting.Slider("Vertical shift", 0, -10, 10, 0.25f, "Moves the label up (positive) or down"));
  final Setting.Toggle shadow = add(new Setting.Toggle("Shadow under letters", false, "Font shadow on the label text"));
  private final Setting.Toggle ownName = add(new Setting.Toggle("Show my own name", true, "Your name over your head in third person"));
  final Setting.Toggle walls = add(new Setting.Toggle("Blocked by walls", true, "Blocks in front of a label hide it"));
  private final Setting.Toggle onModel = add(new Setting.Toggle("On inventory model", false,
      "Your name above the model in the inventory screen"));

  private final Setting.Toggle f1Players = grouped("With HUD hidden (F1)", add(new Setting.Toggle("Hide players' names", true,
      "While F1 hides the HUD")));
  private final Setting.Toggle f1Stands = grouped("With HUD hidden (F1)", add(new Setting.Toggle("Hide armor stand names", true,
      "While F1 hides the HUD")));
  private final Setting.Toggle f1Others = grouped("With HUD hidden (F1)", add(new Setting.Toggle("Hide mob and other names", true,
      "While F1 hides the HUD")));

  final Setting.Toggle box = grouped("Label box", add(new Setting.Toggle("Box behind name", true, "The dark box behind the text")));
  final Setting.Color boxColour = grouped("Label box", add(new Setting.Color("Box colour", 0x3F000000, "Colour and opacity of the box")));
  final Setting.Toggle rounded = grouped("Label box", add(new Setting.Toggle("Round box corners", false, "Rounded instead of square corners")));
  final Setting.Slider roundness = grouped("Label box", add(new Setting.Slider("Corner roundness", 3, 0, 10, 1, "How round the corners are")));
  final Setting.Slider padSides = grouped("Label box", add(new Setting.Slider("Side padding", 0, 0, 10, 1,
      "Extra space left and right of the text")));
  final Setting.Slider padEnds = grouped("Label box", add(new Setting.Slider("Top and bottom padding", 0, 0, 10, 1,
      "Extra space above and below the text")));

  final Setting.Color nameColour = grouped("Name text", add(new Setting.Color("Name colour", 0xFFFFFFFF, "Colour of the label text")));
  final Setting.Toggle forceColour = grouped("Name text", add(new Setting.Toggle("Force this colour", false,
      "Ignore colour codes and team colours in names")));

  /** Nesting depth of the inventory/creative screen drawing the player model. */
  private int modelDepth;
  /** A server hologram (always-visible armor stand name containing yours) floats at your head; refreshed each tick. */
  private boolean hologramOverHead;

  public LabelsModule() {
    super("Nametags", true);
  }

  @Override
  public String category() {
    return "VISUALS";
  }

  /** The module whether or not it is switched on, or null when not registered. */
  public static LabelsModule instance() {
    return Features.get(LabelsModule.class);
  }

  /** The module while it should act, else null. */
  public static LabelsModule active() {
    LabelsModule module = Features.get(LabelsModule.class);
    return module != null && module.enabled() ? module : null;
  }

  @Override
  public void tick() {
    hologramOverHead = false;
    try {
      findHologram();
    } catch (Throwable error) {
      Cbui.reportOnce("nametag hologram check", error);
    }
  }

  private void findHologram() {
    EntityPlayer me = mc.thePlayer;
    if (me == null || mc.theWorld == null) return;
    String mine = me.getName();
    if (mine == null || mine.isEmpty()) return;
    double head = me.posY + me.height;
    AxisAlignedBB around = new AxisAlignedBB(me.posX - 0.6, head - 0.6, me.posZ - 0.6, me.posX + 0.6, head + 1.6, me.posZ + 0.6);
    List<EntityArmorStand> stands = mc.theWorld.getEntitiesWithinAABB(EntityArmorStand.class, around);
    for (EntityArmorStand stand : stands) {
      if (!stand.getAlwaysRenderNameTag() || !stand.hasCustomName()) continue;
      String shown = EnumChatFormatting.getTextWithoutFormattingCodes(stand.getCustomNameTag());
      if (shown != null && shown.contains(mine)) {
        hologramOverHead = true;
        return;
      }
    }
  }

  // ---- inventory model ----

  public void modelStart() {
    modelDepth++;
  }

  public void modelEnd() {
    if (modelDepth > 0) modelDepth--;
  }

  // ---- decisions asked by the hooks ----

  /**
   * Whether vanilla's name check should say yes for the local player's own label: 1 yes, 0 no, -1 leave it to vanilla.
   */
  public int ownLabel(EntityLivingBase entity) {
    if (entity != mc.thePlayer) return -1;
    if (modelDepth > 0) return onModel.get() ? 1 : 0;
    if (!ownName.get() || entity != mc.getRenderViewEntity()) return -1;
    if (mc.gameSettings.thirdPersonView == 0) return -1;
    if (entity.isInvisibleToPlayer(mc.thePlayer) || entity.riddenByEntity != null) return 0;
    return hologramOverHead ? 0 : 1;
  }

  /** True when a sneaking entity's label should go through the restyled routine instead of vanilla's inline code. */
  public boolean takesSneakLabel(EntityLivingBase entity) {
    return entity.isSneaking();
  }

  /** Draws a sneaking entity's label through vanilla's label routine (kept to vanilla's 32-block sneak range). */
  public void drawSneakLabel(Render<?> renderer, EntityLivingBase entity, double x, double y, double z) {
    Entity viewer = renderer.getRenderManager().livingPlayer;
    if (viewer == null || entity.getDistanceSqToEntity(viewer) >= 32.0 * 32.0) return;
    GlStateManager.alphaFunc(516, 0.1F);
    String name = entity.getDisplayName().getFormattedText();
    double lowered = y - (entity.isChild() ? entity.height / 2.0 : 0.0);
    ((dev.cbui.mixin.LabelsRenderInvoker) renderer).cbui$drawLivingLabel(entity, name, x, lowered, z, 64);
  }

  /**
   * Vanilla's label routine was called. Returns true when this card handled the label (drawn or skipped), false to let
   * vanilla draw it unchanged.
   */
  public boolean paint(Render<?> renderer, Entity owner, String text, double x, double y, double z, int maxDistance) {
    if (owner == null || text == null || owner instanceof EntityTNTPrimed) return false;
    if (hideAll.get()) return true;
    RenderManager manager = renderer.getRenderManager();
    Entity viewer = manager == null ? null : manager.livingPlayer;
    if (viewer == null) return false;
    double reach = Math.min(maxDistance, range.get());
    if (owner.getDistanceSqToEntity(viewer) > reach * reach) return true;
    if (mc.gameSettings.hideGUI && hiddenWithHud(owner)) return true;
    FontRenderer font = renderer.getFontRendererFromRenderManager();
    if (font == null) return false;
    try {
      LabelPainter.paint(this, font, manager.playerViewX, manager.playerViewY, owner, text, x, y, z);
    } catch (Throwable error) {
      Cbui.reportOnce("nametag draw", error);
    }
    return true;
  }

  private boolean hiddenWithHud(Entity owner) {
    if (owner instanceof EntityPlayer) return f1Players.get();
    if (owner instanceof EntityArmorStand) return f1Stands.get();
    return f1Others.get();
  }
}

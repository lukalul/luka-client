package dev.cbui.hud.module;

import dev.cbui.hud.AbstractHudModule;
import dev.cbui.hud.Anchor;
import dev.cbui.hud.Setting;
import dev.cbui.hud.TimerBook;
import java.util.Arrays;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityEnderPearl;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;

/**
 * HCF cooldown list (client-side estimates): ender pearl, combat tag and golden apple. Everything is detected by polling on the tick, so no mixins
 * are needed. Rows are text-cached and only rebuilt when the displayed value changes.
 */
public final class CooldownsModule extends AbstractHudModule {
  private static final int PEARL = 0, TAG = 1, GAPPLE = 2;
  private static final String[] NAMES = {"Ender Pearl", "Combat Tag", "Golden Apple"};
  private static final String[] SAMPLES = {"Ender Pearl 12.3s", "Combat Tag 24s", "Golden Apple 6.5s"};

  private final Setting.Toggle pearlOn = grouped("Ender pearl", add(new Setting.Toggle("Ender pearl", true, "Show a timer after you throw an ender pearl")));
  private final Setting.Slider pearlTime = grouped("Ender pearl", adv(new Setting.Slider("Ender pearl time (s)", 16, 1, 60, 1, "How long the server blocks pearls (strafepvp: 16)")));
  private final Setting.Toggle tagOn = grouped("Combat tag", add(new Setting.Toggle("Combat tag", true, "Show a timer after you hit or get hit by a player")));
  private final Setting.Slider tagTime = grouped("Combat tag", adv(new Setting.Slider("Combat tag time (s)", 30, 1, 120, 1, "How long combat tag lasts on your server")));
  private final Setting.Toggle gappleOn = grouped("Golden apple", add(new Setting.Toggle("Golden apple", true, "Show a timer after you eat a golden apple")));
  private final Setting.Slider gappleTime = grouped("Golden apple", adv(new Setting.Slider("Golden apple time (s)", 10, 1, 60, 1, "How long the server blocks golden apples")));

  private final TimerBook book = new TimerBook(3);
  private final String[] text = new String[3];
  private final int[] shownTicks = {-1, -1, -1};
  private final int[] order = new int[3];
  private long now;
  private int rows;
  private World lastWorld;
  private long lastHeldPearl = -100;
  private int lastPearlId = -1, prevHurt, prevSlot = -1, prevSize;
  private boolean wasSwinging, prevUsing, prevGapple;

  public CooldownsModule() { super("Cooldowns", false, Anchor.BOTTOM_CENTER, 0, -70, 90, 31); }

  @Override public void tick() {
    World world = mc.theWorld;
    if (world != lastWorld) { lastWorld = world; reset(); }
    if (mc.thePlayer == null || world == null) { rows = 0; return; }
    now++;
    if (pearlOn.get()) pollPearls(world);
    if (tagOn.get()) pollCombat();
    if (gappleOn.get()) pollGapple();

    rows = 0;
    float widest = 0;
    for (int i = 0; i < 3; i++) {
      int left = enabled(i) ? book.remaining(i, now) : 0;
      if (left <= 0) { shownTicks[i] = -1; continue; }
      if (left != shownTicks[i]) { shownTicks[i] = left; text[i] = NAMES[i] + " " + TimerBook.format(left); }
      order[rows++] = i;
      widest = Math.max(widest, textWidth(text[i]));
    }
    if (rows == 0) { size(textWidth(SAMPLES[0]), linesHeight(3)); return; }
    size(Math.max(widest, textWidth(SAMPLES[0])), linesHeight(rows));
  }

  private boolean enabled(int slot) { return slot == PEARL ? pearlOn.get() : slot == TAG ? tagOn.get() : gappleOn.get(); }

  /**
   * The client never learns who threw a pearl (the spawn packet carries no thrower for pearls, so getThrower() is null), so a freshly spawned
   * pearl counts as ours when we held pearls a moment ago and it appeared next to our eyes.
   */
  private void pollPearls(World world) {
    ItemStack held = mc.thePlayer.getHeldItem();
    if (held != null && held.getItem() == Items.ender_pearl) lastHeldPearl = now;
    if (now - lastHeldPearl > 10) return;
    List<Entity> list = world.loadedEntityList;
    double ex = mc.thePlayer.posX, ey = mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), ez = mc.thePlayer.posZ;
    for (int i = 0, n = list.size(); i < n; i++) {
      Entity e = list.get(i);
      if (!(e instanceof EntityEnderPearl) || e.getEntityId() == lastPearlId || e.ticksExisted > 5) continue;
      Entity thrower = ((EntityEnderPearl) e).getThrower();
      if (thrower != null && thrower != mc.thePlayer) continue;
      double dx = e.posX - ex, dy = e.posY - ey, dz = e.posZ - ez;
      if (dx * dx + dy * dy + dz * dz > 16) continue;
      lastPearlId = e.getEntityId();
      book.start(PEARL, now, Math.round(pearlTime.get() * 20));
    }
  }

  /** Combat tag starts when we hit a player (swing at one) or when we get hurt while another player nearby is mid-swing. */
  private void pollCombat() {
    int hurt = mc.thePlayer.hurtTime;
    if (hurt > prevHurt && attackerNearby()) book.start(TAG, now, Math.round(tagTime.get() * 20));
    prevHurt = hurt;
    boolean swinging = mc.thePlayer.isSwingInProgress;
    if (swinging && !wasSwinging) {
      MovingObjectPosition mop = mc.objectMouseOver;
      if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY && mop.entityHit instanceof EntityPlayer)
        book.start(TAG, now, Math.round(tagTime.get() * 20));
    }
    wasSwinging = swinging;
  }

  private boolean attackerNearby() {
    if (mc.thePlayer.getAITarget() instanceof EntityPlayer) return true;
    List<EntityPlayer> players = mc.theWorld.playerEntities;
    for (int i = 0, n = players.size(); i < n; i++) {
      EntityPlayer other = players.get(i);
      if (other != mc.thePlayer && other.isSwingInProgress && other.getDistanceSqToEntity(mc.thePlayer) < 36) return true;
    }
    return false;
  }

  /** An eat finished when the same slot held a golden apple that was being used and its stack shrank or vanished. */
  private void pollGapple() {
    ItemStack held = mc.thePlayer.getHeldItem();
    int slot = mc.thePlayer.inventory.currentItem;
    boolean using = mc.thePlayer.isUsingItem();
    Item gapple = Items.golden_apple;
    if (prevGapple && prevUsing && slot == prevSlot && (held == null || held.getItem() != gapple || held.stackSize < prevSize))
      book.start(GAPPLE, now, Math.round(gappleTime.get() * 20));
    prevUsing = using;
    prevSlot = slot;
    prevGapple = held != null && held.getItem() == gapple;
    prevSize = held == null ? 0 : held.stackSize;
  }

  private void reset() {
    book.clearAll();
    lastPearlId = -1; lastHeldPearl = -100; prevHurt = 0; prevSlot = -1; prevSize = 0;
    wasSwinging = prevUsing = prevGapple = false;
    rows = 0;
    Arrays.fill(shownTicks, -1);
  }

  @Override protected boolean nothingToShow() { return rows == 0; }

  @Override protected void draw(float partialTicks, boolean editor) {
    float y = 0;
    if (rows == 0) {
      if (!editor) return;
      for (int i = 0; i < 3; i++) { text(SAMPLES[i], 0, y, textColor.get()); y += lineStep(); }
      return;
    }
    for (int r = 0; r < rows; r++) { text(text[order[r]], 0, y, textColor.get()); y += lineStep(); }
  }
}

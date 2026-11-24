package dev.cbui.feature.hurttint;

import dev.cbui.Cbui;
import dev.cbui.feature.FeatureModule;
import dev.cbui.feature.Features;
import dev.cbui.hud.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S0BPacketAnimation;
import net.minecraft.network.play.server.S19PacketEntityStatus;

/**
 * Recolours the red overlay a mob or player gets while it is hurt or dying. With the default settings nothing is
 * touched, so the picture is the game's own.
 */
public final class HurtTintModule extends FeatureModule {
  /** The flash this version of the game paints by itself (red, 40 %, darkened by the entity's light). */
  public static final int GAME_FLASH = 0x66FF0000;

  static final int REGULAR = 0;
  static final int CRITICAL = 1;
  static final int BLOCKED = 2;

  private static final int STATUS_HURT = 2;
  private static final int ANIMATION_CRIT = 4;
  private static final String KINDS = "Colours per hit kind";

  public final Setting.Color colour = add(new Setting.Color("Hurt overlay colour", GAME_FLASH,
      "Tint painted on a mob or player when it takes damage; alpha sets strength"));
  public final Setting.Toggle fade = add(new Setting.Toggle("Fade the overlay", false,
      "Let the tint die away instead of cutting off"));
  public final Setting.Slider fadeTicks = add(new Setting.Slider("Fade length (ticks)", 10, 1, 10, 1,
      "Over how many final ticks of the hurt animation the tint fades"));
  public final Setting.Toggle fadeOnDeath = add(new Setting.Toggle("Fade on death too", false,
      "Also fade the tint while an entity is dying (needs Fade the overlay)"));
  public final Setting.Toggle byKind = add(new Setting.Toggle("Colour by hit kind", false,
      "Pick the tint from how the hit landed"));
  public final Setting.Color regular = grouped(KINDS, add(new Setting.Color("Regular hit", GAME_FLASH,
      "Tint for ordinary hits and anything not recognised")));
  public final Setting.Color critical = grouped(KINDS, add(new Setting.Color("Critical hit", 0x66CFA62B,
      "Tint when the hit threw critical sparkles")));
  public final Setting.Color blocked = grouped(KINDS, add(new Setting.Color("Hit while blocking", 0x99000000,
      "Tint for a player struck during a sword block")));

  public HurtTintModule() {
    super("DamageTint", true);
  }

  @Override
  public String category() {
    return "VISUALS";
  }

  /** The module when it exists and should act, else null. */
  public static HurtTintModule active() {
    HurtTintModule module = Features.get(HurtTintModule.class);
    return module != null && module.enabled() ? module : null;
  }

  /** True when the settings ask for exactly the game's own flash, so the hooks can leave it alone. */
  public boolean looksLikeGame(EntityLivingBase entity) {
    return !fade.get() && wanted(entity) == GAME_FLASH;
  }

  /** The overlay for this entity in this frame, as ARGB, with any fade already applied to the alpha. */
  public int overlay(EntityLivingBase entity, float partialTicks) {
    int argb = wanted(entity);
    float strength = strength(entity, partialTicks);
    if (strength >= 1F) return argb;
    int alpha = Math.round((argb >>> 24) * strength);
    return alpha << 24 | argb & 0xFFFFFF;
  }

  private int wanted(EntityLivingBase entity) {
    if (!byKind.get()) return colour.get();
    int kind = entity instanceof HurtRecord ? ((HurtRecord) entity).cbui$hurtKind() : REGULAR;
    if (kind == CRITICAL) return critical.get();
    if (kind == BLOCKED) return blocked.get();
    return regular.get();
  }

  /** 1 = full overlay, 0 = gone. */
  private float strength(EntityLivingBase entity, float partialTicks) {
    if (!fade.get()) return 1F;
    float length = Math.max(1F, fadeTicks.get());
    if (entity.deathTime > 0) {
      if (!fadeOnDeath.get()) return 1F;
      float dyingFor = Math.max(0F, entity.deathTime - 1 + partialTicks);
      return clamp(1F - dyingFor / length);
    }
    return clamp((entity.hurtTime - partialTicks) / length);
  }

  private static float clamp(float value) {
    return value < 0F ? 0F : value > 1F ? 1F : value;
  }

  /** Called after the game handled an entity status packet. */
  public static void statusHandled(S19PacketEntityStatus packet) {
    HurtTintModule module = active();
    if (module == null || packet == null) return;
    try {
      // 1.7.10 names: m_59227312 = status code, m_16748207 = entity in a world
      if (packet.m_59227312() != STATUS_HURT) return;
      Minecraft mc = Minecraft.getMinecraft();
      if (mc.theWorld == null) return;
      module.hurtArrived(packet.m_16748207(mc.theWorld));
    } catch (Throwable error) {
      Cbui.reportOnce("damage tint status", error);
    }
  }

  /** Called after the game handled an entity animation packet. */
  public static void animationHandled(S0BPacketAnimation packet) {
    HurtTintModule module = active();
    if (module == null || packet == null) return;
    try {
      // 1.7.10 names: m_87015290 = animation type, m_18157545 = entity id
      if (packet.m_87015290() != ANIMATION_CRIT) return;
      Minecraft mc = Minecraft.getMinecraft();
      if (mc.theWorld == null) return;
      module.critArrived(mc.theWorld.getEntityByID(packet.m_18157545()));
    } catch (Throwable error) {
      Cbui.reportOnce("damage tint animation", error);
    }
  }

  private void hurtArrived(Entity entity) {
    if (!(entity instanceof EntityLivingBase) || !(entity instanceof HurtRecord)) return;
    HurtRecord record = (HurtRecord) entity;
    int now = entity.ticksExisted;
    int kind = REGULAR;
    if (entity instanceof EntityPlayer && ((EntityPlayer) entity).isBlocking()) kind = BLOCKED;
    else if (sameOrLastTick(record.cbui$critStamp(), now)) kind = CRITICAL;
    record.cbui$setHurtKind(kind);
    record.cbui$setHurtStamp(now + 1);
  }

  private void critArrived(Entity entity) {
    if (!(entity instanceof EntityLivingBase) || !(entity instanceof HurtRecord)) return;
    HurtRecord record = (HurtRecord) entity;
    int now = entity.ticksExisted;
    record.cbui$setCritStamp(now + 1);
    if (((EntityLivingBase) entity).hurtTime > 0 && record.cbui$hurtKind() != BLOCKED
        && sameOrLastTick(record.cbui$hurtStamp(), now)) {
      record.cbui$setHurtKind(CRITICAL);
    }
  }

  private static boolean sameOrLastTick(int stamp, int now) {
    if (stamp == 0) return false;
    int ago = now - (stamp - 1);
    return ago >= 0 && ago <= 1;
  }
}

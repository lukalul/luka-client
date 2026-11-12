package dev.cbui.feature.hitfix;

import dev.cbui.feature.FeatureModule;
import java.nio.charset.StandardCharsets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.client.C17PacketCustomPayload;

/**
 * HitFix: 1.8.9's timing for packets from the server, on 1.7.10. 1.7.10 keeps almost every packet in a queue and applies the
 * queue once per tick, at the end of the tick; 1.8.9 applies each packet on the next frame, before that frame's ticks. Here
 * the queue is also applied at the start of every frame and every tick, with the game's own method, so hits, knockback and
 * other players' movement show up to 50 ms sooner. Nothing is sent, changed or held back; the tick rate stays the same.
 *
 * <p>The client says it has HitFix the way the original HitFix mod does (channel "1337", message "HitFix/1.0"), so servers
 * that look for it see it.
 */
public final class HitFixModule extends FeatureModule {
  private static final String CHANNEL = "1337";
  private static final byte[] HELLO = "HitFix/1.0".getBytes(StandardCharsets.UTF_8);
  private static volatile HitFixModule instance;
  /** The connection HitFix was last announced on. */
  private static NetHandlerPlayClient told;
  /** For checks: how often the queue was applied early, and how many connections were told. */
  public static volatile long applied, announced, hurtHeld;

  public HitFixModule() {
    super("HitFix", false);
    instance = this;
  }

  private static boolean on() {
    HitFixModule m = instance;
    return m != null && m.enabled();
  }

  /** Start of a frame and of every tick: apply what the server has sent so far. */
  public static void applyWaitingPackets() {
    if (!on()) return;
    Minecraft mc = Minecraft.getMinecraft();
    if (mc.theWorld == null || mc.thePlayer == null) return;
    // a paused single-player world keeps its packets for later, as the game does
    if (mc.isSingleplayer() && mc.currentScreen != null && mc.currentScreen.doesGuiPauseGame()) return;
    NetHandlerPlayClient handler = mc.getNetHandler();
    NetworkManager connection = handler == null ? null : handler.getNetworkManager();
    if (connection == null || !connection.isChannelOpen()) return;
    net.minecraft.entity.EntityLivingBase camera = mc.renderViewEntity;
    int hurtBefore = camera == null ? 0 : camera.hurtTime;
    connection.processReceivedPackets();
    applied++;
    if (camera != null && camera.maxHurtTime > 0 && camera.hurtTime == camera.maxHurtTime && hurtBefore != camera.hurtTime) {
      // the hit lands now, but the hurt timer (the hurt camera, its shake and anything else that reads hurtTime) starts
      // where it would without HitFix: when the game applies its packets at the end of the tick
      camera.hurtTime = hurtBefore;
      heldHurt = camera;
      hurtHeld++;
    }
  }

  /**
   * The player's hurt timer from a hit HitFix applied early, kept back until the end of the tick. Without HitFix the game
   * applies packets at the end of a tick, so hurtTime becomes 10 there and the camera shake runs from the next frame; set
   * earlier, the shake started part of the way in and other hurt camera mods saw it at the wrong time.
   */
  private static net.minecraft.entity.EntityLivingBase heldHurt;

  /** End of a tick, where the game applies its packets: start the held hurt timer. */
  public static void releaseHurt() {
    net.minecraft.entity.EntityLivingBase entity = heldHurt;
    if (entity == null) return;
    heldHurt = null;
    if (entity == Minecraft.getMinecraft().renderViewEntity && entity.maxHurtTime > 0) entity.hurtTime = entity.maxHurtTime;
  }

  /** End of a tick: tell a new connection once that this client has HitFix. */
  public static void announce() {
    Minecraft mc = Minecraft.getMinecraft();
    NetHandlerPlayClient handler = mc.getNetHandler();
    if (handler == null || mc.thePlayer == null) {
      told = null;
      return;
    }
    if (!on() || handler == told) return;
    told = handler;
    handler.addToSendQueue(new C17PacketCustomPayload("REGISTER", CHANNEL.getBytes(StandardCharsets.UTF_8)));
    handler.addToSendQueue(new C17PacketCustomPayload(CHANNEL, HELLO.clone()));
    announced++;
  }
}

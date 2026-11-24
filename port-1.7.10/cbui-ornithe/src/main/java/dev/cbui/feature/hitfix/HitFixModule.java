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
 * the queue is also applied at the start of every frame and every tick, so other players' movement and their hurt and
 * death show up to 50 ms sooner. Packets about the player themself (being hit, knockback, health, teleports) keep the
 * game's own timing, so getting hit looks and feels exactly as without HitFix. Nothing is sent or changed; the tick rate
 * stays the same.
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
  public static volatile long applied, announced, heldBack;
  /** For checks: which packet types made the early pass stop, and how often. */
  private static final java.util.Map<String, Long> HELD_KINDS = new java.util.concurrent.ConcurrentHashMap<>();

  public static String heldKinds() { return HELD_KINDS.toString(); }

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
    java.util.Queue<?> queue = ((dev.cbui.mixin.HitFixNetworkAccess) connection).cbui$receivedPackets();
    net.minecraft.network.INetHandler listener = ((dev.cbui.mixin.HitFixNetworkAccess) connection).cbui$packetListener();
    if (queue == null || !(listener instanceof NetHandlerPlayClient)) return;
    boolean any = false;
    for (int budget = 1000; budget > 0; budget--) {
      Object next = queue.peek();
      if (next == null) break;
      if (aboutThePlayer(next, mc)) {
        // this one, and everything after it, waits for the game's own turn at the end of the tick
        heldBack++;
        HELD_KINDS.merge(next.getClass().getSimpleName(), 1L, Long::sum);
        break;
      }
      Object packet = queue.poll();
      if (packet == null) break;
      ((net.minecraft.network.Packet) packet).processPacket(listener);
      any = true;
    }
    if (any) applied++;
  }

  /**
   * Packets that change the player's own state: knockback, hurt and death, health, a teleport, an explosion's push. HitFix
   * applies them when the game would have, so being hit looks and moves exactly as without HitFix (hurt camera, hand
   * tilt, knockback); only other players' packets come early. The queue keeps its order: applying stops at the first of
   * these.
   */
  private static boolean aboutThePlayer(Object packet, Minecraft mc) {
    if (packet instanceof net.minecraft.network.play.server.S06PacketUpdateHealth) return true;
    if (packet instanceof net.minecraft.network.play.server.S08PacketPlayerPosLook) return true;
    if (packet instanceof net.minecraft.network.play.server.S27PacketExplosion) return true;
    if (packet instanceof net.minecraft.network.play.server.S12PacketEntityVelocity) {
      return ((net.minecraft.network.play.server.S12PacketEntityVelocity) packet).m_74342766() == mc.thePlayer.getEntityId();
    }
    if (packet instanceof net.minecraft.network.play.server.S19PacketEntityStatus) {
      return ((net.minecraft.network.play.server.S19PacketEntityStatus) packet).m_16748207(mc.theWorld) == mc.thePlayer;
    }
    if (packet instanceof net.minecraft.network.play.server.S0BPacketAnimation) {
      // animation 1 is the hurt animation: on the player it starts the hurt camera like the hurt status does. Some servers
      // send both; applied early, this one started the shake and the status a moment later restarted it (a double shake)
      net.minecraft.network.play.server.S0BPacketAnimation a = (net.minecraft.network.play.server.S0BPacketAnimation) packet;
      return a.m_87015290() == 1 && a.m_18157545() == mc.thePlayer.getEntityId();
    }
    return false;
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

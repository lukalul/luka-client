package dev.subframe;

import com.google.common.collect.BiMap;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.Packet;

/** Play packet ids as the client sees them. 1.7.10 keeps a BiMap of id to packet class for each side of each protocol state. */
final class PacketIds {
    private PacketIds() {
    }

    @SuppressWarnings("rawtypes")
    private static BiMap clientbound() {
        return EnumConnectionState.PLAY.m_82122287(true);
    }

    /** The id of a clientbound play packet class, or null if this class is not one. */
    static Integer idOf(Class<? extends Packet> packet) {
        return (Integer) clientbound().inverse().get(packet);
    }

    /** A new, empty clientbound play packet, or null for an unknown id. */
    static Packet create(int id) {
        return Packet.generatePacket(clientbound(), id);
    }
}

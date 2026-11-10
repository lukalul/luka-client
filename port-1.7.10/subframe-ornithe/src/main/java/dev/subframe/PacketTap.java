package dev.subframe;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.network.Packet;

/** Sits in front of the vanilla packet handler and copies every decoded packet to the recorder. */
final class PacketTap extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        Recorder r = Recorder.active;
        if (r != null && msg instanceof Packet) {
            try {
                r.packet((Packet) msg);
            } catch (Throwable t) {
                SubFrameMod.LOG.error("Failed to record packet " + msg.getClass().getName(), t);
                r.packetFailed(msg.getClass().getSimpleName(), t);
            }
        }
        ctx.fireChannelRead(msg);
    }
}

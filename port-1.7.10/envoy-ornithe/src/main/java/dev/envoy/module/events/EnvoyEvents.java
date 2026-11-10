package dev.envoy.module.events;

import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.C17PacketCustomPayload;

public final class EnvoyEvents {
    public static final String CHANNEL = "envoy:events";
    private static final int EVENT_CHAT_OPEN = 0;
    private static final int EVENT_CHAT_CLOSE = 1;
    private boolean chatOpen;

    public void onGuiOpen(GuiScreen screen) {
        boolean next = screen instanceof GuiChat;
        if (next == chatOpen) return;
        chatOpen = next;
        transmit(next ? EVENT_CHAT_OPEN : EVENT_CHAT_CLOSE);
    }

    private static void transmit(int event) {
        NetHandlerPlayClient connection = Minecraft.getMinecraft().getNetHandler();
        if (connection == null) return;
        connection.addToSendQueue(new C17PacketCustomPayload(CHANNEL, payload(event)));
    }

    private static PacketBuffer payload(int event) {
        PacketBuffer data = new PacketBuffer(Unpooled.buffer(9));
        data.writeByte(event);
        data.writeLong(System.currentTimeMillis());
        return data;
    }
}

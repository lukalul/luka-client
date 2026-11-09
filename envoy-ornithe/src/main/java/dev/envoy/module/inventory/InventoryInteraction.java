package dev.envoy.module.inventory;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import dev.envoy.Envoy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

public final class InventoryInteraction {
    private static final String LUNAR = "lunar";
    private InventoryInteraction() { }

    public static boolean hideTooltip(ItemStack stack) {
        SlotPolicy policy = SlotPolicy.read(stack);
        return policy != null && policy.suppressTooltip;
    }

    public static boolean hideHighlight(ItemStack stack) {
        SlotPolicy policy = SlotPolicy.read(stack);
        return policy != null && policy.suppressHighlight;
    }

    public static boolean handleClick(ItemStack stack) {
        SlotPolicy policy = SlotPolicy.read(stack);
        if (policy == null) return false;
        Minecraft client = Minecraft.getMinecraft();
        boolean consumed = policy.blockVanillaClick;
        for (ClickAction action : policy.actions) consumed |= action.perform(client);
        return consumed;
    }

    private interface ClickAction { boolean perform(Minecraft client); }

    private static final class SlotPolicy {
        final boolean suppressTooltip, suppressHighlight, blockVanillaClick;
        final List<ClickAction> actions;

        SlotPolicy(NBTTagCompound data) {
            suppressTooltip = data.getBoolean("hideItemTooltip");
            suppressHighlight = data.getBoolean("hideSlotHighlight");
            blockVanillaClick = data.getBoolean("unclickable");
            actions = decodeActions(data);
        }

        static SlotPolicy read(ItemStack stack) {
            if (stack == null || !stack.hasTagCompound()) return null;
            NBTTagCompound root = stack.getTagCompound();
            return root.hasKey(LUNAR) ? new SlotPolicy(root.getCompoundTag(LUNAR)) : null;
        }

        private static List<ClickAction> decodeActions(NBTTagCompound data) {
            List<ClickAction> result = new ArrayList<>(4);
            if (data.hasKey("copyToClipboard")) result.add(new ClipboardAction(data.getString("copyToClipboard")));
            if (data.hasKey("runCommand")) result.add(new ChatAction(data.getString("runCommand"), true));
            if (data.hasKey("suggestCommand")) result.add(new ChatAction(data.getString("suggestCommand"), false));
            if (data.hasKey("openUrl")) result.add(new BrowserAction(data.getString("openUrl")));
            return result;
        }
    }

    private static final class ClipboardAction implements ClickAction {
        private final String value;
        ClipboardAction(String value) { this.value = value; }
        @Override public boolean perform(Minecraft client) { GuiScreen.setClipboardString(value); return true; }
    }

    private static final class ChatAction implements ClickAction {
        private final String value;
        private final boolean send;
        ChatAction(String value, boolean send) { this.value = value; this.send = send; }
        @Override public boolean perform(Minecraft client) {
            if (send) {
                if (client.thePlayer == null) return false;
                client.thePlayer.sendChatMessage(value);
            } else {
                client.displayGuiScreen(new GuiChat(value));
            }
            return true;
        }
    }

    private static final class BrowserAction implements ClickAction {
        private final String address;
        BrowserAction(String address) { this.address = address; }
        @Override public boolean perform(Minecraft client) {
            if (!client.gameSettings.chatLinks) return true;
            try {
                URI target = new URI(address);
                String protocol = target.getScheme();
                if (protocol == null || !("http".equalsIgnoreCase(protocol) || "https".equalsIgnoreCase(protocol))) return true;
                Class<?> api = Class.forName("java.awt.Desktop");
                Object desktop = api.getMethod("getDesktop").invoke(null);
                api.getMethod("browse", URI.class).invoke(desktop, target);
            } catch (Throwable failure) {
                Envoy.LOGGER.warn("[Inventory] could not open URL: {}", failure.toString());
            }
            return true;
        }
    }
}

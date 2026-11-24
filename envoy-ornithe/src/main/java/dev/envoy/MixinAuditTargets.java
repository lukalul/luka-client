package dev.envoy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.network.NetHandlerLoginClient;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.S3EPacketTeams;

/**
 * The classes Envoy's mixins target, by class literal (correct through remapping and obfuscation). Loading a class
 * applies its mixins. Kept apart from MixinAudit, which Mixin loads as its config plugin before any game class may load.
 */
final class MixinAuditTargets {
    private MixinAuditTargets() { }

    static Class<?>[] load() {
        return new Class<?>[] {
            Minecraft.class, EntityRenderer.class, GuiIngame.class, NetHandlerLoginClient.class, NetworkManager.class,
            RendererLivingEntity.class, RenderItem.class, Render.class, ItemRenderer.class, GuiContainer.class,
            GuiScreen.class, FontRenderer.class, GuiNewChat.class, S3EPacketTeams.class
        };
    }
}

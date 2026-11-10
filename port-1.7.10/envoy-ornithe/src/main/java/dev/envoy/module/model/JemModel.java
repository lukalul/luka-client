package dev.envoy.module.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import dev.luka.compat.GlStateManager;
import net.minecraft.util.ResourceLocation;

public final class JemModel {
    public ResourceLocation texture;
    public float textureWidth = 64.0F;
    public float textureHeight = 32.0F;
    public float shadowSize = 1.0F;

    private final List<JemPart> parts = new ArrayList<>();
    private Map<String, JemPart> index;

    public void addPart(JemPart part) {
        this.parts.add(part);
        this.index = null;
    }

    public JemPart resolve(String boneName) {
        if (this.index == null) rebuildIndex();
        return this.index.get(boneName);
    }

    private void rebuildIndex() {
        Map<String, JemPart> lookup = new HashMap<>();
        ArrayDeque<JemPart> pending = new ArrayDeque<>();
        for (int i = this.parts.size() - 1; i >= 0; i--) pending.addFirst(this.parts.get(i));
        while (!pending.isEmpty()) {
            JemPart current = pending.removeFirst();
            registerAliases(lookup, current);
            List<JemPart> children = current.getChildren();
            for (int i = children.size() - 1; i >= 0; i--) pending.addFirst(children.get(i));
        }
        this.index = lookup;
    }

    private static void registerAliases(Map<String, JemPart> lookup, JemPart part) {
        String name = part.id;
        if (name == null || name.isEmpty()) return;
        if (!lookup.containsKey(name)) lookup.put(name, part);
        int separator = name.indexOf('_');
        if (separator > 0 && separator < name.length() - 1) {
            String alias = name.substring(separator + 1);
            if (!lookup.containsKey(alias)) lookup.put(alias, part);
        }
    }

    public List<JemPart> getParts() {
        return this.parts;
    }

    public JemPart find(String id) {
        for (JemPart root : this.parts) {
            JemPart match = root.find(id);
            if (match != null) return match;
        }
        return null;
    }

    public void resetAnimation() {
        for (JemPart root : this.parts) root.resetAnimation();
    }

    public void render() {
        if (this.texture != null) Minecraft.getMinecraft().getTextureManager().bindTexture(this.texture);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        for (JemPart root : this.parts) root.render();
    }

    public void dispose() {
        for (JemPart root : this.parts) root.dispose();
    }
}

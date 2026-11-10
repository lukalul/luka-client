package dev.envoy.module.icon;

import org.lwjgl.opengl.GL11;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import dev.luka.compat.GlStateManager;
import net.minecraft.client.renderer.Tessellator;

public final class IconTextRenderer {
    private static final int LONGEST_KEY = 32;
    private IconTextRenderer() { }

    public static boolean contains(String text) {
        if (text == null || IconRegistry.isEmpty()) return false;
        TokenCursor cursor = new TokenCursor(text);
        while (cursor.next()) if (cursor.icon() != null) return true;
        return false;
    }

    public static int draw(FontRenderer font, String text, float x, float y, int color, boolean shadow) {
        return draw(font, text, x, y, color, shadow, false);
    }

    public static int draw(final FontRenderer font, String text, float x, final float y, int color,
                           final boolean shadow, final boolean shadowPass) {
        final int adjusted = normalizeColor(color, shadowPass);
        final int alpha = adjusted >>> 24;
        final float[] pen = {x};
        visit(text, new SegmentVisitor() {
            @Override public void text(String value, String formatting) {
                pen[0] = font.drawString(formatting + value, (int) pen[0], (int) y, adjusted, shadow);
            }
            @Override public void icon(IconRegistry.Icon value) {
                if (!shadowPass) paint(value, pen[0], y, alpha);
                pen[0] += value.width;
            }
        });
        return (int) pen[0];
    }

    public static int width(final FontRenderer font, String text) {
        final float[] measured = {0.0F};
        visit(text, new SegmentVisitor() {
            @Override public void text(String value, String formatting) {
                measured[0] += font.getStringWidth(formatting + value);
            }
            @Override public void icon(IconRegistry.Icon value) { measured[0] += value.width; }
        });
        return (int) measured[0];
    }

    private static void visit(String source, SegmentVisitor visitor) {
        TokenCursor cursor = new TokenCursor(source);
        StringBuilder history = new StringBuilder();
        while (cursor.next()) {
            if (cursor.icon() != null) {
                visitor.icon(cursor.icon());
            } else {
                String plain = cursor.text();
                String active = history.length() == 0 ? "" : dev.envoy.module.chat.HexTextRenderer.activeFormat(history.toString());
                visitor.text(plain, active);
                history.append(plain);
            }
        }
    }

    private static int normalizeColor(int color, boolean darken) {
        int opaque = (color & 0xFC000000) == 0 ? color | 0xFF000000 : color;
        return darken ? (opaque & 0xFCFCFC) >> 2 | opaque & 0xFF000000 : opaque;
    }

    private static void paint(IconRegistry.Icon icon, float left, float baseline, int alpha) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(icon.texture);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.color(1, 1, 1, alpha / 255.0F);
        double top = baseline + icon.ascent, right = left + icon.width, bottom = top + icon.height;
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawing(GL11.GL_QUADS);
        vertex(tessellator, left, bottom, 0, 1); vertex(tessellator, right, bottom, 1, 1);
        vertex(tessellator, right, top, 1, 0); vertex(tessellator, left, top, 0, 0);
        tessellator.draw();
        GlStateManager.color(1, 1, 1, 1);
    }

    private static void vertex(Tessellator out, double x, double y, double u, double v) {
        out.addVertexWithUV(x, y, 0, u, v);
    }

    private interface SegmentVisitor { void text(String value, String formatting); void icon(IconRegistry.Icon value); }

    private static final class TokenCursor {
        private final String source;
        private int offset;
        private String text;
        private IconRegistry.Icon icon;
        TokenCursor(String source) { this.source = source == null ? "" : source; }

        boolean next() {
            text = null; icon = null;
            if (offset >= source.length()) return false;
            int marker = source.indexOf(':', offset);
            if (marker < 0) { text = source.substring(offset); offset = source.length(); return true; }
            if (marker > offset) { text = source.substring(offset, marker); offset = marker; return true; }
            int closing = validClosingColon(marker);
            if (closing > marker) icon = IconRegistry.get(source.substring(marker + 1, closing));
            if (icon != null) offset = closing + 1;
            else { text = source.substring(offset, offset + 1); offset++; }
            return true;
        }

        String text() { return text; }
        IconRegistry.Icon icon() { return icon; }

        private int validClosingColon(int opening) {
            int stop = Math.min(source.length(), opening + LONGEST_KEY + 2);
            for (int i = opening + 1; i < stop; i++) {
                char c = source.charAt(i);
                if (c == ':') return i == opening + 1 ? -1 : i;
                boolean asciiLetter = c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z';
                boolean asciiDigit = c >= '0' && c <= '9';
                if (!asciiLetter && !asciiDigit && c != '_' && c != '-' && c != '.') return -1;
            }
            return -1;
        }
    }
}

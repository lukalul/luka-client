package dev.envoy.module.chat;

import net.minecraft.client.gui.FontRenderer;

/** Renders classic formatting codes plus the six-nibble section-sign hex format. */
public final class HexTextRenderer {
    private static final char SECTION = '\u00a7';
    private static final String COLOR_CODES = "0123456789abcdef";
    private static final int[] PALETTE = {
        0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
        0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };

    private HexTextRenderer() { }

    public static String hexPrefix(int rgb) {
        char[] output = new char[14];
        output[0] = SECTION;
        output[1] = 'x';
        int value = rgb & 0xFFFFFF;
        for (int nibble = 0; nibble < 6; nibble++) {
            output[2 + nibble * 2] = SECTION;
            output[3 + nibble * 2] = Character.forDigit((value >>> ((5 - nibble) * 4)) & 15, 16);
        }
        return new String(output);
    }

    public static int drawWithShadow(FontRenderer font, String text, float x, float y, int defaultColor) {
        return draw(font, text, x, y, defaultColor, true);
    }

    public static int draw(FontRenderer font, String text, float x, float y, int defaultColor) {
        return draw(font, text, x, y, defaultColor, false);
    }

    public static int draw(FontRenderer font, String text, float x, float y, int defaultColor, boolean shadow) {
        return draw(font, text, x, y, defaultColor, shadow, false);
    }

    public static int draw(FontRenderer font, String text, float x, float y, int defaultColor, boolean shadow,
                           boolean shadowPass) {
        Paint paint = new Paint(defaultColor);
        float cursor = x;
        int index = 0;
        while (index < text.length()) {
            if (text.charAt(index) == SECTION && index + 1 < text.length()) {
                int consumed = paint.consumeFormatting(text, index);
                if (consumed > 0) {
                    index += consumed;
                    continue;
                }
            }
            char character = text.charAt(index++);
            String glyph = paint.decorate(character);
            int color = paint.argb(shadowPass);
            if (shadow) font.drawStringWithShadow(glyph, (int) cursor, (int) y, color);
            else font.drawString(glyph, (int) cursor, (int) y, color);
            cursor += font.getCharWidth(character) + (paint.bold ? 1 : 0);
        }
        return (int) cursor;
    }

    /** FontRenderer.getFormatFromString with the six-nibble hex sequence kept; private in 1.7.10. */
    public static String activeFormat(String text) {
        StringBuilder active = new StringBuilder();
        for (int marker = text.indexOf(SECTION); marker >= 0 && marker + 1 < text.length();
             marker = text.indexOf(SECTION, marker + 1)) {
            char code = text.charAt(marker + 1);
            if (Character.toLowerCase(code) == 'x' && marker + 13 < text.length()) {
                active.replace(0, active.length(), text.substring(marker, marker + 14));
                marker += 13;
            } else if (isColorCode(code)) {
                active.setLength(0);
                active.append(SECTION).append(code);
            } else if (isStyleCode(code)) {
                active.append(SECTION).append(code);
            }
        }
        return active.toString();
    }

    private static boolean isColorCode(char c) {
        return COLOR_CODES.indexOf(Character.toLowerCase(c)) >= 0;
    }

    private static boolean isStyleCode(char c) {
        char lower = Character.toLowerCase(c);
        return (lower >= 'k' && lower <= 'o') || lower == 'r';
    }

    private static int decodeHex(String text, int sectionIndex) {
        if (sectionIndex + 13 >= text.length()) return -1;
        int color = 0;
        for (int digit = 0; digit < 6; digit++) {
            int position = sectionIndex + 2 + digit * 2;
            if (text.charAt(position) != SECTION) return -1;
            int value = Character.digit(text.charAt(position + 1), 16);
            if (value < 0) return -1;
            color = (color << 4) | value;
        }
        return color;
    }

    private static final class Paint {
        final int alpha, base;
        int rgb;
        boolean bold, italic, underline, strike, obfuscated;

        Paint(int defaultColor) {
            int suppliedAlpha = defaultColor >>> 24;
            alpha = suppliedAlpha == 0 ? 255 : suppliedAlpha;
            base = defaultColor & 0xFFFFFF;
            rgb = base;
        }

        int consumeFormatting(String text, int index) {
            char code = Character.toLowerCase(text.charAt(index + 1));
            if (code == 'x') {
                int decoded = decodeHex(text, index);
                if (decoded >= 0) {
                    rgb = decoded;
                    clearEffects();
                    return 14;
                }
                return 2;
            }
            int paletteIndex = COLOR_CODES.indexOf(code);
            if (paletteIndex >= 0) {
                rgb = PALETTE[paletteIndex];
                clearEffects();
            } else if (code == 'l') bold = true;
            else if (code == 'o') italic = true;
            else if (code == 'n') underline = true;
            else if (code == 'm') strike = true;
            else if (code == 'k') obfuscated = true;
            else if (code == 'r') { rgb = base; clearEffects(); }
            return 2;
        }

        void clearEffects() { bold = italic = underline = strike = obfuscated = false; }

        String decorate(char character) {
            StringBuilder result = new StringBuilder(11);
            if (obfuscated) result.append(SECTION).append('k');
            if (bold) result.append(SECTION).append('l');
            if (strike) result.append(SECTION).append('m');
            if (underline) result.append(SECTION).append('n');
            if (italic) result.append(SECTION).append('o');
            return result.append(character).toString();
        }

        int argb(boolean shadowPass) {
            int visible = shadowPass ? (rgb & 0xFCFCFC) >>> 2 : rgb;
            return (alpha << 24) | visible;
        }
    }
}

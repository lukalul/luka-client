package dev.customcolors.font;

import dev.customcolors.core.GlyphTable;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** Drawing and measuring of text with an HD font sheet, plus the narrow no-break space fix. */
public final class HdText {
    /** U+202F, which newer Java puts into formatted times ("8:56 AM"). */
    public static final char NARROW_SPACE = ' ';

    private HdText() {
    }

    /** The text with every narrow no-break space turned into a plain space; the same instance when there is none. */
    public static String plainSpaces(String text) {
        if (text == null || text.indexOf(NARROW_SPACE) < 0) {
            return text;
        }
        return text.replace(NARROW_SPACE, ' ');
    }

    /**
     * Draws one sheet glyph across its whole advance and returns that advance (fractional), the
     * HD counterpart of vanilla's default-glyph drawing.
     */
    public static float drawGlyph(TextureManager textures, ResourceLocation sheet, int cell, boolean italic, float x,
                                  float y, float advance) {
        textures.bindTexture(sheet);
        float u = (cell % 16) * 8.0F;
        float v = (cell / 16) * 8.0F;
        float slant = italic ? 1.0F : 0.0F;
        float w = Math.min(advance, 8.0F) - 0.01F;
        GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
        GL11.glTexCoord2f(u / 128.0F, v / 128.0F);
        GL11.glVertex3f(x + slant, y, 0.0F);
        GL11.glTexCoord2f(u / 128.0F, (v + 7.99F) / 128.0F);
        GL11.glVertex3f(x - slant, y + 7.99F, 0.0F);
        GL11.glTexCoord2f((u + w) / 128.0F, v / 128.0F);
        GL11.glVertex3f(x + w + slant, y, 0.0F);
        GL11.glTexCoord2f((u + w) / 128.0F, (v + 7.99F) / 128.0F);
        GL11.glVertex3f(x + w - slant, y + 7.99F, 0.0F);
        GL11.glEnd();
        return advance;
    }

    /**
     * Width of a string as it is drawn with the HD face: fractional advances summed, one extra unit per
     * visible character while bold, formatting codes free, rounded down at the end.
     */
    public static int width(FontRenderer font, HdFontFace face, String text) {
        if (text == null) {
            return 0;
        }
        float total = 0.0F;
        boolean bold = false;
        int n = text.length();
        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);
            if (c == '§') {
                if (i + 1 < n) {
                    char code = Character.toLowerCase(text.charAt(++i));
                    if (code == 'l') {
                        bold = true;
                    } else if (code == 'r' || (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) {
                        bold = false;
                    }
                }
                continue;
            }
            float advance;
            if (c == ' ' || c == NARROW_SPACE) {
                advance = face.space;
            } else {
                int cell = c == 0 ? -1 : GlyphTable.cellOf(c);
                advance = cell >= 0 ? face.advance(cell) : Math.max(0, font.getCharWidth(c));
            }
            total += advance;
            if (bold && advance > 0.0F) {
                total += 1.0F;
            }
        }
        return (int) total;
    }
}

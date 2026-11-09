package dev.cbui.font;

import net.minecraft.util.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

public final class Fonts {
    private static final ResourceLocation REGULAR_FILE = new ResourceLocation("cbui", "font/Roboto-Regular.ttf");
    private static final LukaFontRenderer REGULAR = new LukaFontRenderer(REGULAR_FILE, 9);
    private static final LukaFontRenderer BOLD = new LukaFontRenderer(new ResourceLocation("cbui", "font/Roboto-Bold.ttf"), 10);
    private static final Map<Float, LukaFontRenderer> SIZED = new HashMap<>();
    private static final ResourceLocation BOLD_FILE = new ResourceLocation("cbui", "font/Roboto-Bold.ttf");
    private static final Map<Float, LukaFontRenderer> SIZED_BOLD = new HashMap<>();

    public static LukaFontRenderer regular() { return REGULAR; }
    public static LukaFontRenderer bold() { return BOLD; }

    /** The regular face at another size (cached: each size builds its own glyph atlas once). */
    public static synchronized LukaFontRenderer regular(float size) {
        LukaFontRenderer font = SIZED.get(size);
        if (font == null) {
            font = new LukaFontRenderer(REGULAR_FILE, size);
            SIZED.put(size, font);
        }
        return font;
    }

    /** The bold face at another size (cached: each size builds its own glyph atlas once). */
    public static synchronized LukaFontRenderer bold(float size) {
        LukaFontRenderer font = SIZED_BOLD.get(size);
        if (font == null) {
            font = new LukaFontRenderer(BOLD_FILE, size);
            SIZED_BOLD.put(size, font);
        }
        return font;
    }

    private Fonts() { }
}

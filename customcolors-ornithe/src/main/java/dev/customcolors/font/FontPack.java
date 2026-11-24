package dev.customcolors.font;

import dev.customcolors.Switches;
import dev.customcolors.core.GlyphMeasure;
import dev.customcolors.pack.OwnTextures;
import dev.customcolors.pack.PackAccess;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Properties;
import net.minecraft.util.ResourceLocation;

/** Finds pack replacements for font sheets ({@code <root>/font/<file>}) and unicode pages. */
public final class FontPack {
    private static final ResourceLocation[] PAGES = new ResourceLocation[256];
    private static final boolean[] LOOKED_UP = new boolean[256];

    private FontPack() {
    }

    /** Forgets the unicode page lookups; called on every resource reload. */
    public static void reset() {
        synchronized (PAGES) {
            Arrays.fill(PAGES, null);
            Arrays.fill(LOOKED_UP, false);
        }
    }

    /** The pack's HD replacement for a vanilla sheet, measured, or null to stay vanilla. */
    public static HdFontFace prepare(ResourceLocation original) {
        if (original == null || !Switches.hdFonts()) {
            return null;
        }
        String path = original.getResourcePath();
        String file = path.substring(path.lastIndexOf('/') + 1);
        if (!file.endsWith(".png")) {
            return null;
        }
        String stem = file.substring(0, file.length() - 4);
        String domain = original.getResourceDomain();
        for (String root : PackAccess.ROOTS) {
            ResourceLocation sheet = new ResourceLocation(domain, root + "/font/" + file);
            BufferedImage img = PackAccess.image(sheet);
            if (img == null || img.getWidth() < 16 || img.getHeight() < 16) {
                continue;
            }
            Properties overrides = PackAccess.properties(new ResourceLocation(domain, root + "/font/" + stem + ".properties"));
            float[] advances = GlyphMeasure.advances(PackAccess.pixels(img), img.getWidth(), img.getHeight(), overrides);
            OwnTextures.note(sheet);
            return new HdFontFace(sheet, advances);
        }
        return null;
    }

    /** The pack's version of unicode page {@code page}, or null for vanilla. Looked up once per reload. */
    public static ResourceLocation unicodePage(int page) {
        if (page < 0 || page > 255) {
            return null;
        }
        synchronized (PAGES) {
            if (LOOKED_UP[page]) {
                return PAGES[page];
            }
            LOOKED_UP[page] = true;
            if (!Switches.hdFonts()) {
                return null;
            }
            String file = String.format("font/unicode_page_%02x.png", page);
            for (String root : PackAccess.ROOTS) {
                ResourceLocation loc = PackAccess.at(root + "/" + file);
                if (PackAccess.exists(loc)) {
                    PAGES[page] = loc;
                    OwnTextures.note(loc);
                    return loc;
                }
            }
            return null;
        }
    }

    public static int pageCount() {
        int n = 0;
        synchronized (PAGES) {
            for (ResourceLocation p : PAGES) {
                if (p != null) {
                    n++;
                }
            }
        }
        return n;
    }
}

package dev.customcolors;

import dev.customcolors.core.AnimSpec;
import dev.customcolors.core.EggColours;
import dev.customcolors.core.FrameClock;
import dev.customcolors.core.FrameCutter;
import dev.customcolors.core.Glide;
import dev.customcolors.core.GlyphMeasure;
import dev.customcolors.core.GlyphTable;
import dev.customcolors.core.Hex;
import dev.customcolors.core.LightPalette;
import dev.customcolors.core.PackPaths;
import dev.customcolors.core.Palette;
import dev.customcolors.core.PaletteFormat;
import dev.customcolors.core.SkinPaths;
import dev.customcolors.core.SkinPick;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import javax.imageio.ImageIO;

/** Plain-Java checks of the pack format parsing (no game needed). Run by Gradle {@code check}. */
public final class FormatChecks {
    private static int failures;
    private static int passed;

    private FormatChecks() {
    }

    public static void main(String[] args) throws Exception {
        hex();
        paletteFormat();
        colormaps();
        lightmaps();
        glyphs();
        animations();
        paths();
        eggs();
        skins();
        glide();
        testPack(new File("testpack/cc-test/assets/minecraft/mcpatcher"));
        System.out.println("customcolors format checks: " + passed + " passed, " + failures + " failed");
        if (failures > 0) {
            System.exit(1);
        }
    }

    private static void hex() {
        same("hex plain", 0xFF8800, Hex.rgb("FF8800"));
        same("hex hash", 0x00FF00, Hex.rgb("#00ff00"));
        same("hex spaces", 0x123, Hex.rgb(" 123 "));
        same("hex word", -1, Hex.rgb("nothex"));
        same("hex empty", -1, Hex.rgb(""));
        same("hex long", -1, Hex.rgb("1234567"));
        same("hex null", -1, Hex.rgb(null));
    }

    private static void paletteFormat() {
        same("format default", PaletteFormat.CLIMATE, PaletteFormat.kind(props()));
        same("format fixed", PaletteFormat.SINGLE, PaletteFormat.kind(props("format", "fixed")));
        same("format 0", PaletteFormat.SINGLE, PaletteFormat.kind(props("format", "0")));
        same("format grid", PaletteFormat.GRID, PaletteFormat.kind(props("format", "grid")));
        same("format 2", PaletteFormat.GRID, PaletteFormat.kind(props("format", "2")));
        same("format other", PaletteFormat.CLIMATE, PaletteFormat.kind(props("format", "weird")));
        same("colour default", 0xFFFFFF, PaletteFormat.colour(props()));
        same("colour value", 0xFF00FF, PaletteFormat.colour(props("color", "FF00FF")));
        same("colour bad", PaletteFormat.BAD_COLOUR, PaletteFormat.colour(props("color", "zz")));
    }

    private static void colormaps() {
        int[] px = new int[256 * 256];
        Arrays.fill(px, 0xFF0000FF);
        square(px, 256, 51, 173, 12, 0xFFFF0000);
        square(px, 256, 173, 51, 12, 0xFFFFFF00);
        Palette water = Palette.image(px, 256, 256);
        same("plains is red", 0xFF0000, water.climate(0.8F, 0.4F));
        same("desert corner blue", 0x0000FF, water.climate(2.0F, 0.0F));
        same("cold corner blue", 0x0000FF, water.climate(0.0F, 0.0F));
        Palette solid = Palette.solid(0xFF00FF);
        same("fixed everywhere", 0xFF00FF, solid.climate(0.3F, 0.9F));
        same("fixed row", 0xFF00FF, solid.firstRow(16)[15]);
        int[] row = new int[16];
        for (int i = 0; i < 16; i++) {
            row[i] = 0xFF000000 | (i * 16);
        }
        Palette redstone = Palette.image(row, 16, 1);
        same("redstone row 15", 15 * 16, redstone.firstRow(16)[15]);
        check("narrow row ignored", Palette.image(row, 16, 1).firstRow(17) == null);
        Palette list = Palette.image(new int[] {0xFFFF0000, 0xFF0000FF}, 2, 1);
        same("list start", 0xFF0000, list.along(0.0F));
        same("list end", 0x0000FF, list.along(1.0F));
        same("list middle", 0x800080, list.along(0.5F));
        same("entry clamps", 0x0000FF, list.entry(99));
    }

    private static void lightmaps() {
        int w = 16;
        int[] px = new int[w * 32];
        for (int row = 0; row < 16; row++) {
            for (int x = 0; x < w; x++) {
                int c;
                if (x == 15) {
                    c = 0xFF0000;
                } else if (x < 8) {
                    c = row * 0x80 / 15;
                } else {
                    int g = row * 255 / 15;
                    c = (g << 16) | (g << 8) | g;
                }
                px[row * w + x] = 0xFF000000 | c;
                int o = row * 255 / 15;
                px[(16 + row) * w + x] = 0xFF000000 | (o << 16) | ((o / 2) << 8);
            }
        }
        LightPalette lp = LightPalette.of(px, w, 32);
        check("light map 32 high", lp != null);
        check("light map 40 high refused", LightPalette.of(new int[16 * 40], 16, 40) == null);
        int[] table = new int[256];
        lp.fill(table, 1.0F, false, 0.5F, 0.0F, 0.0F);
        same("noon full sky is white", 0xFFFFFF, table[15 * 16] & 0xFFFFFF);
        lp.fill(table, 0.0F, false, 0.5F, 0.0F, 0.0F);
        same("midnight full sky is blue", 0x000080, table[15 * 16] & 0xFFFFFF);
        same("torch at night is orange", 0xFF7F00, table[15] & 0xFFFFFF);
        lp.fill(table, 0.0F, true, 0.5F, 0.0F, 0.0F);
        same("lightning is red", 0xFF0000, table[15 * 16] & 0xFFFFFF);
        lp.fill(table, 0.0F, false, 0.5F, 1.0F, 0.0F);
        same("night vision 32 brightens blue", 0x0000FF, table[15 * 16] & 0xFFFFFF);
        lp.fill(table, 0.0F, false, 0.5F, 0.0F, 1.0F);
        check("bright lifts midnight", (table[15 * 16] & 255) > 0x80);
        same("bright keeps hue", 0, (table[15 * 16] >> 8) & 0xFFFF);
    }

    private static void glyphs() throws Exception {
        same("glyph table upper half", 128, GlyphTable.highCount());
        same("cell of A", 65, GlyphTable.cellOf('A'));
        same("cell of first", 0, GlyphTable.cellOf('À'));
        same("cell of square", 254, GlyphTable.cellOf('■'));
        same("no cell for U+202F", -1, GlyphTable.cellOf(' '));
        int size = 256;
        int[] px = new int[size * size];
        for (int code = 33; code < 127; code++) {
            int bar = (code % 8) + 2;
            int left = (code % 16) * 16;
            int top = (code / 16) * 16;
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < bar; x++) {
                    px[(top + y) * size + left + x] = 0xFFFFFFFF;
                }
            }
        }
        float[] plain = GlyphMeasure.advances(px, size, size, null);
        near("bar B", ((66 % 8) + 2) / 2.0F + 1.0F, plain['B']);
        near("empty cell", GlyphMeasure.BLANK, plain[200]);
        float[] adv = GlyphMeasure.advances(px, size, size, props("width.65", "7.5", "width.32", "3", "width.x", "9"));
        near("override A", 7.5F, adv['A']);
        near("override space", 3.0F, adv[32]);
        near("AAAA", 30.0F, adv['A'] * 4);
        InputStream in = FormatChecks.class.getResourceAsStream("/assets/minecraft/textures/font/ascii.png");
        if (in != null) {
            BufferedImage img = ImageIO.read(in);
            in.close();
            int[] v = img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
            float[] vanilla = GlyphMeasure.advances(v, img.getWidth(), img.getHeight(), null);
            near("vanilla a", 6.0F, vanilla['a']);
            near("vanilla i", 2.0F, vanilla['i']);
            check("vanilla-shaped space near 4 (" + vanilla[32] + ")", Math.abs(vanilla[32] - 4.0F) < 0.5F);
            System.out.println("  vanilla sheet measured: space " + vanilla[32] + ", 'a' " + vanilla['a']);
        } else {
            System.out.println("  (vanilla ascii.png not on the classpath; space calibration skipped)");
        }
    }

    private static void animations() {
        AnimSpec pig = AnimSpec.parse(props("from", "~/anim/sub/pig_frames.png", "to", "textures/entity/pig/pig.png",
            "x", "8", "y", "8", "w", "8", "h", "8", "duration", "20", "tile.0", "0", "tile.1", "1", "tile.2", "0",
            "tile.3", "2", "duration.1", "40"));
        same("pig steps", "[0, 1, 0, 2]", Arrays.toString(pig.steps(3)));
        same("pig ticks", "[20, 40, 20, 20]", Arrays.toString(pig.stepTicks(3)));
        same("wrap", "[2, 0]", Arrays.toString(AnimSpec.parse(props("from", "a", "to", "b", "w", "1", "h", "1",
            "tile.0", "-1", "tile.1", "3")).steps(3)));
        boolean refused = false;
        try {
            AnimSpec.parse(props("from", "a", "to", "b", "h", "4"));
        } catch (IllegalArgumentException e) {
            refused = true;
        }
        check("missing w refused", refused);
        FrameClock clock = new FrameClock(pig.steps(3), pig.stepTicks(3));
        StringBuilder seen = new StringBuilder();
        for (int t = 0; t < 100; t++) {
            if (t % 10 == 0) {
                seen.append(clock.frame());
            }
            clock.tick();
        }
        same("pig timeline", "0011110022", seen.toString());
        int[] strip = new int[16 * 48];
        for (int y = 0; y < 48; y++) {
            for (int x = 0; x < 16; x++) {
                strip[y * 16 + x] = y < 16 ? 0xFFFFFFFF : (y < 32 ? 0xFF000000 : 0xFF00FF00);
            }
        }
        int[][] frames = FrameCutter.cut(strip, 16, 48, 8, 8);
        same("three frames", 3, frames.length);
        same("frame size", 64, frames[0].length);
        same("third frame green", 0xFF00FF00, frames[2][27]);
        same("short strip one frame", 1, FrameCutter.cut(new int[16 * 4], 16, 4, 8, 8).length);
        int[][] logo = FrameCutter.cut(new int[32 * 70], 32, 70, 32, 32);
        same("leftover rows dropped", 2, logo.length);
    }

    private static void paths() {
        same("dot path", "minecraft:mcpatcher/anim/sub/x.png",
            join(PackPaths.resolve("./x.png", "mcpatcher/anim/sub/a.properties")));
        same("tilde path", "minecraft:optifine/anim/x.png",
            join(PackPaths.resolve("~/anim/x.png", "optifine/anim/sub/a.properties")));
        same("namespaced", "foo:bar/x.png", join(PackPaths.resolve("foo:bar/x.png", "mcpatcher/anim/a.properties")));
        same("plain", "minecraft:textures/gui/title/minecraft.png",
            join(PackPaths.resolve("textures/gui/title/minecraft.png", "mcpatcher/anim/a.properties")));
    }

    private static void eggs() {
        Map<String, int[]> eggs = EggColours.parse(props("egg.shell.Creeper", "0000FF", "egg.spots.Creeper", "FFFF00",
            "egg.Pig", "FF00FF", "egg.Cow", "nothex", "potion.water", "00FF00"));
        same("creeper shell", 0x0000FF, eggs.get("Creeper")[0]);
        same("creeper spots", 0xFFFF00, eggs.get("Creeper")[1]);
        same("pig short form", 0xFF00FF, eggs.get("Pig")[0]);
        same("pig spots vanilla", -1, eggs.get("Pig")[1]);
        check("bad egg skipped", !eggs.containsKey("Cow"));
    }

    @SuppressWarnings("unchecked")
    private static void skins() {
        String[] roots = {"mcpatcher", "optifine"};
        Set<String>[] files = new Set[2];
        files[0] = set("pig/pig2.png", "pig/pig3.png", "cow2.png", "spider/spider2.png", "spider/spider_eyes2.png");
        files[1] = set("sheep/sheep2.png", "pig/pig2.png", "pig/pig3.png", "pig/pig4.png");
        same("pig alternates", "[mcpatcher/mob/pig/pig2.png, mcpatcher/mob/pig/pig3.png]",
            Arrays.toString(SkinPaths.alternates("textures/entity/pig/pig.png", roots, files)));
        same("flat cow", "[mcpatcher/mob/cow2.png]",
            Arrays.toString(SkinPaths.alternates("textures/entity/cow/cow.png", roots, files)));
        same("optifine sheep", "[optifine/mob/sheep/sheep2.png]",
            Arrays.toString(SkinPaths.alternates("textures/entity/sheep/sheep.png", roots, files)));
        same("armour untouched", 0, SkinPaths.alternates("textures/models/armor/iron_layer_1.png", roots, files).length);
        int[] counts = new int[3];
        for (long i = 0; i < 3000; i++) {
            counts[SkinPick.pick(i * 7919L, ~i * 104729L, 3)]++;
        }
        check("picks spread " + Arrays.toString(counts), counts[0] > 800 && counts[1] > 800 && counts[2] > 800);
        same("pick stable", SkinPick.pick(42L, 99L, 2), SkinPick.pick(42L, 99L, 2));
    }

    private static void glide() {
        Glide g = new Glide();
        same("first value at once", 0xFF0000, g.toward(0xFF0000, 0L));
        int mid = g.toward(0x0000FF, 1_000_000_000L);
        check("one second in between", ((mid >> 16) & 255) > 60 && ((mid >> 16) & 255) < 120);
        int late = g.toward(0x0000FF, 4_000_000_000L);
        check("settled after a few seconds", ((late >> 16) & 255) < 10);
    }

    /** The generated test pack read through the same parsers (skipped when the pack was not generated). */
    private static void testPack(File root) throws Exception {
        if (!root.isDirectory()) {
            System.out.println("  (testpack/cc-test not generated; pack checks skipped)");
            return;
        }
        BufferedImage w = ImageIO.read(new File(root, "colormap/water.png"));
        Palette water = Palette.image(w.getRGB(0, 0, 256, 256, null, 0, 256), 256, 256);
        same("pack water at plains", 0xFF0000, water.climate(0.8F, 0.4F));
        Properties sky = new Properties();
        FileInputStream in = new FileInputStream(new File(root, "colormap/sky0.properties"));
        sky.load(in);
        in.close();
        same("pack sky fixed", PaletteFormat.SINGLE, PaletteFormat.kind(sky));
        same("pack sky colour", 0xFF00FF, PaletteFormat.colour(sky));
        Properties colors = new Properties();
        in = new FileInputStream(new File(root, "color.properties"));
        colors.load(in);
        in.close();
        same("pack harm ignored", -1, Hex.rgb(colors.getProperty("potion.harm")));
        same("pack creeper spots", 0xFFFF00, EggColours.parse(colors).get("Creeper")[1]);
        BufferedImage lm = ImageIO.read(new File(root, "lightmap/world0.png"));
        check("pack light map usable", LightPalette.of(lm.getRGB(0, 0, 16, 32, null, 0, 16), 16, 32) != null);
        BufferedImage font = ImageIO.read(new File(root, "font/ascii.png"));
        Properties fontProps = new Properties();
        in = new FileInputStream(new File(root, "font/ascii.properties"));
        fontProps.load(in);
        in.close();
        float[] adv = GlyphMeasure.advances(font.getRGB(0, 0, 256, 256, null, 0, 256), 256, 256, fontProps);
        near("pack AAAA", 30.0F, adv['A'] * 4);
        near("pack space", 3.0F, adv[32]);
        Properties pig = new Properties();
        in = new FileInputStream(new File(root, "anim/sub/pig.properties"));
        pig.load(in);
        in.close();
        AnimSpec spec = AnimSpec.parse(pig);
        same("pack pig source", "minecraft:mcpatcher/anim/sub/pig_frames.png",
            join(PackPaths.resolve(spec.from, "mcpatcher/anim/sub/pig.properties")));
    }

    private static Set<String> set(String... values) {
        return new HashSet<String>(Arrays.asList(values));
    }

    private static String join(String[] pair) {
        return pair[0] + ":" + pair[1];
    }

    private static void square(int[] px, int width, int cx, int cy, int half, int colour) {
        for (int y = cy - half; y <= cy + half; y++) {
            for (int x = cx - half; x <= cx + half; x++) {
                px[y * width + x] = colour;
            }
        }
    }

    private static Properties props(String... kv) {
        Properties p = new Properties();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            p.setProperty(kv[i], kv[i + 1]);
        }
        return p;
    }

    private static void check(String what, boolean ok) {
        if (ok) {
            passed++;
        } else {
            failures++;
            System.out.println("FAIL " + what);
        }
    }

    private static void same(String what, Object expected, Object actual) {
        boolean ok = expected == null ? actual == null : expected.equals(actual);
        if (!ok) {
            System.out.println("FAIL " + what + ": expected " + expected + ", got " + actual);
            failures++;
        } else {
            passed++;
        }
    }

    private static void near(String what, float expected, float actual) {
        check(what + " (" + actual + ")", Math.abs(expected - actual) < 0.01F);
    }
}

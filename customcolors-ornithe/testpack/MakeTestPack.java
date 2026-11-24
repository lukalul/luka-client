import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.imageio.ImageIO;

/**
 * Writes the customcolors test packs (every image is drawn here from solid colours and simple shapes).
 * Run from this folder: {@code java MakeTestPack.java}. Output:
 * <ul>
 * <li>cc-test           - every test piece under mcpatcher/ (plus optifine/mob/sheep for the root check)</li>
 * <li>cc-test-optifine  - the same pieces under optifine/ only</li>
 * <li>cc-test-both      - mcpatcher/ pieces plus optifine/ pieces in other colours (mcpatcher must win)</li>
 * <li>cc-test-extra     - 64-high night-vision light map, a 16x40 (ignored) Nether light map, no font</li>
 * </ul>
 */
public final class MakeTestPack {
    private static File base;

    public static void main(String[] args) throws IOException {
        base = new File(args.length > 0 ? args[0] : ".");
        pieces("cc-test", "mcpatcher", false);
        file("cc-test", "assets/minecraft/optifine/mob/sheep/sheep2.png", solid(64, 32, 0xFF8000FF));
        pieces("cc-test-optifine", "optifine", false);
        pieces("cc-test-both", "mcpatcher", false);
        pieces("cc-test-both", "optifine", true);
        file("cc-test-both", "assets/minecraft/mcpatcher/mob/sheep/sheep2.png", solid(64, 32, 0xFFFF8000));
        file("cc-test-both", "assets/minecraft/optifine/mob/sheep/sheep2.png", solid(64, 32, 0xFF8000FF));
        extra("cc-test-extra");
        System.out.println("test packs written to " + base.getCanonicalPath());
    }

    /** All pieces of C01-C07 section 6 under one root; {@code other} = different colours for the order check. */
    private static void pieces(String pack, String root, boolean other) throws IOException {
        text(pack, "pack.mcmeta", "{\n  \"pack\": {\n    \"pack_format\": 1,\n    \"description\": \"customcolors test ("
            + root + ")\"\n  }\n}\n");
        String r = "assets/minecraft/" + root + "/";
        // C01 colormaps
        BufferedImage water = solid(256, 256, other ? 0xFF00FF00 : 0xFF0000FF);
        fill(water, 51 - 12, 173 - 12, 24, 24, other ? 0xFFFFFFFF : 0xFFFF0000);
        fill(water, 173 - 12, 51 - 12, 24, 24, 0xFFFFFF00);
        file(pack, r + "colormap/water.png", water);
        text(pack, r + "colormap/swampgrass.properties", "format=fixed\ncolor=" + (other ? "FFFFFF" : "FF00FF") + "\n");
        file(pack, r + "colormap/swampfoliage.png", solid(16, 16, other ? 0xFFFFFFFF : 0xFF00FFFF));
        file(pack, r + "colormap/pine.png", solid(16, 16, other ? 0xFF000000 : 0xFFFFFF00));
        file(pack, r + "colormap/birch.png", solid(16, 16, other ? 0xFF000000 : 0xFFEEEEEE));
        BufferedImage redstone = new BufferedImage(16, 1, BufferedImage.TYPE_INT_ARGB);
        redstone.setRGB(0, 0, 0xFF0000FF);
        for (int i = 1; i < 15; i++) {
            int v = i * 255 / 15;
            redstone.setRGB(i, 0, 0xFF000000 | (v << 16) | (255 - v));
        }
        redstone.setRGB(15, 0, 0xFFFF0000);
        file(pack, r + "colormap/redstone.png", redstone);
        file(pack, r + "colormap/stem.png", solid(8, 1, 0xFF00FF00));
        file(pack, r + "colormap/pumpkinstem.png", ramp(8, 0xFF000000, 0xFFFF8000));
        file(pack, r + "colormap/lavadrop.png", ramp(40, 0xFFFF0000, 0xFFFFFFFF));
        BufferedImage mycelium = new BufferedImage(4, 1, BufferedImage.TYPE_INT_ARGB);
        mycelium.setRGB(0, 0, 0xFFFF0000);
        mycelium.setRGB(1, 0, 0xFF00FF00);
        mycelium.setRGB(2, 0, 0xFF0000FF);
        mycelium.setRGB(3, 0, 0xFFFFFFFF);
        file(pack, r + "colormap/myceliumparticle.png", mycelium);
        BufferedImage orb = new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB);
        orb.setRGB(0, 0, 0xFFFF0000);
        orb.setRGB(1, 0, 0xFF0000FF);
        file(pack, r + "colormap/xporb.png", orb);
        // C07 sky and fog
        text(pack, r + "colormap/sky0.properties", "format=fixed\ncolor=" + (other ? "FFFFFF" : "FF00FF") + "\n");
        file(pack, r + "colormap/fog0.png", solid(256, 256, other ? 0xFFFFFFFF : 0xFF00FF00));
        file(pack, r + "colormap/underwater.png", solid(256, 256, other ? 0xFFFFFFFF : 0xFFFFFF00));
        text(pack, r + "colormap/underlava.properties", "format=fixed\ncolor=" + (other ? "FFFFFF" : "0000FF") + "\n");
        // C02 color.properties
        text(pack, r + "color.properties", other
            ? "potion.moveSpeed=FFFFFF\npotion.water=FFFFFF\negg.shell.Creeper=FFFFFF\nlilypad=00FFFF\n"
            : "potion.moveSpeed=FF0000\npotion.poison=0000FF\npotion.water=00FF00\negg.shell.Creeper=0000FF\n"
                + "egg.spots.Creeper=FFFF00\negg.Pig=FF00FF\nlilypad=FF00FF\npotion.harm=nothex\n");
        // C03 light map (Overworld, 16x32) and Nether
        file(pack, r + "lightmap/world0.png", lightmap(32, other));
        BufferedImage nether = new BufferedImage(16, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 32; y++) {
            int g = (y % 16) * 255 / 15;
            fill(nether, 0, y, 16, 1, 0xFF000000 | (g << 8));
        }
        file(pack, r + "lightmap/world-1.png", nether);
        // C04 fonts
        if (!other) {
            BufferedImage ascii = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
            for (int code = 33; code < 127; code++) {
                fill(ascii, (code % 16) * 16, (code / 16) * 16, (code % 8) + 2, 16, 0xFFFFFFFF);
            }
            file(pack, r + "font/ascii.png", ascii);
            text(pack, r + "font/ascii.properties", "width.65=7.5\nwidth.32=3\n");
            BufferedImage page = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
            for (int cell = 0; cell < 256; cell++) {
                fill(page, (cell % 16) * 16 + 1, (cell / 16) * 16 + 1, 12, 14, 0xFFFF0000);
            }
            file(pack, r + "font/unicode_page_04.png", page);
        }
        // C05 animations
        BufferedImage logo = new BufferedImage(32, 64, BufferedImage.TYPE_INT_ARGB);
        fill(logo, 0, 0, 32, 32, 0xFFFF0000);
        fill(logo, 0, 32, 32, 32, 0xFF0000FF);
        file(pack, r + "anim/logo_frames.png", logo);
        text(pack, r + "anim/logo.properties",
            "from=./logo_frames.png\nto=textures/gui/title/minecraft.png\nx=0\ny=0\nw=32\nh=32\nduration=10\n");
        BufferedImage pig = new BufferedImage(16, 48, BufferedImage.TYPE_INT_ARGB);
        fill(pig, 0, 0, 16, 16, 0xFFFFFFFF);
        fill(pig, 0, 16, 16, 16, 0xFF000000);
        fill(pig, 0, 32, 16, 16, 0xFF00FF00);
        file(pack, r + "anim/sub/pig_frames.png", pig);
        text(pack, r + "anim/sub/pig.properties", "from=~/anim/sub/pig_frames.png\nto=textures/entity/pig/pig.png\n"
            + "x=8\ny=8\nw=8\nh=8\nduration=20\ntile.0=0\ntile.1=1\ntile.2=0\ntile.3=2\nduration.1=40\n");
        text(pack, r + "anim/bad.properties", "from=./logo_frames.png\nto=textures/gui/title/minecraft.png\nh=4\n");
        // C06 random mobs
        file(pack, r + "mob/pig/pig2.png", solid(64, 32, other ? 0xFFFFFFFF : 0xFFFF0000));
        file(pack, r + "mob/pig/pig3.png", solid(64, 32, other ? 0xFFFFFFFF : 0xFF0000FF));
        file(pack, r + "mob/cow2.png", solid(64, 32, 0xFFFF8000));
        file(pack, r + "mob/spider/spider2.png", solid(64, 32, 0xFF00FF00));
        BufferedImage eyes = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        fill(eyes, 40, 12, 8, 8, 0xFFFFFF00);
        file(pack, r + "mob/spider/spider_eyes2.png", eyes);
    }

    private static void extra(String pack) throws IOException {
        text(pack, "pack.mcmeta", "{\n  \"pack\": {\n    \"pack_format\": 1,\n    \"description\": \"customcolors test (extra)\"\n  }\n}\n");
        file(pack, "assets/minecraft/mcpatcher/lightmap/world0.png", lightmap(64, false));
        file(pack, "assets/minecraft/mcpatcher/lightmap/world-1.png", solid(16, 40, 0xFF00FF00));
    }

    /** C03 test light map: blue night / grey day ramps, red lightning column, steady orange torch rows. */
    private static BufferedImage lightmap(int height, boolean other) {
        BufferedImage img = new BufferedImage(16, height, BufferedImage.TYPE_INT_ARGB);
        for (int row = 0; row < 16; row++) {
            for (int x = 0; x < 16; x++) {
                int c;
                if (x == 15) {
                    c = 0xFF0000;
                } else if (x < 8) {
                    c = other ? (row * 255 / 15) << 8 : row * 0x80 / 15;
                } else {
                    int g = row * 255 / 15;
                    c = (g << 16) | (g << 8) | g;
                }
                img.setRGB(x, row, 0xFF000000 | c);
                int o = row * 255 / 15;
                img.setRGB(x, 16 + row, 0xFF000000 | (o << 16) | ((o / 2) << 8));
            }
        }
        if (height == 64) {
            fill(img, 0, 32, 16, 32, 0xFFFF00FF);
        }
        return img;
    }

    private static BufferedImage solid(int w, int h, int argb) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        fill(img, 0, 0, w, h, argb);
        return img;
    }

    private static BufferedImage ramp(int w, int from, int to) {
        BufferedImage img = new BufferedImage(w, 1, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < w; x++) {
            float t = w == 1 ? 0 : x / (float) (w - 1);
            int r = Math.round(((from >> 16) & 255) + (((to >> 16) & 255) - ((from >> 16) & 255)) * t);
            int g = Math.round(((from >> 8) & 255) + (((to >> 8) & 255) - ((from >> 8) & 255)) * t);
            int b = Math.round((from & 255) + ((to & 255) - (from & 255)) * t);
            img.setRGB(x, 0, 0xFF000000 | (r << 16) | (g << 8) | b);
        }
        return img;
    }

    private static void fill(BufferedImage img, int x0, int y0, int w, int h, int argb) {
        for (int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) {
                img.setRGB(x, y, argb);
            }
        }
    }

    private static void file(String pack, String path, BufferedImage img) throws IOException {
        File f = new File(base, pack + "/" + path);
        f.getParentFile().mkdirs();
        ImageIO.write(img, "png", f);
    }

    private static void text(String pack, String path, String content) throws IOException {
        File f = new File(base, pack + "/" + path);
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }
}

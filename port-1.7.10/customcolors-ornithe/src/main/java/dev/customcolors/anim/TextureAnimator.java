package dev.customcolors.anim;

import dev.customcolors.CustomColorsMod;
import dev.customcolors.core.AnimSpec;
import dev.customcolors.core.FrameClock;
import dev.customcolors.core.FrameCutter;
import dev.customcolors.core.PackPaths;
import dev.customcolors.pack.PackAccess;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import dev.luka.compat.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.util.ResourceLocation;

/** Pack texture animations ({@code anim/*.properties}), stepped once per client tick. */
public final class TextureAnimator {
    private static volatile List<Clip> clips = new ArrayList<Clip>();

    private TextureAnimator() {
    }

    /** One running animation. */
    private static final class Clip {
        final String name;
        final ResourceLocation target;
        final int x;
        final int y;
        final BufferedImage[] frames;
        final FrameClock clock;
        final boolean guiOnly;
        int shownFrame = -1;
        int shownTexture = -1;

        Clip(String name, ResourceLocation target, int x, int y, BufferedImage[] frames, FrameClock clock) {
            this.name = name;
            this.target = target;
            this.x = x;
            this.y = y;
            this.frames = frames;
            this.clock = clock;
            this.guiOnly = target.getResourcePath().startsWith("textures/gui/");
        }
    }

    public static int count() {
        return clips.size();
    }

    public static void reload() {
        List<Clip> next = new ArrayList<Clip>();
        for (String path : PackAccess.listSelected("anim")) {
            String file = path.substring(path.lastIndexOf('/') + 1);
            if (!file.endsWith(".properties") || file.startsWith("._")) {
                continue;
            }
            try {
                Clip clip = load(path);
                if (clip != null) {
                    next.add(clip);
                }
            } catch (Exception e) {
                CustomColorsMod.LOG.info("customcolors: skipping animation {}: {}", path, e.getMessage());
            }
        }
        clips = next;
    }

    private static Clip load(String path) throws Exception {
        Properties props = PackAccess.properties(PackAccess.at(path));
        if (props == null) {
            return null;
        }
        AnimSpec spec = AnimSpec.parse(props);
        String[] from = PackPaths.resolve(spec.from, path);
        String[] to = PackPaths.resolve(spec.to, path);
        ResourceLocation source = new ResourceLocation(from[0], from[1]);
        ResourceLocation target = new ResourceLocation(to[0], to[1]);
        BufferedImage strip = PackAccess.image(source);
        if (strip == null) {
            throw new IllegalArgumentException("cannot read " + source);
        }
        int[] size = imageSize(target);
        if (size == null) {
            throw new IllegalArgumentException("cannot read target " + target);
        }
        if (spec.x + spec.w > size[0] || spec.y + spec.h > size[1]) {
            throw new IllegalArgumentException("area lies outside " + target);
        }
        int[][] cut = FrameCutter.cut(PackAccess.pixels(strip), strip.getWidth(), strip.getHeight(), spec.w, spec.h);
        BufferedImage[] frames = new BufferedImage[cut.length];
        for (int i = 0; i < cut.length; i++) {
            BufferedImage img = new BufferedImage(spec.w, spec.h, BufferedImage.TYPE_INT_ARGB);
            img.setRGB(0, 0, spec.w, spec.h, cut[i], 0, spec.w);
            frames[i] = img;
        }
        FrameClock clock = new FrameClock(spec.steps(frames.length), spec.stepTicks(frames.length));
        return new Clip(path, target, spec.x, spec.y, frames, clock);
    }

    /** Width and height of an image resource without decoding the pixels. */
    private static int[] imageSize(ResourceLocation loc) {
        InputStream in = null;
        ImageInputStream iis = null;
        try {
            in = Minecraft.getMinecraft().getResourceManager().getResource(loc).getInputStream();
            iis = ImageIO.createImageInputStream(in);
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis);
                return new int[] {reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        } catch (Exception e) {
            return null;
        } finally {
            try {
                if (iis != null) {
                    iis.close();
                }
                if (in != null) {
                    in.close();
                }
            } catch (Exception ignored) {
                // nothing to do
            }
        }
    }

    /** Steps every animation and writes changed frames into their (loaded) target textures. */
    public static void tick() {
        List<Clip> list = clips;
        if (list.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        TextureManager textures = mc.getTextureManager();
        if (textures == null) {
            return;
        }
        boolean screenOpen = mc.currentScreen != null;
        for (int i = 0; i < list.size(); i++) {
            Clip clip = list.get(i);
            clip.clock.tick();
            ITextureObject tex = textures.getTexture(clip.target);
            if (tex == null || (clip.guiOnly && !screenOpen)) {
                continue;
            }
            int id = tex.getGlTextureId();
            int frame = clip.clock.frame();
            if (frame == clip.shownFrame && id == clip.shownTexture) {
                continue;
            }
            try {
                GlStateManager.bindTexture(id);
                TextureUtil.uploadTextureImageSub(id, clip.frames[frame], clip.x, clip.y, false, false);
                clip.shownFrame = frame;
                clip.shownTexture = id;
            } catch (Exception e) {
                CustomColorsMod.reportOnce("animation " + clip.name, e);
            }
        }
    }
}

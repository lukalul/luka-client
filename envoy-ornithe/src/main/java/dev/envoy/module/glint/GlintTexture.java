package dev.envoy.module.glint;

import java.awt.image.BufferedImage;
import dev.envoy.Envoy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.util.ResourceLocation;

public final class GlintTexture {
    private static final ResourceLocation VANILLA_GLINT =
        new ResourceLocation("textures/misc/enchanted_item_glint.png");
    private static volatile ResourceLocation generated;

    private GlintTexture() { }

    public static ResourceLocation get() {
        ResourceLocation ready = generated;
        if (ready != null) return ready;
        synchronized (GlintTexture.class) {
            if (generated == null) generated = createTexture();
            return generated;
        }
    }

    private static ResourceLocation createTexture() {
        Minecraft minecraft = Minecraft.getMinecraft();
        try {
            BufferedImage source = TextureUtil.readBufferedImage(
                minecraft.getResourceManager().getResource(VANILLA_GLINT).getInputStream());
            int width = source.getWidth();
            int height = source.getHeight();
            int[] pixels = source.getRGB(0, 0, width, height, null, 0, width);
            for (int i = 0; i < pixels.length; i++) pixels[i] = alphaWhite(pixels[i]);
            BufferedImage converted = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            converted.setRGB(0, 0, width, height, pixels, 0, width);
            ResourceLocation result = minecraft.getTextureManager().getDynamicTextureLocation(
                "envoy_glint", new DynamicTexture(converted));
            Envoy.LOGGER.info("[Glint] alpha texture generated ({}x{})", width, height);
            return result;
        } catch (Exception error) {
            Envoy.LOGGER.error("[Glint] failed to generate the alpha texture, falling back to vanilla", error);
            return VANILLA_GLINT;
        }
    }

    private static int alphaWhite(int pixel) {
        int intensity = ((pixel >>> 16) & 255) + ((pixel >>> 8) & 255) + (pixel & 255);
        return ((intensity / 3) << 24) | 0xFFFFFF;
    }
}

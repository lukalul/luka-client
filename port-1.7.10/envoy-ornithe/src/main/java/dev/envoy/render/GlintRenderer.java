package dev.envoy.render;

import dev.envoy.module.glint.GlintState;
import dev.envoy.module.glint.GlintTexture;
import dev.envoy.module.model.ModelRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;

/**
 * Coloured glint overlay for items carrying the Apollo {@code lunar.glint} tag. Minecraft 1.8 re-rendered the
 * baked model with an alpha glint texture; 1.7.10 has no baked models, so this repeats vanilla's glint passes
 * with the same animation, tinted, on top of the item geometry vanilla just drew.
 */
public final class GlintRenderer {
    private static final long[] PERIODS = { 3000L, 4873L };
    private static final float[] DIRECTIONS = { 1.0F, -1.0F };
    private static final float[] ROTATIONS = { -50.0F, 10.0F };

    private GlintRenderer() { }

    public static boolean wants(ItemStack stack) {
        return stack != null && GlintState.hasGlint(stack) && ModelRegistry.findForItem(stack) == null;
    }

    /** Glint for the 3D item mesh of renderItemIn2D (held and dropped items); call right after the mesh is drawn. */
    public static void meshGlint(Tessellator tessellator, TextureManager textures, ItemStack stack, float thickness) {
        begin(textures, GlintState.glintArgb(stack));
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        for (int layer = 0; layer < 2; layer++) {
            GL11.glPushMatrix();
            GL11.glScalef(0.125F, 0.125F, 0.125F);
            float phase = (float) (Minecraft.getSystemTime() % PERIODS[layer]) / (float) PERIODS[layer] * 8.0F;
            GL11.glTranslatef(DIRECTIONS[layer] * phase, 0.0F, 0.0F);
            GL11.glRotatef(ROTATIONS[layer], 0.0F, 0.0F, 1.0F);
            ItemRenderer.renderItemIn2D(tessellator, 0.0F, 0.0F, 1.0F, 1.0F, 256, 256, thickness);
            GL11.glPopMatrix();
        }
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        end(textures, stack);
    }

    /** Glint rectangle over a GUI item, like vanilla's RenderItem.renderGlint, drawn at the item's z. */
    public static void guiGlint(TextureManager textures, ItemStack stack, int x, int y, int width, int height, float z) {
        begin(textures, GlintState.glintArgb(stack));
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        Tessellator tessellator = Tessellator.instance;
        for (int layer = 0; layer < 2; layer++) {
            float scale = 0.00390625F;
            float scroll = (float) (Minecraft.getSystemTime() % (long) (3000 + layer * 1873)) / (3000.0F + layer * 1873) * 256.0F;
            float slope = layer == 1 ? -1.0F : 4.0F;
            tessellator.startDrawingQuads();
            tessellator.addVertexWithUV(x, y + height, z, (scroll + height * slope) * scale, height * scale);
            tessellator.addVertexWithUV(x + width, y + height, z, (scroll + width + height * slope) * scale, height * scale);
            tessellator.addVertexWithUV(x + width, y, z, (scroll + width) * scale, 0.0D);
            tessellator.addVertexWithUV(x, y, z, scroll * scale, 0.0D);
            tessellator.draw();
        }
        GL11.glDepthMask(true);
        end(textures, stack);
    }

    private static void begin(TextureManager textures, int argb) {
        GL11.glDepthFunc(GL11.GL_EQUAL);
        GL11.glDisable(GL11.GL_LIGHTING);
        textures.bindTexture(GlintTexture.get());
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        int alpha = (argb >>> 24) & 255;
        GL11.glColor4f(((argb >> 16) & 255) / 255.0F, ((argb >> 8) & 255) / 255.0F, (argb & 255) / 255.0F,
            alpha == 0 ? 1.0F : alpha / 255.0F);
    }

    private static void end(TextureManager textures, ItemStack stack) {
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        textures.bindTexture(textures.getResourceLocation(stack.getItemSpriteNumber()));
    }
}

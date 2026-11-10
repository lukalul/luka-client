package dev.envoy.render;

import java.util.ArrayList;
import java.util.List;

import dev.envoy.module.model.JemAnimation;
import dev.envoy.module.model.JemModel;
import dev.envoy.module.model.ModelAnimator;
import dev.envoy.module.model.ModelRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import dev.luka.compat.GlStateManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;

public final class JemItemRenderer {

    private static final float PIXEL = 0.0625F;
    private static final float ITEM_SCALE = 0.5F;

    private JemItemRenderer() {
    }

    public static boolean renderHeld(ItemStack stack) {
        ModelRegistry.ItemEntry entry = ModelRegistry.findForItem(stack);
        if (entry == null) {
            return false;
        }
        JemModel model = animatedModel(entry);
        if (model == null) {
            return false;
        }

        GlStateManager.pushMatrix();
        GlStateManager.enableRescaleNormal();
        GlStateManager.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
        GlStateManager.scale(-1.0F, -1.0F, 1.0F);
        transform(entry);
        model.render();
        GlStateManager.disableRescaleNormal();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
        return true;
    }

    /** GUI item: 1.8's three-dimensional item transform around the slot's centre. */
    public static boolean renderGui(ItemStack stack, int x, int y, float zLevel) {
        if (ModelRegistry.findForItem(stack) == null) {
            return false;
        }
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 8.0F, y + 8.0F, 100.0F + zLevel);
        GlStateManager.scale(1.0F, 1.0F, -1.0F);
        GlStateManager.scale(20.0F, 20.0F, 20.0F);
        GlStateManager.rotate(210.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(-135.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.enableLighting();
        boolean drawn = renderHeld(stack);
        GlStateManager.disableLighting();
        GlStateManager.popMatrix();
        return drawn;
    }

    /** Item on the ground, with vanilla's bob and spin. */
    public static boolean renderDropped(EntityItem item, double x, double y, double z, float partialTicks) {
        ItemStack stack = item.getEntityItem();
        if (ModelRegistry.findForItem(stack) == null) {
            return false;
        }
        float bob = net.minecraft.util.MathHelper.sin((item.age + partialTicks) / 10.0F + item.hoverStart) * 0.1F + 0.1F;
        float spin = ((item.age + partialTicks) / 20.0F + item.hoverStart) * 57.295776F;
        GlStateManager.pushMatrix();
        GlStateManager.translate((float) x, (float) y + bob + 0.25F, (float) z);
        GlStateManager.rotate(spin, 0.0F, 1.0F, 0.0F);
        boolean drawn = renderHeld(stack);
        GlStateManager.popMatrix();
        return drawn;
    }

    /** Armor attached to the biped body parts; {@code biped} is the renderer's main model, posed for this frame. */
    public static boolean renderArmor(EntityLivingBase entity, ItemStack stack, int armorSlot, ModelBiped biped) {
        ModelRegistry.ItemEntry entry = ModelRegistry.findForItem(stack);
        if (entry == null || biped == null) {
            return false;
        }
        List<ModelRenderer> parts = attachmentPoints(biped, entry.attach, armorSlot);
        if (parts.isEmpty()) {
            return false;
        }
        JemModel model = animatedModel(entry);
        if (model == null) {
            return false;
        }

        for (ModelRenderer part : parts) {
            GlStateManager.pushMatrix();
            part.postRender(PIXEL);
            transform(entry);
            model.render();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.popMatrix();
        }
        return true;
    }

    private static JemModel animatedModel(ModelRegistry.ItemEntry entry) {
        JemModel model = ModelRegistry.getModel(entry.modelPath);
        if (model == null) {
            return null;
        }
        model.resetAnimation();
        JemAnimation animation = ModelRegistry.getAnimation(entry.animationPath, entry.animationName);
        if (animation != null) {
            ModelAnimator.apply(model, animation, Minecraft.getSystemTime() / 1000.0D);
        }
        return model;
    }

    private static void transform(ModelRegistry.ItemEntry entry) {
        if (entry.offset[0] != 0.0F || entry.offset[1] != 0.0F || entry.offset[2] != 0.0F) {
            GlStateManager.translate(entry.offset[0] * PIXEL, entry.offset[1] * PIXEL, entry.offset[2] * PIXEL);
        }
        if (entry.scale != 1.0F) {
            GlStateManager.scale(entry.scale, entry.scale, entry.scale);
        }
    }

    private static List<ModelRenderer> attachmentPoints(ModelBiped biped, String attach, int armorSlot) {
        List<ModelRenderer> parts = new ArrayList<>();
        String target = attach == null ? defaultAttachment(armorSlot) : attach;
        ModelRenderer primary = namedPart(biped, target);
        if (primary != null) {
            parts.add(primary);
        }
        if ("legs".equals(target)) {
            parts.add(biped.bipedRightLeg);
        } else if ("arms".equals(target)) {
            parts.add(biped.bipedRightArm);
        }
        return parts;
    }

    private static String defaultAttachment(int slot) {
        return slot == 4 ? "head" : (slot == 2 || slot == 3 ? "body" : "legs");
    }

    private static ModelRenderer namedPart(ModelBiped model, String name) {
        if ("head".equals(name)) return model.bipedHead;
        if ("body".equals(name)) return model.bipedBody;
        if ("left_arm".equals(name) || "arms".equals(name)) return model.bipedLeftArm;
        if ("right_arm".equals(name)) return model.bipedRightArm;
        if ("left_leg".equals(name) || "legs".equals(name)) return model.bipedLeftLeg;
        if ("right_leg".equals(name)) return model.bipedRightLeg;
        return null;
    }
}

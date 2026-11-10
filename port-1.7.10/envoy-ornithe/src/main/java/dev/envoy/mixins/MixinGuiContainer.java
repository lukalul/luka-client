package dev.envoy.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.envoy.module.inventory.InventoryInteraction;
import net.minecraft.client.gui.inventory.GuiContainer;
import dev.luka.compat.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.inventory.Slot;

/**
 * Inventory (interaction) module: inside a container GUI, handles items tagged {@code lunar.*}. It
 * cancels the click (action fired / unclickable) and hides the slot highlight when asked.
 */
@Mixin(GuiContainer.class)
public abstract class MixinGuiContainer {

    @Shadow
    private Slot theSlot;

    @Inject(method = "handleMouseClick", at = @At("HEAD"), cancellable = true, require = 0)
    private void envoy$routeTaggedClick(Slot clickedSlot, int slotId, int button, int clickMode, CallbackInfo callback) {
        if (isHandled(clickedSlot)) callback.cancel();
    }

    private static boolean isHandled(Slot slot) {
        return slot != null && slot.getHasStack() && InventoryInteraction.handleClick(slot.getStack());
    }

    @Redirect(
        method = "drawScreen",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/inventory/GuiContainer;drawGradientRect(IIIIII)V"),
        require = 0)
    private void envoy$drawPermittedHighlight(GuiContainer screen, int left, int top, int right, int bottom,
                                               int firstColor, int secondColor) {
        Slot hovered = this.theSlot;
        boolean suppressed = hovered != null && hovered.getHasStack()
            && InventoryInteraction.hideHighlight(hovered.getStack());
        if (!suppressed) paintGradient(left, top, right, bottom, firstColor, secondColor);
    }

    private static void paintGradient(int left, int top, int right, int bottom, int startColor, int endColor) {
        float sa = (startColor >> 24 & 255) / 255.0F;
        float sr = (startColor >> 16 & 255) / 255.0F;
        float sg = (startColor >> 8 & 255) / 255.0F;
        float sb = (startColor & 255) / 255.0F;
        float ea = (endColor >> 24 & 255) / 255.0F;
        float er = (endColor >> 16 & 255) / 255.0F;
        float eg = (endColor >> 8 & 255) / 255.0F;
        float eb = (endColor & 255) / 255.0F;

        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(7425);

        Tessellator batch = Tessellator.instance;
        batch.startDrawing(7);
        batch.setColorRGBA_F(sr, sg, sb, sa); batch.addVertex(right, top, 0.0D);
        batch.setColorRGBA_F(sr, sg, sb, sa); batch.addVertex(left, top, 0.0D);
        batch.setColorRGBA_F(er, eg, eb, ea); batch.addVertex(left, bottom, 0.0D);
        batch.setColorRGBA_F(er, eg, eb, ea); batch.addVertex(right, bottom, 0.0D);
        batch.draw();

        GlStateManager.shadeModel(7424);
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
    }
}

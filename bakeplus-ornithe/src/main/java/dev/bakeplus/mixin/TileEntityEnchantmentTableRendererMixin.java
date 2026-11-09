package dev.bakeplus.mixin;

import dev.bakeplus.BakePlus;
import dev.bakeplus.BakePlusConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBook;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.tileentity.TileEntityEnchantmentTable;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.minecraft.unmapped.C_82248221", remap = false)
public abstract class TileEntityEnchantmentTableRendererMixin {
    private static final ResourceLocation BAKEPLUS_BOOK_TEXTURE =
        new ResourceLocation("textures/entity/enchanting_table_book.png");
    @Unique private final ModelBook bakeplus$book = new ModelBook();

    @Inject(method = "m_40644418", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void bakeplus$staticBook(TileEntityEnchantmentTable table, double x, double y, double z,
                                     float partialTicks, int destroyStage, CallbackInfo ci) {
        if (!BakePlusConfig.staticEnchantingTables) return;
        try {
            bakeplus$drawBook(x, y, z);
            ci.cancel();
        } catch (Throwable failure) {
            BakePlus.report("enchanting table", failure);
        }
    }

    @Unique
    private void bakeplus$drawBook(double x, double y, double z) {
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate((float)x + 0.5F, (float)y + 0.75F, (float)z + 0.5F);
            GlStateManager.rotate(0.0F, 0.0F, 1.0F, 0.0F);
            GlStateManager.rotate(80.0F, 0.0F, 0.0F, 1.0F);
            GlStateManager.enableCull();
            Minecraft.getMinecraft().getTextureManager().bindTexture(BAKEPLUS_BOOK_TEXTURE);
            bakeplus$book.render(null, 0.0F, 0.15F, 0.85F, 1.0F, 0.0F, 0.0625F);
        } finally {
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.popMatrix();
        }
    }
}

package yt.ren.astatine.mixin.geometry;

import java.util.List;
import net.minecraft.client.render.entity.ItemRenderer;
import net.minecraft.client.render.model.block.BakedModel;
import net.minecraft.client.render.model.block.BakedQuad;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Direction;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yt.ren.astatine.Native;
import yt.ren.astatine.compat.GLCompat;
import yt.ren.astatine.memory.Memory;
import yt.ren.astatine.render.geometry.ItemMeshes;
import yt.ren.astatine.render.geometry.VertexLayouts;
import yt.ren.astatine.shaderpack.WorldDraws;

@Mixin(ItemRenderer.class)
public abstract class ItemRendererMeshMixin {
	@Shadow
	private void renderQuads(BufferBuilder builder, List<BakedQuad> quads, int color, ItemStack stack) {
		throw new AssertionError();
	}

	@Inject(method = "render(Lnet/minecraft/client/render/model/block/BakedModel;ILnet/minecraft/item/ItemStack;)V", at = @At("HEAD"), cancellable = true)
	private void astatine$drawMesh(BakedModel model, int color, ItemStack stack, CallbackInfo callback) {
		if (!ItemMeshes.ENABLED || GLCompat.isCompiling()) return;
		long key = ItemMeshes.key(model, color, stack);
		long mesh = ItemMeshes.find(model, key);
		if (mesh == ItemMeshes.NONE) {
			BufferBuilder builder = ItemMeshes.builder;
			builder.begin(GL11.GL_QUADS, DefaultVertexFormat.BLOCK_NORMALS);
			for (Direction direction : Direction.values()) renderQuads(builder, model.getQuads(direction), color, stack);
			renderQuads(builder, model.getQuads(), color, stack);
			builder.end();
			if (builder.getVertexCount() == 0) {
				builder.clear();
				return;
			}
			mesh = Native.uploadMesh(VertexLayouts.of(builder.getFormat()), Memory.INSTANCE.address(builder.getBuffer()), builder.getVertexCount());
			builder.clear();
			ItemMeshes.store(model, key, mesh);
		}
		WorldDraws.drawMesh(mesh, GL11.GL_QUADS);
		callback.cancel();
	}
}

package yt.ren.astatine.mixin.async;

import net.minecraft.client.render.block.BlockRenderer;
import net.minecraft.client.render.vertex.Tesselator;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import yt.ren.astatine.terrain.async.MeshThread;

/**
 * luka: the block renderer draws into Tesselator.INSTANCE everywhere. On a mesh worker it draws into that worker's
 * own Tesselator instead; on any other thread nothing changes.
 */
@Mixin(BlockRenderer.class)
public abstract class BlockRendererAsyncMixin {
	@Redirect(
		method = "*",
		at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/vertex/Tesselator;INSTANCE:Lnet/minecraft/client/render/vertex/Tesselator;", opcode = Opcodes.GETSTATIC),
		require = 0
	)
	private static Tesselator astatine$workerTesselator() {
		Thread thread = Thread.currentThread();
		return thread instanceof MeshThread ? (Tesselator) ((MeshThread) thread).astatine$tesselator() : Tesselator.INSTANCE;
	}
}

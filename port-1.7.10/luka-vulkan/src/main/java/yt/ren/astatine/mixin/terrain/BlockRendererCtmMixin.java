package yt.ren.astatine.mixin.terrain;

import net.minecraft.block.Block;
import net.minecraft.client.render.block.BlockRenderer;
import net.minecraft.client.render.texture.Sprite;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import yt.ren.astatine.terrain.ConnectedTextures;

/**
 * Connected textures: every face the block renderer draws from a world position asks for its sprite here (cubes with
 * and without ambient occlusion, and what is built from them: slabs, stairs, fences, walls, logs, cactus, doors,
 * beds). A forced sprite, as used for the block breaking overlay, replaces the result later in each face method, so it
 * is left alone.
 */
@Mixin(BlockRenderer.class)
public abstract class BlockRendererCtmMixin {
	@Shadow private Sprite forcedSprite;
	@Shadow private int northFaceRotation;
	@Shadow private int southFaceRotation;
	@Shadow private int westFaceRotation;
	@Shadow private int eastFaceRotation;
	@Shadow private int topFaceRotation;
	@Shadow private int bottomFaceRotation;

	@Redirect(
		method = "getSprite(Lnet/minecraft/block/Block;Lnet/minecraft/world/WorldView;IIII)Lnet/minecraft/client/render/texture/Sprite;",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;getSprite(Lnet/minecraft/world/WorldView;IIII)Lnet/minecraft/client/render/texture/Sprite;"),
		require = 0
	)
	private Sprite astatine$connected(Block block, WorldView world, int x, int y, int z, int face) {
		Sprite sprite = block.getSprite(world, x, y, z, face);
		if (!ConnectedTextures.loaded || !ConnectedTextures.enabled || sprite == null || this.forcedSprite != null) {
			return sprite;
		}
		// The west face method reads the field named east and the other way round.
		int rotation = switch (face) {
			case 0 -> this.bottomFaceRotation;
			case 1 -> this.topFaceRotation;
			case 2 -> this.northFaceRotation;
			case 3 -> this.southFaceRotation;
			case 4 -> this.eastFaceRotation;
			case 5 -> this.westFaceRotation;
			default -> 0;
		};
		return ConnectedTextures.sprite(block, world, x, y, z, face, sprite, rotation != 0);
	}
}

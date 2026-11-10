package yt.ren.astatine.mixin.terrain;

import java.util.List;
import net.minecraft.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.Culler;
import net.minecraft.client.render.platform.Lighting;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.living.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yt.ren.astatine.terrain.TerrainRenderer;

/**
 * Replaces 1.7.10's RenderChunk array and display lists with the native terrain sections. Vanilla's entity, block
 * entity, particle, sky and cloud code stays as it is; only chunk bookkeeping and drawing move across.
 */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
	@Shadow public List blockEntitiesToBeRendered;
	@Shadow private ClientWorld world;
	@Shadow private Minecraft minecraft;
	@Shadow private int lastViewDistance;
	@Shadow private int entityRenderCooldown;

	@Shadow
	public abstract void notifyEntityChanged();

	/**
	 * @author luka
	 * @reason Astatine terrain instead of RenderChunk objects
	 */
	@Overwrite
	public void reload() {
		if (this.world == null) {
			return;
		}
		Blocks.LEAVES.setCulling(this.minecraft.options.fancyGraphics);
		Blocks.LEAVES2.setCulling(this.minecraft.options.fancyGraphics);
		this.lastViewDistance = this.minecraft.options.viewDistance;
		this.blockEntitiesToBeRendered.clear();
		this.notifyEntityChanged();
		TerrainRenderer.reload((WorldRenderer) (Object) this, this.world, this.lastViewDistance);
		this.entityRenderCooldown = 2;
	}

	@Inject(method = "setWorld", at = @At("HEAD"))
	private void astatine$forget(ClientWorld world, CallbackInfo callback) {
		if (this.world != null && world != this.world) {
			TerrainRenderer.clear();
		}
	}

	/**
	 * @author luka
	 * @reason Astatine terrain
	 */
	@Overwrite
	public void cullChunks(Culler culler, float tickDelta) {
		LivingEntity camera = this.minecraft.camera;
		if (camera != null) {
			TerrainRenderer.setup(camera, tickDelta);
		}
	}

	/**
	 * @author luka
	 * @reason Astatine terrain: sections are meshed in cullChunks
	 */
	@Overwrite
	public boolean compileChunks(LivingEntity camera, boolean force) {
		TerrainRenderer.accept();
		return true;
	}

	/**
	 * @author luka
	 * @reason Astatine terrain
	 */
	@Overwrite
	public int render(LivingEntity camera, int layer, double tickDelta) {
		if (this.minecraft.options.viewDistance != this.lastViewDistance) {
			this.reload();
		}
		Lighting.turnOff();
		return this.astatine$draw(layer, tickDelta);
	}

	/**
	 * @author luka
	 * @reason Astatine terrain: called by GameRenderer for the second translucent pass with fancy graphics
	 */
	@Overwrite
	public void renderLastChunks(int layer, double tickDelta) {
		this.astatine$draw(layer, tickDelta);
	}

	@org.spongepowered.asm.mixin.Unique
	private int astatine$draw(int layer, double tickDelta) {
		this.minecraft.gameRenderer.enableLightMap(tickDelta);
		int drawn;
		if (layer == 0) {
			drawn = TerrainRenderer.drawOpaqueLayers();
		} else {
			TerrainRenderer.sortTranslucent();
			drawn = TerrainRenderer.drawTranslucent();
		}
		this.minecraft.gameRenderer.disableLightMap(tickDelta);
		return drawn;
	}

	/**
	 * @author luka
	 * @reason Astatine terrain
	 */
	@Overwrite
	public String getChunkDebugInfo() {
		return TerrainRenderer.debug();
	}

	/**
	 * @author luka
	 * @reason Astatine terrain
	 */
	@Overwrite
	public void markDirty(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		TerrainRenderer.markDirty(minX, minY, minZ, maxX, maxY, maxZ, false);
	}

	@Inject(method = "notifyBlockChanged", at = @At("HEAD"))
	private void astatine$notifyImportant(int x, int y, int z, CallbackInfo callback) {
		TerrainRenderer.markDirty(x - 1, y - 1, z - 1, x + 1, y + 1, z + 1, true);
		TerrainRenderer.markImportant(x, y, z);
	}
}

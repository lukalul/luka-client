package yt.ren.astatine.mixin.model;

import net.minecraft.client.render.model.ModelPart;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import yt.ren.astatine.shaderpack.WorldDraws;
import yt.ren.astatine.extension.MeshHolder;
import yt.ren.astatine.render.geometry.ModelBaker;

@Mixin(ModelPart.class)
public abstract class ModelPartMixin implements MeshHolder {
	@Shadow
	private boolean compiled;

	@Unique
	private long astatine$mesh;

	@Override
	public long astatine$mesh() {
		return this.astatine$mesh;
	}

	@Override
	public void astatine$mesh(long mesh) {
		this.astatine$mesh = mesh;
	}

	// TODO: This is instance-able, could make it GPU-skinned.
	//		 Should be looked into as a fast-path.

	/**
	 * @author Ren
	 * @reason We bake the model into a mesh
	 */
	@Overwrite
	private void compile(float scale) {
		this.astatine$mesh = ModelBaker.bake((ModelPart) (Object) this, scale);
		this.compiled = true;
	}

	@Unique
	private static final boolean astatine$FAST = !"off".equalsIgnoreCase(System.getenv("ASTATINE_FAST_PARTS"));

	@org.spongepowered.asm.mixin.Shadow public float x, y, z, rotationX, rotationY, rotationZ, translateX, translateY, translateZ;
	@org.spongepowered.asm.mixin.Shadow public boolean visible, invisible;
	@org.spongepowered.asm.mixin.Shadow public java.util.List<ModelPart> children;

	/** luka: the rotated case of render(), which is nearly every limb of every player, with one matrix step instead of four. */
	@org.spongepowered.asm.mixin.injection.Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void astatine$renderFast(float scale, org.spongepowered.asm.mixin.injection.callback.CallbackInfo callback) {
		if (!astatine$FAST || this.invisible || !this.visible || !this.compiled) return;
		if (this.rotationX == 0.0F && this.rotationY == 0.0F && this.rotationZ == 0.0F) return;
		boolean moved = this.translateX != 0.0F || this.translateY != 0.0F || this.translateZ != 0.0F;
		if (moved) net.minecraft.client.render.platform.GlStateManager.translatef(this.translateX, this.translateY, this.translateZ);
		net.minecraft.client.render.platform.GlStateManager.pushMatrix();
		yt.ren.astatine.render.GL.partTransform(this.x * scale, this.y * scale, this.z * scale, this.rotationZ, this.rotationY, this.rotationX);
		if (this.astatine$mesh != 0L) WorldDraws.drawMesh(this.astatine$mesh, GL11.GL_QUADS);
		if (this.children != null) for (int i = 0; i < this.children.size(); i++) this.children.get(i).render(scale);
		net.minecraft.client.render.platform.GlStateManager.popMatrix();
		if (moved) net.minecraft.client.render.platform.GlStateManager.translatef(-this.translateX, -this.translateY, -this.translateZ);
		callback.cancel();
	}

	@Redirect(method = {"render", "renderForceTransform"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;callList(I)V"))
	private void astatine$draw(int list) {
		if (this.astatine$mesh != 0L) {
			WorldDraws.drawMesh(this.astatine$mesh, GL11.GL_QUADS);
		}
	}
}

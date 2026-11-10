package yt.ren.astatine.mixin.target;

import net.minecraft.client.render.pipeline.RenderTarget;
import net.minecraft.client.render.vertex.Tesselator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yt.ren.astatine.Native;
import yt.ren.astatine.compat.DeferredScreenshot;
import yt.ren.astatine.compat.GLCompat;

/**
 * luka: the game copies its picture over the whole window with a textured quad. That copy is handed to the renderer
 * as a plain image copy instead, which lets the renderer draw the game straight into the window while nothing else
 * reads the picture (see Renderer::updateDirect). Binding the picture as a texture would count as reading it.
 */
@Mixin(RenderTarget.class)
public abstract class RenderTargetMixin {
	@Shadow
	public int width;

	@Shadow
	public int height;

	@Shadow
	public int viewWidth;

	@Shadow
	public int viewHeight;

	@Shadow
	public int frameBufferId;

	@Unique
	private boolean astatine$blitted;

	@Unique
	private int astatine$drawWidth;

	@Unique
	private int astatine$drawHeight;

	@Inject(method = "draw(II)V", at = @At("HEAD"))
	private void astatine$beginDraw(int width, int height, CallbackInfo callback) {
		this.astatine$blitted = false;
		this.astatine$drawWidth = width;
		this.astatine$drawHeight = height;
		if (this.viewWidth == this.width && this.viewHeight == this.height) DeferredScreenshot.frameDrawn((RenderTarget) (Object) this);
	}

	@Inject(method = "draw(II)V", at = @At("RETURN"))
	private void astatine$endDraw(int width, int height, CallbackInfo callback) {
		this.astatine$blitted = false;
	}

	@Redirect(method = "draw(II)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/pipeline/RenderTarget;bindRead()V"))
	private void astatine$bindRead(RenderTarget target) {
		long framebuffer = GLCompat.framebufferHandle(this.frameBufferId);
		if (framebuffer != 0L && this.viewWidth == this.width && this.viewHeight == this.height
			&& Native.framebufferBlit(framebuffer, this.astatine$drawWidth, this.astatine$drawHeight)) {
			this.astatine$blitted = true;
			return;
		}
		target.bindRead();
	}

	@Redirect(method = "draw(II)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/pipeline/RenderTarget;unbindRead()V"))
	private void astatine$unbindRead(RenderTarget target) {
		if (!this.astatine$blitted) target.unbindRead();
	}

	@Redirect(method = "draw(II)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/Tesselator;begin()V"))
	private void astatine$begin(Tesselator tesselator) {
		if (!this.astatine$blitted) tesselator.begin();
	}

	@Redirect(method = "draw(II)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/Tesselator;color(I)V"))
	private void astatine$color(Tesselator tesselator, int color) {
		if (!this.astatine$blitted) tesselator.color(color);
	}

	@Redirect(method = "draw(II)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/Tesselator;vertex(DDDDD)V"))
	private void astatine$vertex(Tesselator tesselator, double x, double y, double z, double u, double v) {
		if (!this.astatine$blitted) tesselator.vertex(x, y, z, u, v);
	}

	@Redirect(method = "draw(II)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/Tesselator;end()I"))
	private int astatine$end(Tesselator tesselator) {
		return this.astatine$blitted ? 0 : tesselator.end();
	}
}

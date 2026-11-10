package yt.ren.astatine.mixin.client;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.texture.TextureManager;
import net.minecraft.client.resource.Identifier;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yt.ren.astatine.compat.GLCompat;

/**
 * 1.7.10 draws every glyph with its own glBegin/glEnd. These redirects batch consecutive glyphs into one draw, flushing
 * whenever the colour, texture or a textured-off rectangle (underline, strikethrough) forces a break.
 */
@Mixin(TextRenderer.class)
public abstract class TextRendererMixin {
	@Unique
	private static final int STRIDE = 20;

	@Unique
	private ByteBuffer astatine$batch = ByteBuffer.allocateDirect(STRIDE * 4 * 256).order(ByteOrder.nativeOrder());

	@Unique
	private int astatine$vertices;

	@Unique
	private final float[] astatine$corners = new float[20];

	@Unique
	private int astatine$corner;

	@Unique
	private Identifier astatine$texture;

	@Unique
	private void astatine$flush() {
		if (this.astatine$vertices > 0) {
			GLCompat.drawPositionTex(this.astatine$batch, this.astatine$vertices);
			this.astatine$vertices = 0;
			this.astatine$batch.clear();
		}
	}

	@Redirect(method = {"drawBasicGlyph", "bindFontPageTexture"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/texture/TextureManager;bind(Lnet/minecraft/client/resource/Identifier;)V"))
	private void astatine$bindFont(TextureManager textureManager, Identifier texture) {
		if (texture != this.astatine$texture) {
			this.astatine$flush();
			textureManager.bind(texture);
			this.astatine$texture = texture;
		}
	}

	@Redirect(method = {"drawBasicGlyph", "drawUnicodeGlyph"}, at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glBegin(I)V"))
	private void astatine$begin(int mode) {
		this.astatine$corner = 0;
	}

	@Redirect(method = {"drawBasicGlyph", "drawUnicodeGlyph"}, at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glTexCoord2f(FF)V"))
	private void astatine$texCoord(float u, float v) {
		int at = this.astatine$corner * 5;
		this.astatine$corners[at + 3] = u;
		this.astatine$corners[at + 4] = v;
	}

	@Redirect(method = {"drawBasicGlyph", "drawUnicodeGlyph"}, at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glVertex3f(FFF)V"))
	private void astatine$vertex(float x, float y, float z) {
		int at = this.astatine$corner++ * 5;
		this.astatine$corners[at] = x;
		this.astatine$corners[at + 1] = y;
		this.astatine$corners[at + 2] = z;
	}

	@Redirect(method = {"drawBasicGlyph", "drawUnicodeGlyph"}, at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glEnd()V"))
	private void astatine$end() {
		if (this.astatine$batch.remaining() < STRIDE * 4) {
			this.astatine$flush();
		}
		this.astatine$emit(0);
		this.astatine$emit(1);
		this.astatine$emit(3);
		this.astatine$emit(2);
		this.astatine$vertices += 4;
	}

	@Unique
	private void astatine$emit(int corner) {
		float[] corners = this.astatine$corners;
		int at = corner * 5;
		this.astatine$batch.putFloat(corners[at]).putFloat(corners[at + 1]).putFloat(corners[at + 2]).putFloat(corners[at + 3]).putFloat(corners[at + 4]);
	}

	@Redirect(method = "drawLayer(Ljava/lang/String;Z)V", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glColor4f(FFFF)V"))
	private void astatine$color(float r, float g, float b, float a) {
		this.astatine$flush();
		GL11.glColor4f(r, g, b, a);
	}

	@Redirect(method = "drawLayer(Ljava/lang/String;Z)V", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDisable(I)V"))
	private void astatine$disable(int capability) {
		this.astatine$flush();
		GL11.glDisable(capability);
	}

	@Inject(method = "drawLayer(Ljava/lang/String;Z)V", at = @At("RETURN"))
	private void astatine$finish(String text, boolean shadow, CallbackInfo callback) {
		this.astatine$flush();
		this.astatine$texture = null;
	}
}

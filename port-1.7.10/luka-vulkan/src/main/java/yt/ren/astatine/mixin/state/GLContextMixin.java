package yt.ren.astatine.mixin.state;

import org.lwjgl.opengl.ContextCapabilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import yt.ren.astatine.render.GLCapabilities;

/**
 * The game checks OpenGL capabilities in several places (GLX, OpenGlCapabilities, the title and stream screens,
 * GameRenderer). There is no OpenGL context to ask, so every one of them gets the renderer's own answer.
 */
@Mixin(targets = "org.lwjgl.opengl.GLContext", remap = false)
public abstract class GLContextMixin {
	/**
	 * @author Ren
	 * @reason No OpenGL context under the Vulkan renderer
	 */
	@Overwrite
	public static ContextCapabilities getCapabilities() {
		return GLCapabilities.getCapabilities();
	}
}

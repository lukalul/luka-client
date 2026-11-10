package yt.ren.astatine.mixin.state;

import net.minecraft.client.render.platform.GLX;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(GLX.class)
public abstract class GLXMixin {
	/**
	 * @author Ren
	 * @reason Framebuffers are always available here; the main framebuffer is what the renderer presents
	 */
	@Overwrite
	public static boolean useFbo() {
		return true;
	}
}

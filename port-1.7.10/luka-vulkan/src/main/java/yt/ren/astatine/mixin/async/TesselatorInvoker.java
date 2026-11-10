package yt.ren.astatine.mixin.async;

import net.minecraft.client.render.vertex.Tesselator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** luka: a Tesselator of its own for each mesh worker (the game's constructor is private). */
@Mixin(Tesselator.class)
public interface TesselatorInvoker {
	@Invoker("<init>")
	static Tesselator astatine$create(int bufferSize) {
		throw new AssertionError();
	}
}

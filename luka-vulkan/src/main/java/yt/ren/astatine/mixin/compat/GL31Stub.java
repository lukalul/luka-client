package yt.ren.astatine.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;

/** Empty: exists so GLCompatPlugin gets to rewrite this LWJGL class. */
@Mixin(targets = "org.lwjgl.opengl.GL31", remap = false)
public abstract class GL31Stub {
}

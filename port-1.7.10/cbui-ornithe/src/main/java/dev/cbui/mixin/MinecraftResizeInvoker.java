package dev.cbui.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The game's own resize (new framebuffer, menus laid out again), for the Resolution setting. */
@Mixin(Minecraft.class)
public interface MinecraftResizeInvoker {
  @Invoker("resize")
  void cbui$resize(int width, int height);
}

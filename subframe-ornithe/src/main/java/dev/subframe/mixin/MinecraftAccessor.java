package dev.subframe.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Timer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Accessor("timer") Timer subframe$getTimer();
    @Accessor("timer") void subframe$setTimer(Timer timer);
    @Invoker("resize") void subframe$resize(int width, int height);
}

package dev.subframe.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Session;
import net.minecraft.util.Timer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Accessor("timer") Timer subframe$getTimer();
    @Accessor("timer") void subframe$setTimer(Timer timer);
    @Accessor("session") Session subframe$getSession();
    @Accessor("session") @Mutable void subframe$setSession(Session session);
    @Invoker("resize") void subframe$resize(int width, int height);
}

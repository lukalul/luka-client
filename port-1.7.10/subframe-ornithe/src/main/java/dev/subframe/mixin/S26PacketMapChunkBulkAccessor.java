package dev.subframe.mixin;

import net.minecraft.network.play.server.S26PacketMapChunkBulk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Whether a bulk chunk packet carries sky light; the packet has no getter for it. */
@Mixin(S26PacketMapChunkBulk.class)
public interface S26PacketMapChunkBulkAccessor {
    @Accessor("f_15276608") boolean subframe$hasSkyLight();
}

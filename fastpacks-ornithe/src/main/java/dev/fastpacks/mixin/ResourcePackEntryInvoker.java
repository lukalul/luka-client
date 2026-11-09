package dev.fastpacks.mixin;

import java.io.File;
import net.minecraft.client.resources.ResourcePackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ResourcePackRepository.Entry.class)
public interface ResourcePackEntryInvoker {
    @Invoker("<init>")
    static ResourcePackRepository.Entry fastpacks$create(ResourcePackRepository owner, File file) {
        throw new AssertionError();
    }
}

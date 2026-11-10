package dev.fastpacks.mixin;

import dev.fastpacks.FastPacks;
import dev.fastpacks.pack.ExtraPacks;
import dev.fastpacks.pack.NestedPacks;
import java.io.File;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.ResourcePackRepository;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A pack inside a sub-folder is known by its path below resourcepacks ("block overlays/Pack.zip"), which is what FastPacks saves in
 * the options. That lets the game match it again on the next launch and keeps two packs with the same file name apart.
 */
@Mixin(ResourcePackRepository.Entry.class)
public abstract class ResourcePackEntryMixin {
    @Shadow @Final private File resourcePackFile;

    private String fastpacks$nestedName() {
        File root = new File(Minecraft.getMinecraft().mcDataDir, "resourcepacks");
        // a pack from the standard launcher's folder is known as ".minecraft/<path>"
        String extra = ExtraPacks.nameOf(root, resourcePackFile);
        return extra != null ? extra : NestedPacks.nestedName(root, resourcePackFile);
    }

    @Inject(method = "getResourcePackName", at = @At("RETURN"), cancellable = true, require = 0)
    private void fastpacks$name(CallbackInfoReturnable<String> cir) {
        try {
            String nested = fastpacks$nestedName();
            if (nested != null) cir.setReturnValue(nested);
        } catch (Throwable t) {
            FastPacks.reportOnce("nested pack name", t);
        }
    }

    @Inject(method = "toString", at = @At("RETURN"), cancellable = true, require = 0)
    private void fastpacks$string(CallbackInfoReturnable<String> cir) {
        try {
            String nested = fastpacks$nestedName();
            if (nested != null) cir.setReturnValue(nested + ":" + resourcePackFile.isDirectory() + ":" + resourcePackFile.lastModified());
        } catch (Throwable t) {
            FastPacks.reportOnce("nested pack key", t);
        }
    }
}

package dev.fastpacks.mixin;

import dev.fastpacks.FastPacks;
import dev.fastpacks.pack.ExtraPacks;
import dev.fastpacks.pack.NestedPacks;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.ResourcePackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Makes the game's list of available packs include the ones inside sub-folders of resourcepacks, so they can be selected and saved. */
@Mixin(ResourcePackRepository.class)
public abstract class ResourcePackRepositoryMixin {
    @Inject(method = "getResourcePackFiles", at = @At("RETURN"), cancellable = true, require = 0)
    private void fastpacks$nested(CallbackInfoReturnable<List<File>> cir) {
        try {
            List<File> files = new ArrayList<File>(cir.getReturnValue());
            File root = new File(Minecraft.getMinecraft().mcDataDir, "resourcepacks");
            NestedPacks.collect(root, files);
            // the standard launcher's packs too (read only), so a pack chosen from there is found again at startup
            ExtraPacks.collect(root, files);
            cir.setReturnValue(files);
        } catch (Throwable t) {
            FastPacks.reportOnce("nested pack listing", t);
        }
    }
}

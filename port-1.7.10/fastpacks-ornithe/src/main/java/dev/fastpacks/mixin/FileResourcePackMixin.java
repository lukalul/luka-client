package dev.fastpacks.mixin;

import dev.fastpacks.FastPacks;
import dev.fastpacks.pack.WrappedZips;
import net.minecraft.client.resources.FileResourcePack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Lets zip packs that were zipped with a wrapper folder ("MyPack/assets/...") load: when a zip has no top-level pack.mcmeta or
 * assets folder but has one inside a single wrapper folder, every lookup is shifted into that folder. Normal zips are untouched
 * (the root is "" and each hook returns at once). Everything is guarded so a failure falls back to vanilla behaviour.
 */
@Mixin(FileResourcePack.class)
public abstract class FileResourcePackMixin {
    @Unique private String fastpacks$root;

    @Shadow private ZipFile getResourcePackZipFile() throws IOException { throw new AssertionError(); }

    @Unique
    private String fastpacks$root() {
        String root = fastpacks$root;
        if (root != null) return root;
        root = "";
        try {
            root = WrappedZips.findRoot(getResourcePackZipFile().entries());
        } catch (Throwable t) {
            FastPacks.reportOnce("wrapped zip detection", t);
        }
        return fastpacks$root = root;
    }

    @Inject(method = "getInputStreamByName", at = @At("HEAD"), cancellable = true, require = 0)
    private void fastpacks$stream(String name, CallbackInfoReturnable<InputStream> cir) throws IOException {
        String root = fastpacks$root();
        if (root.isEmpty()) return;
        ZipFile zip = getResourcePackZipFile();
        ZipEntry entry = zip.getEntry(root + name);
        if (entry == null) throw new FileNotFoundException(name);
        cir.setReturnValue(zip.getInputStream(entry));
    }

    @Inject(method = "hasResourceName", at = @At("HEAD"), cancellable = true, require = 0)
    private void fastpacks$has(String name, CallbackInfoReturnable<Boolean> cir) {
        String root = fastpacks$root();
        if (root.isEmpty()) return;
        try {
            cir.setReturnValue(getResourcePackZipFile().getEntry(root + name) != null);
        } catch (IOException e) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "getResourceDomains", at = @At("HEAD"), cancellable = true, require = 0)
    private void fastpacks$domains(CallbackInfoReturnable<Set<String>> cir) {
        String root = fastpacks$root();
        if (root.isEmpty()) return;
        Set<String> domains = new HashSet<String>();
        try {
            String prefix = root + "assets/";
            Enumeration<? extends ZipEntry> entries = getResourcePackZipFile().entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (!name.startsWith(prefix)) continue;
                String rest = name.substring(prefix.length());
                int slash = rest.indexOf('/');
                if (slash <= 0) continue;
                String domain = rest.substring(0, slash);
                if (domain.equals(domain.toLowerCase())) domains.add(domain);
            }
        } catch (IOException ignored) {
        }
        cir.setReturnValue(domains);
    }
}

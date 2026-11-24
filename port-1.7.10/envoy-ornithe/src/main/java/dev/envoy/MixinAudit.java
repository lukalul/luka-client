package dev.envoy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Startup check of Envoy's optional hooks (every injection is {@code require = 0}, so a hook that finds nothing is
 * silent). This class is also the mixin config plugin of envoy.mixins.json: right after Mixin has applied one of our
 * mixins it hands over the finished class, and every merged {@code envoy$} handler is counted as in place only if
 * the class really calls it somewhere, which is exactly what Mixin's injectors resolved. No class names are looked
 * up by string, so the result is the same in development, on intermediary names and in the obfuscated build.
 * {@link #report()} loads the target classes that are not loaded yet and prints one line.
 */
public final class MixinAudit implements IMixinConfigPlugin {
    private static final String HANDLER_TOKEN = "envoy$";
    private static final String MERGED = "Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;";
    private static final Set<String> DECLARED = Collections.synchronizedSet(new TreeSet<String>());
    private static final Map<String, String> RESULTS = Collections.synchronizedMap(new TreeMap<String, String>());
    private static final Set<String> MISSING = Collections.synchronizedSet(new TreeSet<String>());
    private static int hooksInPlace, hooksTotal;

    public MixinAudit() { }

    public static void report() {
        int loaded = MixinAuditTargets.load().length;
        List<String> notApplied = new ArrayList<String>();
        synchronized (DECLARED) {
            for (String mixin : DECLARED) if (!RESULTS.containsKey(mixin)) notApplied.add(mixin);
        }
        if (DECLARED.isEmpty() && RESULTS.isEmpty()) {
            Envoy.LOGGER.warn("[Mixin] hook check unavailable: the mixin plugin was not called ({} targets loaded)", loaded);
            return;
        }
        int inPlace, total;
        synchronized (MixinAudit.class) { inPlace = hooksInPlace; total = hooksTotal; }
        Envoy.LOGGER.info("[Mixin] hooks in place {}/{} across {} mixins{}{} | {}", inPlace, total, RESULTS.size(),
            MISSING.isEmpty() ? "" : "; NOT IN PLACE " + MISSING,
            notApplied.isEmpty() ? "" : "; mixins not applied " + notApplied, RESULTS);
    }

    private static void record(ClassNode target, String mixinClassName) {
        List<String> handlers = new ArrayList<String>();
        for (MethodNode method : target.methods) {
            if (method.name.contains(HANDLER_TOKEN) && mergedFrom(method, mixinClassName)) handlers.add(method.name);
        }
        Set<String> called = new HashSet<String>();
        for (MethodNode method : target.methods) {
            if (method.instructions == null) continue;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.owner.equals(target.name) && !call.name.equals(method.name)) called.add(call.name);
            }
        }
        int inPlace = 0;
        String mixin = simpleName(mixinClassName);
        for (String handler : handlers) {
            if (called.contains(handler)) inPlace++;
            else MISSING.add(mixin + "." + handler.substring(handler.indexOf(HANDLER_TOKEN) + HANDLER_TOKEN.length()));
        }
        synchronized (MixinAudit.class) {
            hooksInPlace += inPlace;
            hooksTotal += handlers.size();
        }
        RESULTS.put(mixin, inPlace + "/" + handlers.size());
    }

    /** Merged from this mixin (Mixin tags every merged method); without the tag any envoy$ method counts. */
    private static boolean mergedFrom(MethodNode method, String mixinClassName) {
        if (method.visibleAnnotations == null) return true;
        for (AnnotationNode annotation : method.visibleAnnotations) {
            if (!MERGED.equals(annotation.desc) || annotation.values == null) continue;
            for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
                if ("mixin".equals(annotation.values.get(i))) return mixinClassName.equals(annotation.values.get(i + 1));
            }
        }
        return true;
    }

    private static String simpleName(String className) {
        return className.substring(className.lastIndexOf('.') + 1);
    }

    @Override
    public void onLoad(String mixinPackage) { }

    @Override
    public String getRefMapperConfig() { return null; }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        DECLARED.add(simpleName(mixinClassName));
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }

    @Override
    public List<String> getMixins() { return null; }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        try {
            record(targetClass, mixinClassName);
        } catch (Throwable failure) {
            RESULTS.put(simpleName(mixinClassName), "check failed: " + failure);
        }
    }
}

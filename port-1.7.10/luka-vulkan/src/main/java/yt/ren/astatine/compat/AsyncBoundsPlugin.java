package yt.ren.astatine.compat;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import yt.ren.astatine.terrain.async.AsyncSwitch;

/**
 * luka: the mixin plugin of {@code astatine-async.mixins.json}. Applies those mixins only when async meshing is on
 * ({@link AsyncSwitch}) and, after they are applied, rewrites every GETFIELD and PUTFIELD of a block bounds field in
 * the target class into a call to {@code terrain/async/BlockBounds}, which picks the game's field or the current
 * mesh worker's copy. The targets are Block and the ten block classes that touch those fields directly (found by
 * scanning every class of the 1.7.10 client); no other class does, they use Block's getters.
 */
public final class AsyncBoundsPlugin implements IMixinConfigPlugin {
	private static final Logger LOGGER = LogManager.getLogger("Astatine/AsyncTerrain");
	private static final String BOUNDS = "yt/ren/astatine/terrain/async/BlockBounds";
	private static final String SOURCE = "/yt/ren/astatine/mixin/async/BlockBoundsMixin.class";

	/** The six field names as the game runs with them, minX to maxZ; null when they could not be read. */
	private String[] names;

	@Override
	public void onLoad(String mixinPackage) {
		if (!AsyncSwitch.ENABLED) return;
		try (InputStream in = AsyncBoundsPlugin.class.getResourceAsStream(SOURCE)) {
			if (in == null) throw new IllegalStateException("BlockBoundsMixin class not found");
			ClassNode node = new ClassNode();
			new ClassReader(in).accept(node, ClassReader.SKIP_CODE);
			List<String> found = new ArrayList<>();
			for (FieldNode field : node.fields) {
				if ((field.access & Opcodes.ACC_STATIC) == 0 && "D".equals(field.desc)) found.add(field.name);
			}
			if (found.size() != 6) throw new IllegalStateException("expected 6 bounds fields, found " + found);
			names = found.toArray(new String[0]);
		} catch (Throwable error) {
			// without the names no async mixin applies, so Block does not implement BoundsAccess and TerrainWorkers stays off
			names = null;
			LOGGER.error("Async terrain off: block bounds fields not found", error);
		}
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		return AsyncSwitch.ENABLED && names != null;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
		if (names == null || !mixinClassName.contains(".async.Bounds") && !mixinClassName.endsWith(".BlockBoundsMixin")) return;
		int count = 0;
		for (MethodNode method : targetClass.methods) {
			if (method.name.startsWith("astatine$raw")) continue;
			for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; ) {
				AbstractInsnNode next = insn.getNext();
				if (insn instanceof FieldInsnNode) {
					FieldInsnNode field = (FieldInsnNode) insn;
					int index = index(field);
					if (index >= 0 && field.owner.equals(targetClass.name)) {
						boolean read = field.getOpcode() == Opcodes.GETFIELD;
						MethodInsnNode call = new MethodInsnNode(Opcodes.INVOKESTATIC, BOUNDS, (read ? "get" : "set") + index,
							read ? "(Ljava/lang/Object;)D" : "(Ljava/lang/Object;D)V", false);
						method.instructions.set(insn, call);
						count++;
					}
				}
				insn = next;
			}
		}
		LOGGER.info("Async terrain: {} block bounds accesses in {} go through BlockBounds", count, targetClassName);
	}

	private int index(FieldInsnNode field) {
		if (!"D".equals(field.desc)) return -1;
		if (field.getOpcode() != Opcodes.GETFIELD && field.getOpcode() != Opcodes.PUTFIELD) return -1;
		for (int i = 0; i < 6; i++) if (names[i].equals(field.name)) return i;
		return -1;
	}
}

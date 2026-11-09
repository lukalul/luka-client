package yt.ren.astatine.compat;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Rewrites LWJGL's GL classes after the (empty) stub mixins are applied: every static gl* / ngl* method forwards to the
 * same-named method in {@link GLCompat} when one exists and otherwise does nothing and returns zero.
 */
public final class GLCompatPlugin implements IMixinConfigPlugin {
	private static final String COMPAT = "yt/ren/astatine/compat/GLCompat";
	private final Set<String> implemented = new HashSet<>();

	@Override
	public void onLoad(String mixinPackage) {
		try (InputStream in = GLCompatPlugin.class.getResourceAsStream("/" + COMPAT + ".class")) {
			if (in == null) throw new IllegalStateException("GLCompat class not found");
			ClassNode node = new ClassNode();
			new ClassReader(in).accept(node, ClassReader.SKIP_CODE);
			for (MethodNode method : node.methods) {
				if ((method.access & Opcodes.ACC_PUBLIC) != 0 && (method.access & Opcodes.ACC_STATIC) != 0) {
					implemented.add(method.name + method.desc);
				}
			}
		} catch (IOException error) {
			throw new IllegalStateException(error);
		}
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
		for (MethodNode method : targetClass.methods) {
			if ((method.access & Opcodes.ACC_STATIC) == 0) continue;
			if (!method.name.startsWith("gl") && !method.name.startsWith("ngl")) continue;
			rewrite(method);
		}
	}

	private void rewrite(MethodNode method) {
		method.access &= ~Opcodes.ACC_NATIVE;
		method.instructions = new InsnList();
		if (method.tryCatchBlocks != null) method.tryCatchBlocks.clear();
		if (method.localVariables != null) method.localVariables.clear();
		method.visibleAnnotations = null;
		method.invisibleAnnotations = null;
		Type returnType = Type.getReturnType(method.desc);
		int argumentSize = Type.getArgumentsAndReturnSizes(method.desc) >> 2;
		if (implemented.contains(method.name + method.desc)) {
			int slot = 0;
			for (Type argument : Type.getArgumentTypes(method.desc)) {
				method.instructions.add(new VarInsnNode(argument.getOpcode(Opcodes.ILOAD), slot));
				slot += argument.getSize();
			}
			method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, COMPAT, method.name, method.desc, false));
		} else {
			switch (returnType.getSort()) {
				case Type.VOID:
					break;
				case Type.LONG:
					method.instructions.add(new InsnNode(Opcodes.LCONST_0));
					break;
				case Type.FLOAT:
					method.instructions.add(new InsnNode(Opcodes.FCONST_0));
					break;
				case Type.DOUBLE:
					method.instructions.add(new InsnNode(Opcodes.DCONST_0));
					break;
				case Type.ARRAY:
				case Type.OBJECT:
					method.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
					break;
				default:
					method.instructions.add(new InsnNode(Opcodes.ICONST_0));
					break;
			}
		}
		method.instructions.add(new InsnNode(returnType.getOpcode(Opcodes.IRETURN)));
		method.maxLocals = argumentSize;
		method.maxStack = Math.max(argumentSize, 2);
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		return true;
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
}

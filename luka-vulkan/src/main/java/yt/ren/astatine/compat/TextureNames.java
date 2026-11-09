package yt.ren.astatine.compat;

import java.util.HashMap;
import java.util.Map;

/** Maps OpenGL-style int texture names to the renderer's texture objects, for mods that bind textures by name. */
public final class TextureNames {
	private static final Map<Integer, Long> NAMES = new HashMap<>();
	private static int current;
	private static int nextName = 1 << 24; // above the names vanilla's texture code hands out

	private TextureNames() {
	}

	public static void put(int name, long texture) {
		NAMES.put(name, texture);
	}

	public static int allocate() {
		return nextName++;
	}

	public static boolean has(int name) {
		return NAMES.containsKey(name);
	}

	public static void remove(int name) {
		NAMES.remove(name);
	}

	public static long handle(int name) {
		current = name;
		Long texture = NAMES.get(name);
		return texture == null ? 0L : texture;
	}

	public static int current() {
		return current;
	}
}

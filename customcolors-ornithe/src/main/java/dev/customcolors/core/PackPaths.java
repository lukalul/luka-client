package dev.customcolors.core;

/** Resolves the path shortcuts used inside pack property files ({@code ./} and {@code ~/}). */
public final class PackPaths {
    private PackPaths() {
    }

    /**
     * Returns {namespace, path}. {@code owner} is the path of the properties file inside the
     * {@code minecraft} namespace, e.g. {@code mcpatcher/anim/sub/pig.properties}.
     * {@code ./x} = next to that file, {@code ~/x} = its root folder, {@code ns:path} as written,
     * anything else a path in the {@code minecraft} namespace.
     */
    public static String[] resolve(String value, String owner) {
        String v = value.trim().replace('\\', '/');
        if (v.startsWith("./")) {
            int slash = owner.lastIndexOf('/');
            String dir = slash < 0 ? "" : owner.substring(0, slash + 1);
            return new String[] {"minecraft", dir + v.substring(2)};
        }
        if (v.startsWith("~/")) {
            int slash = owner.indexOf('/');
            String root = slash < 0 ? "" : owner.substring(0, slash + 1);
            return new String[] {"minecraft", root + v.substring(2)};
        }
        int colon = v.indexOf(':');
        if (colon > 0) {
            return new String[] {v.substring(0, colon), v.substring(colon + 1)};
        }
        while (v.startsWith("/")) {
            v = v.substring(1);
        }
        return new String[] {"minecraft", v};
    }
}

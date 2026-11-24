package dev.customcolors.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Finds the numbered alternates of an entity texture among the files the selected packs carry. */
public final class SkinPaths {
    public static final String ENTITY_DIR = "textures/entity/";

    private SkinPaths() {
    }

    /**
     * Alternate resource paths (number 2, 3, ...) for {@code texturePath} such as
     * {@code textures/entity/cow/cow.png}. {@code filesByRoot[i]} holds the paths under
     * {@code roots[i] + "/mob/"} (e.g. {@code cow/cow2.png}). The first root with any alternate wins;
     * numbering stops at the first gap. Returns an empty array when there is none.
     */
    public static String[] alternates(String texturePath, String[] roots, Set<String>[] filesByRoot) {
        if (texturePath == null || !texturePath.startsWith(ENTITY_DIR) || !texturePath.endsWith(".png")) {
            return new String[0];
        }
        String rel = texturePath.substring(ENTITY_DIR.length(), texturePath.length() - 4);
        int slash = rel.lastIndexOf('/');
        String flat = slash < 0 ? rel : rel.substring(slash + 1);
        for (int r = 0; r < roots.length; r++) {
            Set<String> files = filesByRoot[r];
            if (files == null || files.isEmpty()) {
                continue;
            }
            List<String> found = new ArrayList<String>();
            for (int n = 2; ; n++) {
                String nested = rel + n + ".png";
                String plain = flat + n + ".png";
                if (files.contains(nested)) {
                    found.add(roots[r] + "/mob/" + nested);
                } else if (files.contains(plain)) {
                    found.add(roots[r] + "/mob/" + plain);
                } else {
                    break;
                }
            }
            if (!found.isEmpty()) {
                return found.toArray(new String[0]);
            }
        }
        return new String[0];
    }
}

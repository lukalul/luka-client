package dev.fastpacks.preview;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The textures read from one pack for the hover slideshow: items tagged with the group (swords, armor, blocks...) they belong to. */
public final class PackPreview {
    public final List<Item> items;
    /** Group ids that have at least one item, in slideshow order. */
    public final List<Integer> groups;

    public PackPreview(List<Item> items) {
        this.items = Collections.unmodifiableList(items);
        List<Integer> present = new ArrayList<>();
        for (Item item : items) {
            if (!present.contains(item.group)) present.add(item.group);
        }
        this.groups = Collections.unmodifiableList(present);
    }

    public static final class Item {
        public final String label;
        public final BufferedImage image;
        public final int group;

        Item(String label, BufferedImage image, int group) {
            this.label = label;
            this.image = image;
            this.group = group;
        }
    }
}

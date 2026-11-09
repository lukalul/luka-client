package dev.envoy.module.notification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import dev.envoy.module.nametag.AdventureText.Run;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

public final class NotificationState {
    public static final class Notification {
        public final List<List<Run>> titleLines;
        public final List<List<Run>> descriptionLines;
        public final ItemStack itemIcon;
        public final ResourceLocation textureIcon;
        public final long createdAt;
        public final long displayMillis;

        public Notification(List<List<Run>> titleLines, List<List<Run>> descriptionLines,
                            ItemStack itemIcon, ResourceLocation textureIcon,
                            long createdAt, long displayMillis) {
            this.titleLines = immutableLines(titleLines);
            this.descriptionLines = immutableLines(descriptionLines);
            this.itemIcon = itemIcon;
            this.textureIcon = textureIcon;
            this.createdAt = createdAt;
            this.displayMillis = displayMillis;
        }

        public long elapsed(long now) {
            return now - createdAt;
        }

        public long remaining(long now) {
            return displayMillis - elapsed(now);
        }

        private static List<List<Run>> immutableLines(List<List<Run>> input) {
            List<List<Run>> output = new ArrayList<>(input.size());
            for (List<Run> line : input) output.add(Collections.unmodifiableList(new ArrayList<>(line)));
            return Collections.unmodifiableList(output);
        }
    }

    private static final ConcurrentLinkedDeque<Notification> QUEUE = new ConcurrentLinkedDeque<>();

    private NotificationState() {
    }

    public static void add(Notification notification) {
        if (notification != null) QUEUE.offerLast(notification);
    }

    public static void remove(Notification notification) {
        if (notification != null) QUEUE.removeFirstOccurrence(notification);
    }

    public static void resetAll() {
        QUEUE.clear();
    }

    public static List<Notification> all() {
        return new ArrayList<>(QUEUE);
    }

    public static boolean isEmpty() {
        return QUEUE.peekFirst() == null;
    }
}

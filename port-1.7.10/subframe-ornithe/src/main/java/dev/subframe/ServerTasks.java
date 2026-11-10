package dev.subframe;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/** 1.7.10's integrated server has no task queue: MinecraftServerMixin runs these at the start of each server tick. */
public final class ServerTasks {
    private static final Queue<Runnable> QUEUE = new ConcurrentLinkedQueue<Runnable>();

    private ServerTasks() {
    }

    static void run(Runnable task) {
        QUEUE.add(task);
    }

    public static void drain() {
        for (Runnable task = QUEUE.poll(); task != null; task = QUEUE.poll()) {
            try {
                task.run();
            } catch (Throwable t) {
                SubFrameMod.problem("server task", t);
            }
        }
    }
}

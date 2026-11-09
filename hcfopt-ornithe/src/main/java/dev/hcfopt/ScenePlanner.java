package dev.hcfopt;

import java.util.ArrayList;
import java.util.List;

/** Pure, deterministic command planner for the integrated-server benchmark scene. */
public final class ScenePlanner {
    public static final int ORIGIN_X = 0;
    public static final int ORIGIN_Y = 4;
    public static final int ORIGIN_Z = 0;

    private ScenePlanner() { }

    public static List<String> plan(int hopperCount, int itemCount, int mobCount, int fallingCount, int tntCount,
                                    int chestCount, int furnaceCount, int signCount) {
        List<String> commands = new ArrayList<String>();
        commands.add("/gamerule doMobSpawning false");
        commands.add("/gamerule doDaylightCycle false");
        commands.add("/time set 6000");
        commands.add("/weather clear 1000000");
        commands.add("/kill @e[type=!Player]");
        commands.add("/fill -64 3 -64 64 3 64 stone");
        // One Y plane per command keeps every fill below vanilla's 32,768-block limit.
        for (int y = 4; y <= 24; y++) commands.add("/fill -64 " + y + " -64 64 " + y + " 64 air");

        final int width = 50;
        final int depth = 20;
        int remaining = Math.max(0, hopperCount);
        int layer = 0;
        while (remaining > 0) {
            int inLayer = Math.min(width * depth, remaining);
            int fullRows = inLayer / width;
            int partial = inLayer % width;
            int y = ORIGIN_Y + layer;
            if (fullRows > 0) commands.add(fill(ORIGIN_X, y, ORIGIN_Z, ORIGIN_X + width - 1, y, ORIGIN_Z + fullRows - 1, "hopper"));
            if (partial > 0) commands.add(fill(ORIGIN_X, y, ORIGIN_Z + fullRows, ORIGIN_X + partial - 1, y, ORIGIN_Z + fullRows, "hopper"));
            remaining -= inLayer;
            layer++;
        }

        int top = ORIGIN_Y + Math.max(1, layer);
        addBlocks(commands, chestCount, top, "chest");
        addBlocks(commands, furnaceCount, top + 1, "furnace");
        for (int i = 0; i < Math.max(0, signCount); i++) {
            int x = ORIGIN_X + i % width;
            int z = ORIGIN_Z + (i / width) % depth;
            int y = top + 2 + i / (width * depth);
            commands.add("/setblock " + x + " " + y + " " + z + " standing_sign 0 replace {Text1:\"HCFOpt\",Text2:\"benchmark\"}");
        }

        for (int i = 0; i < Math.max(0, itemCount); i++) {
            int x = -20 + i % 40;
            int z = -20 + (i / 40) % 40;
            commands.add("/summon Item " + x + " 8 " + z + " {Item:{id:\"minecraft:cobblestone\",Count:64b},PickupDelay:32767s,Age:-32768s}");
        }
        for (int i = 0; i < Math.max(0, mobCount); i++) {
            int x = -30 + (i * 7) % 60;
            int z = -30 + (i * 11) % 60;
            String type = (i & 1) == 0 ? "Zombie" : "Skeleton";
            commands.add("/summon " + type + " " + x + " 4 " + z + " {NoAI:1b,Silent:1b,PersistenceRequired:1b}");
        }
        for (int i = 0; i < Math.max(0, fallingCount); i++) {
            int x = -25 + i % 50;
            int z = -25 + (i / 50) % 50;
            commands.add("/summon FallingSand " + x + " " + (18 + i % 4) + " " + z
                    + " {Block:\"minecraft:sand\",Time:-2147483648,DropItem:0b}");
        }
        for (int i = 0; i < Math.max(0, tntCount); i++) {
            int x = -18 + i % 12 * 3;
            int z = -18 + (i / 12) * 3;
            commands.add("/summon PrimedTnt " + x + " 5 " + z + " {Fuse:32000s}");
        }
        return commands;
    }

    public static String cameraCommand(int tick) {
        final int[][] points = {
                {10, 10, -18, 25, 25}, {25, 12, 10, 90, 18}, {52, 14, 10, 90, 15},
                {25, 20, 45, 180, 30}, {-18, 12, 15, -90, 18}, {10, 8, 10, 45, 12}
        };
        int[] p = points[Math.floorMod(tick / 20, points.length)];
        return "/tp @p " + p[0] + " " + p[1] + " " + p[2] + " " + p[3] + " " + p[4];
    }

    private static void addBlocks(List<String> commands, int count, int y, String block) {
        int width = 50;
        int remaining = Math.max(0, count);
        int row = 0;
        while (remaining > 0) {
            int n = Math.min(width, remaining);
            commands.add(fill(ORIGIN_X, y, ORIGIN_Z + row, ORIGIN_X + n - 1, y, ORIGIN_Z + row, block));
            remaining -= n;
            row++;
        }
    }

    private static String fill(int x1, int y1, int z1, int x2, int y2, int z2, String block) {
        return "/fill " + x1 + " " + y1 + " " + z1 + " " + x2 + " " + y2 + " " + z2 + " " + block;
    }
}

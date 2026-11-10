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

        final int width = 50;
        final int depth = 20;
        int remaining = Math.max(0, hopperCount);
        int layer = 0;
        while (remaining > 0) {
            int inLayer = Math.min(width * depth, remaining);
            int fullRows = inLayer / width;
            int partial = inLayer % width;
            int y = ORIGIN_Y + layer;
            if (fullRows > 0) addFill(commands, ORIGIN_X, y, ORIGIN_Z, ORIGIN_X + width - 1, y, ORIGIN_Z + fullRows - 1, "hopper");
            if (partial > 0) addFill(commands, ORIGIN_X, y, ORIGIN_Z + fullRows, ORIGIN_X + partial - 1, y, ORIGIN_Z + fullRows, "hopper");
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
            commands.add("/summon Item " + x + " 8 " + z + " {Item:{id:4s,Count:64b},Age:-32768s}");
        }
        for (int i = 0; i < Math.max(0, mobCount); i++) {
            int x = -30 + (i * 7) % 60;
            int z = -30 + (i * 11) % 60;
            String type = (i & 1) == 0 ? "Zombie" : "Skeleton";
            commands.add("/summon " + type + " " + x + " 4 " + z + " {PersistenceRequired:1b}");
        }
        for (int i = 0; i < Math.max(0, fallingCount); i++) {
            int x = -25 + i % 50;
            int z = -25 + (i / 50) % 50;
            commands.add("/summon FallingSand " + x + " " + (18 + i % 4) + " " + z
                    + " {TileID:12,Time:1b,DropItem:0b}");
        }
        // tntCount is not used on 1.7.10: EntityTNTPrimed stores its fuse as a byte (at most 127 ticks), so primed TNT would blow the scene up.
        return commands;
    }

    private static final int[][] CAMERA_POINTS = {
            {10, 10, -18, 25, 25}, {25, 12, 10, 90, 18}, {52, 14, 10, 90, 15},
            {25, 20, 45, 180, 30}, {-18, 12, 15, -90, 18}, {10, 8, 10, 45, 12}
    };

    /** /tp on 1.7.10 cannot set a rotation (and ignores the whole command if given one), so the angles are applied on the client: see {@link #cameraAngles}. */
    public static String cameraCommand(int tick) {
        int[] p = CAMERA_POINTS[Math.floorMod(tick / 20, CAMERA_POINTS.length)];
        return "/tp @p " + p[0] + " " + p[1] + " " + p[2];
    }

    /** Yaw and pitch of the camera point used at this tick. */
    public static float[] cameraAngles(int tick) {
        int[] p = CAMERA_POINTS[Math.floorMod(tick / 20, CAMERA_POINTS.length)];
        return new float[] {p[3], p[4]};
    }

    private static void addBlocks(List<String> commands, int count, int y, String block) {
        int width = 50;
        int remaining = Math.max(0, count);
        int row = 0;
        while (remaining > 0) {
            int n = Math.min(width, remaining);
            addFill(commands, ORIGIN_X, y, ORIGIN_Z + row, ORIGIN_X + n - 1, y, ORIGIN_Z + row, block);
            remaining -= n;
            row++;
        }
    }

    /** 1.7.10 has no /fill, so a box becomes one /setblock per block. */
    private static void addFill(List<String> commands, int x1, int y1, int z1, int x2, int y2, int z2, String block) {
        for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
                    commands.add("/setblock " + x + " " + y + " " + z + " " + block);
    }
}

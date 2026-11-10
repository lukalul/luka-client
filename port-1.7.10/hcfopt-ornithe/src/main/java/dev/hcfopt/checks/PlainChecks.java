package dev.hcfopt.checks;

import dev.hcfopt.BenchmarkStats;
import dev.hcfopt.ScenePlanner;
import dev.hcfopt.ThrottlePolicy;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

/** Run by Gradle without Minecraft to check deterministic policy, planning and output math. */
public final class PlainChecks {
    public static void main(String[] args) throws Exception {
        require(ThrottlePolicy.interval(31 * 31, 32) == 1, "near interval");
        require(ThrottlePolicy.interval(33 * 33, 32) == 2, "middle interval");
        require(ThrottlePolicy.interval(1000 * 1000, 32) == 8, "far interval");
        int runs = 0;
        for (int tick = 0; tick < 80; tick++) if (ThrottlePolicy.shouldRun(7, tick, 1000000, 32)) runs++;
        require(runs == 10, "stable 1/8 throttle");

        List<String> scene = ScenePlanner.plan(3000, 300, 100, 200, 40, 300, 200, 100);
        int hopperVolume = 0;
        for (String command : scene) if (command.endsWith(" hopper")) hopperVolume++;
        require(hopperVolume == 3000, "exact hopper count");
        require(scene.equals(ScenePlanner.plan(3000, 300, 100, 200, 40, 300, 200, 100)), "deterministic planner");

        BenchmarkStats stats = new BenchmarkStats();
        BenchmarkStats.Window window = stats.addWindow("check");
        for (int i = 0; i < 100; i++) { window.tick(2_000_000L); window.frame(10_000_000L, 2_000_000L, 400, 3000); }
        require(window.summary().contains("100.00 FPS"), "summary FPS");
        File dir = Files.createTempDirectory("hcfopt-check").toFile();
        File csv = new File(dir, "out.csv"), txt = new File(dir, "out.txt");
        stats.write(csv, txt);
        require(Files.readAllLines(csv.toPath()).size() == 101, "CSV rows");
        require(txt.length() > 0, "summary output");
        System.out.println("HCFOpt plain checks passed: throttle math, scene planner, CSV and summary");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}

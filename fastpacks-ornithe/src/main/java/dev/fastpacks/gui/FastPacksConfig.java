package dev.fastpacks.gui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

final class FastPacksConfig {
    String lastFolder = "";
    boolean transparentBackground;
    boolean previews = true;

    static FastPacksConfig load(File gameDir) {
        File file = file(gameDir);
        try (Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            FastPacksConfig c = new Gson().fromJson(r, FastPacksConfig.class); return c == null ? new FastPacksConfig() : c;
        } catch (Exception ignored) { return new FastPacksConfig(); }
    }

    void save(File gameDir) {
        File file = file(gameDir);
        try { Files.createDirectories(file.getParentFile().toPath());
            try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) { new GsonBuilder().setPrettyPrinting().create().toJson(this, w); }
        } catch (Exception ignored) { }
    }
    private static File file(File gameDir) { return new File(new File(gameDir, "config"), "fastpacks.json"); }
}

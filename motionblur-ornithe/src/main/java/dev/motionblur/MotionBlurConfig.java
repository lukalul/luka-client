package dev.motionblur;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small dependency-free reader/writer for config/motionblur.json. */
public final class MotionBlurConfig {
    private MotionBlurConfig() { }

    public static final class Data {
        public boolean enabled;
        public int level = 3;
        public boolean blurMenus = true;
        public int frameSkip = 1;
        public int toggleKey;
        public int mode = 2;
        public boolean smoothSwings = true;
        public boolean smoothFov = true;
        public boolean smoothHandSway = true;
        public boolean smoothItemUse = true;

        public void sanitize() {
            level = AlphaMath.clamp(level, 0, 10);
            frameSkip = AlphaMath.clamp(frameSkip, 1, 10);
            if (toggleKey < 0) toggleKey = 0;
            mode = AlphaMath.clamp(mode, 0, 2);
        }
    }

    public static Data load(File file) throws Exception {
        Data data = new Data();
        if (!file.isFile()) return data;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        try (FileInputStream in = new FileInputStream(file)) {
            for (int read; (read = in.read(buffer)) >= 0;) bytes.write(buffer, 0, read);
        }
        String json = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        data.enabled = readBoolean(json, "enabled", data.enabled);
        data.level = readInt(json, "level", data.level); // the old 0-100 "strength" key is not read: levels are a different scale
        data.blurMenus = readBoolean(json, "blurMenus", data.blurMenus);
        data.frameSkip = readInt(json, "frameSkip", data.frameSkip);
        data.toggleKey = readInt(json, "toggleKey", data.toggleKey);
        data.mode = readInt(json, "mode", data.mode);
        data.smoothSwings = readBoolean(json, "smoothSwings", data.smoothSwings);
        data.smoothFov = readBoolean(json, "smoothFov", data.smoothFov);
        data.smoothHandSway = readBoolean(json, "smoothHandSway", data.smoothHandSway);
        data.smoothItemUse = readBoolean(json, "smoothItemUse", data.smoothItemUse);
        data.sanitize();
        return data;
    }

    public static void save(File file, Data data) throws Exception {
        data.sanitize();
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new java.io.IOException("Could not create " + parent);
        }
        String json = "{\n"
            + "  \"enabled\": " + data.enabled + ",\n"
            + "  \"level\": " + data.level + ",\n"
            + "  \"blurMenus\": " + data.blurMenus + ",\n"
            + "  \"frameSkip\": " + data.frameSkip + ",\n"
            + "  \"toggleKey\": " + data.toggleKey + ",\n"
            + "  \"mode\": " + data.mode + ",\n"
            + "  \"smoothSwings\": " + data.smoothSwings + ",\n"
            + "  \"smoothFov\": " + data.smoothFov + ",\n"
            + "  \"smoothHandSway\": " + data.smoothHandSway + ",\n"
            + "  \"smoothItemUse\": " + data.smoothItemUse + "\n"
            + "}\n";
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(json.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static boolean readBoolean(String json, String key, boolean fallback) {
        Matcher match = pattern(key, "(true|false)").matcher(json);
        return match.find() ? Boolean.parseBoolean(match.group(1)) : fallback;
    }

    private static int readInt(String json, String key, int fallback) {
        Matcher match = pattern(key, "(-?\\d+)").matcher(json);
        if (!match.find()) return fallback;
        try { return Integer.parseInt(match.group(1)); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private static Pattern pattern(String key, String value) {
        return Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*" + value, Pattern.CASE_INSENSITIVE);
    }
}

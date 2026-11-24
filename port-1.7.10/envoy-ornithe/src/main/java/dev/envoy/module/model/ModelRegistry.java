package dev.envoy.module.model;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.envoy.Envoy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;

/** Boss bar styles from the resource pack's envoy/cem/index.json ("bossbars"). Custom models were removed. */
public final class ModelRegistry {
    private static final String BASE_PATH = "envoy/cem/";
    private static final ResourceLocation INDEX = resource("index.json");
    private static final List<BossBarEntry> BOSS_BARS = new ArrayList<BossBarEntry>();
    private static boolean listenerRegistered;
    private static boolean loadAttempted;

    private ModelRegistry() {
    }

    public static void init() {
        IResourceManager manager = Minecraft.getMinecraft().getResourceManager();
        if (manager == null) {
            Envoy.LOGGER.info("[Model] resource manager not ready at startup, loading on first use");
            return;
        }
        listenerRegistered = true;
        if (manager instanceof IReloadableResourceManager) {
            ((IReloadableResourceManager) manager).registerReloadListener(new ReloadHook());
            Envoy.LOGGER.info("[Model] reload listener registered on {}",
                manager.getClass().getSimpleName());
        } else {
            Envoy.LOGGER.warn("[Model] resource manager is not reloadable ({}), loading once",
                manager.getClass().getSimpleName());
            reload(manager);
        }
    }

    public static void ensureLoaded() {
        if (loadAttempted) {
            return;
        }
        if (!listenerRegistered) {
            init();
        }
        if (!loadAttempted) {
            reload(Minecraft.getMinecraft().getResourceManager());
        }
    }

    public static void reload(IResourceManager manager) {
        BOSS_BARS.clear();
        loadAttempted = true;
        JsonObject index;
        try {
            index = readObject(manager, INDEX);
        } catch (java.io.IOException missing) {
            Envoy.LOGGER.info("[Model] no '{}' found in the loaded resource packs", INDEX);
            return;
        } catch (Exception malformed) {
            Envoy.LOGGER.error("[Model] could not parse '{}'", INDEX, malformed);
            return;
        }

        JsonArray array = index.getAsJsonArray("bossbars");
        if (array != null) {
            for (JsonElement value : array) {
                if (!value.isJsonObject()) continue;
                BossBarEntry entry = bossBarEntry(value.getAsJsonObject());
                if (entry != null) BOSS_BARS.add(entry);
            }
        }
        Envoy.LOGGER.info("[Model] loaded {} boss bar override(s)", BOSS_BARS.size());
    }

    private static JsonObject readObject(IResourceManager manager, ResourceLocation location) throws Exception {
        IResource resource = manager.getResource(location);
        return new JsonParser().parse(new InputStreamReader(resource.getInputStream(), "UTF-8"))
            .getAsJsonObject();
    }

    private static BossBarEntry bossBarEntry(JsonObject source) {
        String name = requiredString(source, "name");
        if (name == null) return null;
        return new BossBarEntry(NameMatcher.compile(name), string(source, "texture"),
            color(source, "color", 0xFFFFFFFF), color(source, "backgroundColor", 0xFF202020),
            color(source, "textColor", 0xFFFFFFFF), integer(source, "notches", 0),
            flag(source, "hideName"), integer(source, "y", 12));
    }

    private static String requiredString(JsonObject source, String key) {
        JsonElement value = source.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    private static String string(JsonObject source, String key) {
        JsonElement value = source.get(key);
        return value == null ? null : value.getAsString();
    }

    private static int integer(JsonObject source, String key, int fallback) {
        JsonElement value = source.get(key);
        return value == null ? fallback : value.getAsInt();
    }

    private static boolean flag(JsonObject source, String key) {
        JsonElement value = source.get(key);
        return value != null && value.getAsBoolean();
    }

    private static int color(JsonObject source, String key, int fallback) {
        JsonElement element = source.get(key);
        if (element == null || !element.isJsonPrimitive()) return fallback;
        String text = element.getAsString().trim();
        if (text.startsWith("#")) text = text.substring(1);
        try {
            long value = Long.parseLong(text, 16);
            return text.length() <= 6 ? (int) (0xFF000000L | value) : (int) value;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public static boolean hasBossBars() { return !BOSS_BARS.isEmpty(); }

    public static BossBarEntry findForBossBar(String bossName) {
        if (bossName == null) return null;
        String plain = EnumChatFormatting.getTextWithoutFormattingCodes(bossName);
        for (BossBarEntry entry : BOSS_BARS) if (entry.name.matches(plain)) return entry;
        return null;
    }

    public static ResourceLocation resource(String path) {
        return new ResourceLocation("minecraft", BASE_PATH + path);
    }

    public static final class BossBarEntry {
        public final NameMatcher name;
        public final String texturePath;
        public final int color, backgroundColor, textColor, notches;
        public final boolean hideName;
        public final int y;
        BossBarEntry(NameMatcher name, String texturePath, int color, int backgroundColor,
                     int textColor, int notches, boolean hideName, int y) {
            this.name = name; this.texturePath = texturePath; this.color = color;
            this.backgroundColor = backgroundColor; this.textColor = textColor;
            this.notches = notches; this.hideName = hideName; this.y = y;
        }
    }

    public static final class NameMatcher {
        private final Pattern pattern;
        private final String literal;
        private final boolean ignoreCase;
        private NameMatcher(Pattern pattern, String literal, boolean ignoreCase) {
            this.pattern = pattern; this.literal = literal; this.ignoreCase = ignoreCase;
        }
        public static NameMatcher compile(String value) {
            if (value.startsWith("regex:")) return regex(value.substring(6), false);
            if (value.startsWith("iregex:")) return regex(value.substring(7), true);
            if (value.startsWith("pattern:")) return regex(wildcard(value.substring(8)), false);
            if (value.startsWith("ipattern:")) return regex(wildcard(value.substring(9)), true);
            return new NameMatcher(null, value, false);
        }
        private static NameMatcher regex(String expression, boolean insensitive) {
            return new NameMatcher(Pattern.compile(expression,
                insensitive ? Pattern.CASE_INSENSITIVE : 0), null, insensitive);
        }
        private static String wildcard(String value) {
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < value.length(); i++) {
                char character = value.charAt(i);
                result.append(character == '*' ? ".*" : character == '?' ? "."
                    : Pattern.quote(String.valueOf(character)));
            }
            return result.toString();
        }
        public boolean matches(String value) {
            if (pattern != null) return pattern.matcher(value).matches();
            return ignoreCase ? literal.equalsIgnoreCase(value) : literal.equals(value);
        }
        @Override public String toString() { return pattern == null ? literal : pattern.pattern(); }
    }

    private static final class ReloadHook implements IResourceManagerReloadListener {
        @Override public void onResourceManagerReload(IResourceManager manager) { reload(manager); }
    }
}

package dev.envoy.module.model;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;

public final class ModelRegistry {
    private static final String BASE_PATH = "envoy/cem/";
    private static final ResourceLocation INDEX = resource("index.json");
    private static final RegistryData DATA = new RegistryData();
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
        DATA.reset();
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

        readObjects(index, "entities", new ObjectReader() {
            @Override public void accept(JsonObject object) {
                Entry entry = entityEntry(object);
                if (entry != null) DATA.entities.add(entry);
            }
        });
        readObjects(index, "items", new ObjectReader() {
            @Override public void accept(JsonObject object) {
                ItemEntry entry = itemEntry(object);
                if (entry != null) DATA.items.add(entry);
            }
        });
        readObjects(index, "bossbars", new ObjectReader() {
            @Override public void accept(JsonObject object) {
                BossBarEntry entry = bossBarEntry(object);
                if (entry != null) DATA.bossBars.add(entry);
            }
        });
        logLoadedIndex();
    }

    private static JsonObject readObject(IResourceManager manager, ResourceLocation location) throws Exception {
        IResource resource = manager.getResource(location);
        return new JsonParser().parse(new InputStreamReader(resource.getInputStream(), "UTF-8"))
            .getAsJsonObject();
    }

    private static void readObjects(JsonObject root, String key, ObjectReader reader) {
        JsonArray array = root.getAsJsonArray(key);
        if (array == null) return;
        for (JsonElement value : array) {
            if (value.isJsonObject()) reader.accept(value.getAsJsonObject());
        }
    }

    private static Entry entityEntry(JsonObject source) {
        String model = requiredString(source, "model");
        if (model == null) return null;
        return new Entry(model, string(source, "entity"), matcher(source, "name"),
            number(source, "scale", 1.0F), flag(source, "ignoreCull"),
            number(source, "renderDistance", 0.0F), string(source, "animation"),
            string(source, "animationName"));
    }

    private static ItemEntry itemEntry(JsonObject source) {
        String model = requiredString(source, "model");
        if (model == null) return null;
        return new ItemEntry(model, string(source, "item"), matcher(source, "name"),
            number(source, "scale", 1.0F), vector(source.get("offset")), string(source, "attach"),
            string(source, "animation"), string(source, "animationName"));
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

    private static NameMatcher matcher(JsonObject source, String key) {
        String value = string(source, key);
        return value == null ? null : NameMatcher.compile(value);
    }

    private static float number(JsonObject source, String key, float fallback) {
        JsonElement value = source.get(key);
        return value == null ? fallback : value.getAsFloat();
    }

    private static int integer(JsonObject source, String key, int fallback) {
        JsonElement value = source.get(key);
        return value == null ? fallback : value.getAsInt();
    }

    private static boolean flag(JsonObject source, String key) {
        JsonElement value = source.get(key);
        return value != null && value.getAsBoolean();
    }

    private static float[] vector(JsonElement value) {
        float[] result = new float[3];
        if (value != null && value.isJsonArray() && value.getAsJsonArray().size() >= 3) {
            for (int axis = 0; axis < 3; axis++) result[axis] = value.getAsJsonArray().get(axis).getAsFloat();
        }
        return result;
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

    private static void logLoadedIndex() {
        Envoy.LOGGER.info("[Model] loaded {} entity, {} item, {} boss bar override(s)",
            DATA.entities.size(), DATA.items.size(), DATA.bossBars.size());
        for (Entry entry : DATA.entities) {
            Envoy.LOGGER.info("[Model]   entity model={} entity={} name={}",
                entry.modelPath, entry.entityId, entry.name);
        }
        for (ItemEntry entry : DATA.items) {
            Envoy.LOGGER.info("[Model]   item model={} item={} name={} attach={}",
                entry.modelPath, entry.itemId, entry.name, entry.attach);
        }
    }

    public static boolean hasBossBars() { return !DATA.bossBars.isEmpty(); }

    public static BossBarEntry findForBossBar(String bossName) {
        if (bossName == null) return null;
        String plain = EnumChatFormatting.getTextWithoutFormattingCodes(bossName);
        for (BossBarEntry entry : DATA.bossBars) if (entry.name.matches(plain)) return entry;
        return null;
    }

    public static boolean isEmpty() { return DATA.entities.isEmpty(); }
    public static boolean hasItems() { return !DATA.items.isEmpty(); }

    public static Entry findForEntity(Entity entity) {
        if (DATA.entities.isEmpty()) return null;
        String type = EntityList.getEntityString(entity);
        String name = entity instanceof net.minecraft.entity.EntityLiving && ((net.minecraft.entity.EntityLiving) entity).hasCustomNameTag()
            ? EnumChatFormatting.getTextWithoutFormattingCodes(((net.minecraft.entity.EntityLiving) entity).getCustomNameTag()) : null;
        for (Entry entry : DATA.entities) {
            boolean typeMatches = entry.entityId == null
                || type != null && type.equalsIgnoreCase(stripDomain(entry.entityId));
            boolean nameMatches = entry.name == null || name != null && entry.name.matches(name);
            if (typeMatches && nameMatches) return entry;
        }
        return null;
    }

    public static ItemEntry findForItem(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return null;
        Object registered = Item.itemRegistry.getNameForObject(stack.getItem());
        String itemId = registered == null ? null : registered.toString();
        String name = stack.hasDisplayName()
            ? EnumChatFormatting.getTextWithoutFormattingCodes(stack.getDisplayName()) : null;
        for (ItemEntry entry : DATA.items) {
            boolean itemMatches = entry.itemId == null
                || itemId != null && idsMatch(itemId, entry.itemId);
            boolean nameMatches = entry.name == null || name != null && entry.name.matches(name);
            if (itemMatches && nameMatches) return entry;
        }
        return null;
    }

    private static boolean idsMatch(String actual, String expected) {
        return actual.equalsIgnoreCase(expected)
            || stripDomain(actual).equalsIgnoreCase(stripDomain(expected));
    }

    public static JemModel getModel(Entry entry) { return getModel(entry.modelPath); }

    public static JemModel getModel(String modelPath) {
        JemModel cached = DATA.models.get(modelPath);
        if (cached != null) return cached;
        try {
            JemModel loaded = JemParser.parse(readObject(Minecraft.getMinecraft().getResourceManager(),
                resource(modelPath)), BASE_PATH);
            DATA.models.put(modelPath, loaded);
            return loaded;
        } catch (Exception failure) {
            Envoy.LOGGER.error("[Model] could not load {}", modelPath, failure);
            return null;
        }
    }

    public static JemAnimation getAnimation(Entry entry) {
        return getAnimation(entry.animationPath, entry.animationName);
    }

    public static JemAnimation getAnimation(String animationPath, String wantedName) {
        if (animationPath == null) return null;
        String key = animationPath + "#" + wantedName;
        if (DATA.animations.containsKey(key)) return DATA.animations.get(key);
        JemAnimation loaded = null;
        try {
            IResource resource = Minecraft.getMinecraft().getResourceManager().getResource(resource(animationPath));
            loaded = AnimationLoader.load(resource.getInputStream(), wantedName);
            if (loaded == null) {
                Envoy.LOGGER.warn("[Model] animation '{}' not found in {}", wantedName, animationPath);
            } else {
                Envoy.LOGGER.info("[Model] loaded animation '{}' ({}s, loop={}, {} bones) from {}",
                    loaded.name(), loaded.length(), loaded.loop(), loaded.boneNames().size(), animationPath);
            }
        } catch (Exception failure) {
            Envoy.LOGGER.error("[Model] could not load animation {}", animationPath, failure);
        }
        DATA.animations.put(key, loaded);
        return loaded;
    }

    public static ResourceLocation resource(String path) {
        return new ResourceLocation("minecraft", BASE_PATH + path);
    }

    private static String stripDomain(String id) {
        int separator = id.indexOf(':');
        return separator < 0 ? id : id.substring(separator + 1);
    }

    public static final class Entry {
        public final String modelPath, entityId;
        public final NameMatcher name;
        public final float scale;
        public final boolean ignoreCull;
        public final float renderDistance;
        public final String animationPath, animationName;
        Entry(String modelPath, String entityId, NameMatcher name, float scale, boolean ignoreCull,
              float renderDistance, String animationPath, String animationName) {
            this.modelPath = modelPath; this.entityId = entityId; this.name = name; this.scale = scale;
            this.ignoreCull = ignoreCull; this.renderDistance = renderDistance;
            this.animationPath = animationPath; this.animationName = animationName;
        }
    }

    public static final class ItemEntry {
        public final String modelPath, itemId;
        public final NameMatcher name;
        public final float scale;
        public final float[] offset;
        public final String attach, animationPath, animationName;
        ItemEntry(String modelPath, String itemId, NameMatcher name, float scale, float[] offset,
                  String attach, String animationPath, String animationName) {
            this.modelPath = modelPath; this.itemId = itemId; this.name = name; this.scale = scale;
            this.offset = offset; this.attach = attach; this.animationPath = animationPath;
            this.animationName = animationName;
        }
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

    private interface ObjectReader { void accept(JsonObject object); }
    private static final class ReloadHook implements IResourceManagerReloadListener {
        @Override public void onResourceManagerReload(IResourceManager manager) { reload(manager); }
    }
    private static final class RegistryData {
        private final List<Entry> entities = new ArrayList<>();
        private final List<ItemEntry> items = new ArrayList<>();
        private final List<BossBarEntry> bossBars = new ArrayList<>();
        private final Map<String, JemModel> models = new HashMap<>();
        private final Map<String, JemAnimation> animations = new HashMap<>();
        private void reset() {
            for (JemModel model : models.values()) model.dispose();
            entities.clear(); items.clear(); bossBars.clear(); models.clear(); animations.clear();
        }
    }
}

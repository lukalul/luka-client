package dev.envoy.module.icon;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

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
import net.minecraft.util.ResourceLocation;

public final class IconRegistry {
    private static final String BASE_PATH = "envoy/";
    private static final ResourceLocation CATALOG = new ResourceLocation("minecraft", BASE_PATH + "icons.json");
    private static volatile Map<String, Icon> catalog = Collections.emptyMap();
    private static boolean listenerInstalled;

    public static final class Icon {
        public final ResourceLocation texture;
        public final float width;
        public final float height;
        public final float ascent;

        Icon(ResourceLocation texture, float width, float height, float ascent) {
            this.texture = texture;
            this.width = width;
            this.height = height;
            this.ascent = ascent;
        }
    }

    private IconRegistry() { }

    public static synchronized void init() {
        if (listenerInstalled) return;
        IResourceManager manager = Minecraft.getMinecraft().getResourceManager();
        if (manager == null) return;
        listenerInstalled = true;
        if (manager instanceof IReloadableResourceManager) {
            ((IReloadableResourceManager) manager).registerReloadListener(new CatalogReloader());
        } else {
            reload(manager);
        }
    }

    public static void reload(IResourceManager resourceManager) {
        Map<String, Icon> loaded;
        try {
            loaded = readCatalog(resourceManager);
        } catch (java.io.IOException missing) {
            loaded = Collections.emptyMap();
        } catch (RuntimeException malformed) {
            Envoy.LOGGER.error("[Icon] could not parse '{}'", CATALOG, malformed);
            loaded = Collections.emptyMap();
        }
        catalog = loaded;
        Envoy.LOGGER.info("[Icon] loaded {} chat icon(s)", loaded.size());
    }

    public static boolean isEmpty() {
        if (!listenerInstalled) init();
        return catalog.isEmpty();
    }

    public static Icon get(String name) {
        return catalog.get(name);
    }

    private static Map<String, Icon> readCatalog(IResourceManager manager) throws java.io.IOException {
        IResource resource = manager.getResource(CATALOG);
        try (InputStream stream = resource.getInputStream(); Reader reader = new InputStreamReader(stream, "UTF-8")) {
            JsonObject document = new JsonParser().parse(reader).getAsJsonObject();
            JsonArray entries = document.getAsJsonArray("icons");
            if (entries == null) return Collections.emptyMap();
            Map<String, Icon> result = new LinkedHashMap<>();
            for (JsonElement entry : entries) {
                IconRecord record = IconRecord.from(entry);
                if (record != null) result.put(record.name, record.toIcon());
            }
            return Collections.unmodifiableMap(result);
        }
    }

    private static ResourceLocation textureLocation(String raw) {
        String file = raw.endsWith(".png") ? raw : raw + ".png";
        return file.indexOf(':') >= 0 ? new ResourceLocation(file)
            : new ResourceLocation("minecraft", BASE_PATH + file);
    }

    private static final class CatalogReloader implements IResourceManagerReloadListener {
        @Override public void onResourceManagerReload(IResourceManager manager) { reload(manager); }
    }

    private static final class IconRecord {
        final String name, texture;
        final float width, height, ascent;

        IconRecord(String name, String texture, float width, float height, float ascent) {
            this.name = name; this.texture = texture; this.width = width; this.height = height; this.ascent = ascent;
        }

        static IconRecord from(JsonElement value) {
            if (!value.isJsonObject()) return null;
            JsonObject object = value.getAsJsonObject();
            JsonElement name = object.get("name"), texture = object.get("texture");
            if (name == null || texture == null) return null;
            return new IconRecord(name.getAsString(), texture.getAsString(), decimal(object, "width", 8.0F),
                decimal(object, "height", 8.0F), decimal(object, "ascent", 0.0F));
        }

        Icon toIcon() { return new Icon(textureLocation(texture), width, height, ascent); }

        private static float decimal(JsonObject object, String key, float fallback) {
            JsonElement value = object.get(key);
            return value != null && value.isJsonPrimitive() ? value.getAsFloat() : fallback;
        }
    }
}

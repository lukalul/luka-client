package dev.envoy.module.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.ResourceLocation;

public final class JemParser {
    private static final String[][] UV_NAMES = {
        {"uvDown"}, {"uvUp"}, {"uvNorth", "uvFront"},
        {"uvSouth", "uvBack"}, {"uvWest", "uvLeft"}, {"uvEast", "uvRight"}
    };

    private JemParser() {
    }

    public static JemModel parse(JsonObject root, String basePath) {
        ParseContext context = new ParseContext(basePath);
        JemModel result = new JemModel();
        result.texture = context.texture(text(root, "texture", null));
        float[] atlas = vector(root.get("textureSize"), 2);
        if (atlas != null) {
            result.textureWidth = atlas[0];
            result.textureHeight = atlas[1];
        }
        result.shadowSize = decimal(root, "shadowSize", 1.0F);

        JsonArray entries = array(root, "models");
        if (entries != null) {
            for (JsonElement entry : entries) {
                if (!entry.isJsonObject()) continue;
                JsonObject definition = entry.getAsJsonObject();
                JemPart part = context.part(definition, result.texture,
                    result.textureWidth, result.textureHeight);
                part.attachTo = text(definition, "part", null);
                part.attach = flag(definition, "attach", false);
                result.addPart(part);
            }
        }
        return result;
    }

    private static final class ParseContext {
        private final String textureBase;

        private ParseContext(String textureBase) {
            this.textureBase = textureBase;
        }

        private JemPart part(JsonObject source, ResourceLocation inheritedTexture,
                             float inheritedWidth, float inheritedHeight) {
            JemPart part = new JemPart();
            part.id = text(source, "id", "");
            part.scale = decimal(source, "scale", 1.0F);
            part.visible = flag(source, "visible", true);

            ResourceLocation ownTexture = texture(text(source, "texture", null));
            part.texture = ownTexture;
            float width = inheritedWidth;
            float height = inheritedHeight;
            float[] atlas = vector(source.get("textureSize"), 2);
            if (atlas != null) {
                width = atlas[0];
                height = atlas[1];
            }

            AxisFlags inversion = new AxisFlags(text(source, "invertAxis", ""));
            float[] translation = vectorOrZero(source.get("translate"));
            part.translateX = inversion.x(translation[0]);
            part.translateY = inversion.y(translation[1]);
            part.translateZ = inversion.z(translation[2]);
            float[] rotation = vectorOrZero(source.get("rotate"));
            part.rotateX = inversion.x(radians(rotation[0]));
            part.rotateY = inversion.y(radians(rotation[1]));
            part.rotateZ = inversion.z(radians(rotation[2]));

            String mirror = text(source, "mirrorTexture", "").toLowerCase();
            addBoxes(array(source, "boxes"), part, inversion, width, height,
                mirror.indexOf('u') >= 0, mirror.indexOf('v') >= 0);
            addBoxes(array(source, "sprites"), part, inversion, width, height,
                mirror.indexOf('u') >= 0, mirror.indexOf('v') >= 0);

            ResourceLocation childTexture = ownTexture == null ? inheritedTexture : ownTexture;
            JsonElement single = source.get("submodel");
            if (single != null && single.isJsonObject()) {
                part.addChild(part(single.getAsJsonObject(), childTexture, width, height));
            }
            JsonArray children = array(source, "submodels");
            if (children != null) {
                for (JsonElement child : children) {
                    if (child.isJsonObject()) part.addChild(part(child.getAsJsonObject(), childTexture, width, height));
                }
            }
            return part;
        }

        private void addBoxes(JsonArray definitions, JemPart parent, AxisFlags inversion,
                              float width, float height, boolean flipU, boolean flipV) {
            if (definitions == null) return;
            for (JsonElement element : definitions) {
                if (!element.isJsonObject()) continue;
                JsonObject definition = element.getAsJsonObject();
                float[] bounds = vector(definition.get("coordinates"), 6);
                if (bounds == null) continue;
                inversion.adjustBounds(bounds);
                float[] rawOffset = vector(definition.get("textureOffset"), 2);
                int[] offset = rawOffset == null ? null : new int[] {(int) rawOffset[0], (int) rawOffset[1]};
                int[][] faces = faceUvs(definition);
                if (offset != null || faces != null) {
                    parent.addBox(new JemBox(bounds, decimal(definition, "sizeAdd", 0.0F),
                        offset, faces, width, height, flipU, flipV));
                }
            }
        }

        private ResourceLocation texture(String name) {
            if (name == null || name.isEmpty()) return null;
            String path = name.endsWith(".png") ? name : name + ".png";
            if (path.indexOf(':') >= 0 || path.indexOf('/') >= 0) {
                return new ResourceLocation(path.charAt(0) == '/' ? path.substring(1) : path);
            }
            return new ResourceLocation(this.textureBase + path);
        }
    }

    private static final class AxisFlags {
        private final boolean invertX;
        private final boolean invertY;
        private final boolean invertZ;

        private AxisFlags(String axes) {
            String normalized = axes.toLowerCase();
            this.invertX = normalized.indexOf('x') >= 0;
            this.invertY = normalized.indexOf('y') >= 0;
            this.invertZ = normalized.indexOf('z') >= 0;
        }

        private float x(float value) { return this.invertX ? -value : value; }
        private float y(float value) { return this.invertY ? -value : value; }
        private float z(float value) { return this.invertZ ? -value : value; }

        private void adjustBounds(float[] bounds) {
            if (this.invertX) bounds[0] = -bounds[0] - bounds[3];
            if (this.invertY) bounds[1] = -bounds[1] - bounds[4];
            if (this.invertZ) bounds[2] = -bounds[2] - bounds[5];
        }
    }

    private static int[][] faceUvs(JsonObject box) {
        int[][] result = null;
        for (int face = 0; face < UV_NAMES.length; face++) {
            float[] coordinates = firstVector(box, UV_NAMES[face], 4);
            if (coordinates == null) continue;
            if (result == null) result = new int[6][];
            result[face] = new int[] {(int) coordinates[0], (int) coordinates[1],
                (int) coordinates[2], (int) coordinates[3]};
        }
        return result;
    }

    private static float[] firstVector(JsonObject source, String[] keys, int size) {
        for (String key : keys) {
            float[] candidate = vector(source.get(key), size);
            if (candidate != null) return candidate;
        }
        return null;
    }

    private static JsonArray array(JsonObject source, String key) {
        JsonElement value = source.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : null;
    }

    private static String text(JsonObject source, String key, String fallback) {
        JsonElement value = source.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
    }

    private static float decimal(JsonObject source, String key, float fallback) {
        JsonElement value = source.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsFloat() : fallback;
    }

    private static boolean flag(JsonObject source, String key, boolean fallback) {
        JsonElement value = source.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsBoolean() : fallback;
    }

    private static float[] vectorOrZero(JsonElement value) {
        float[] parsed = vector(value, 3);
        return parsed == null ? new float[3] : parsed;
    }

    private static float[] vector(JsonElement value, int size) {
        if (value == null || !value.isJsonArray() || value.getAsJsonArray().size() < size) return null;
        float[] result = new float[size];
        for (int index = 0; index < size; index++) result[index] = value.getAsJsonArray().get(index).getAsFloat();
        return result;
    }

    private static float radians(float degrees) {
        return (float) Math.toRadians(degrees);
    }
}

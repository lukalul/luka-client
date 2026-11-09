package dev.envoy.module.model;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class AnimationLoader {
    private AnimationLoader() {
    }

    public static JemAnimation load(InputStream stream, String wanted) throws Exception {
        JsonObject document = new JsonParser().parse(new InputStreamReader(stream, "UTF-8")).getAsJsonObject();
        Map.Entry<String, JsonElement> selection = choose(document.getAsJsonObject("animations"), wanted);
        return selection == null ? null : build(selection);
    }

    private static Map.Entry<String, JsonElement> choose(JsonObject library, String wanted) {
        if (library == null || library.entrySet().isEmpty()) {
            return null;
        }
        if (wanted == null || wanted.isEmpty()) {
            return library.entrySet().iterator().next();
        }
        for (Map.Entry<String, JsonElement> candidate : library.entrySet()) {
            if (candidate.getKey().equals(wanted) || candidate.getKey().endsWith("." + wanted)) {
                return candidate;
            }
        }
        return null;
    }

    private static JemAnimation build(Map.Entry<String, JsonElement> source) {
        JsonObject definition = source.getValue().getAsJsonObject();
        double duration = definition.has("animation_length")
            ? definition.get("animation_length").getAsDouble() : 0.0D;
        boolean repeats = definition.has("loop") && booleanValue(definition.get("loop"));
        Map<String, JemAnimation.Timeline> timelines = new HashMap<>();
        JsonObject bones = definition.getAsJsonObject("bones");
        if (bones != null) {
            for (Map.Entry<String, JsonElement> bone : bones.entrySet()) {
                JemAnimation.Timeline timeline = decodeTimeline(bone.getValue());
                if (timeline != null) {
                    timelines.put(bone.getKey(), timeline);
                }
            }
        }
        return new JemAnimation(source.getKey(), duration, repeats, timelines);
    }

    private static JemAnimation.Timeline decodeTimeline(JsonElement source) {
        if (!source.isJsonObject()) {
            return null;
        }
        JsonObject channels = source.getAsJsonObject();
        JemAnimation.Timeline result = new JemAnimation.Timeline();
        decodeChannel(channels.get("rotation"), result.rotation);
        decodeChannel(channels.get("position"), result.position);
        decodeChannel(channels.get("scale"), result.scale);
        decodeChannel(channels.get("visible"), result.visible);
        return result.rotation.isEmpty() && result.position.isEmpty()
            && result.scale.isEmpty() && result.visible.isEmpty() ? null : result;
    }

    private static void decodeChannel(JsonElement source, TreeMap<Double, JemAnimation.Keyframe> output) {
        if (source == null) {
            return;
        }
        JsonObject frames = source.isJsonObject() ? source.getAsJsonObject() : null;
        if (frames != null && timestampMap(frames)) {
            for (Map.Entry<String, JsonElement> frame : frames.entrySet()) {
                addFrame(output, Double.parseDouble(frame.getKey()), frame.getValue());
            }
        } else {
            addFrame(output, 0.0D, source);
        }
    }

    private static boolean timestampMap(JsonObject object) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            try {
                Double.parseDouble(entry.getKey());
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return true;
    }

    private static void addFrame(TreeMap<Double, JemAnimation.Keyframe> output,
                                 double timestamp, JsonElement source) {
        double[] vector = decodeVector(source);
        if (vector == null) {
            return;
        }
        String easing = null;
        boolean spline = false;
        if (source.isJsonObject()) {
            JsonObject properties = source.getAsJsonObject();
            easing = properties.has("easing") ? properties.get("easing").getAsString() : null;
            spline = properties.has("lerp_mode")
                && "catmullrom".equals(properties.get("lerp_mode").getAsString());
        }
        output.put(timestamp, new JemAnimation.Keyframe(vector, easing, spline));
    }

    private static double[] decodeVector(JsonElement source) {
        if (source == null || source.isJsonNull()) {
            return null;
        }
        if (source.isJsonPrimitive()) {
            try {
                return filled(source.getAsDouble());
            } catch (Exception ignored) {
                return filled(booleanValue(source) ? 1.0D : 0.0D);
            }
        }
        if (source.isJsonArray()) {
            JsonArray values = source.getAsJsonArray();
            if (values.size() == 0) {
                return null;
            }
            if (values.get(0).isJsonObject()) {
                return decodeVector(values.get(0));
            }
            if (values.size() < 3) {
                return filled(values.get(0).getAsDouble());
            }
            return new double[] {values.get(0).getAsDouble(), values.get(1).getAsDouble(),
                values.get(2).getAsDouble()};
        }
        JsonObject wrapper = source.getAsJsonObject();
        for (String alternative : new String[] {"vector", "post", "pre"}) {
            if (wrapper.has(alternative)) {
                return decodeVector(wrapper.get(alternative));
            }
        }
        return null;
    }

    private static double[] filled(double value) {
        return new double[] {value, value, value};
    }

    private static boolean booleanValue(JsonElement value) {
        try {
            return value.getAsBoolean();
        } catch (Exception ignored) {
            return "true".equals(value.getAsString());
        }
    }
}

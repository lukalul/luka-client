package dev.envoy.module.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.envoy.Envoy;

public final class ModelAnimator {
    private static final Set<String> LOGGED_BINDINGS = new HashSet<>();

    private ModelAnimator() {
    }

    public static void apply(JemModel model, JemAnimation animation, double seconds) {
        BindingResult result = new BindingResult();
        double playhead = animation.time(seconds);
        for (String boneName : animation.boneNames()) {
            JemPart target = model.resolve(boneName);
            if (target == null) {
                result.missing.add(boneName);
            } else {
                result.bound++;
                updatePart(target, animation, boneName, playhead);
            }
        }
        logBindingOnce(model, animation, result);
    }

    private static void updatePart(JemPart target, JemAnimation animation,
                                   String boneName, double playhead) {
        double[] rotation = animation.sampleRotation(boneName, playhead);
        if (rotation != null) {
            target.animRotateX = radians(-rotation[0]);
            target.animRotateY = radians(-rotation[1]);
            target.animRotateZ = radians(rotation[2]);
        }
        double[] translation = animation.samplePosition(boneName, playhead);
        if (translation != null) {
            target.animTranslateX = (float) -translation[0];
            target.animTranslateY = (float) -translation[1];
            target.animTranslateZ = (float) translation[2];
        }
        double[] scale = animation.sampleScale(boneName, playhead);
        if (scale != null) {
            target.animScaleX = (float) scale[0];
            target.animScaleY = (float) scale[1];
            target.animScaleZ = (float) scale[2];
        }
        Boolean visible = animation.sampleVisible(boneName, playhead);
        if (visible != null) {
            target.hidden = !visible.booleanValue();
        }
    }

    private static float radians(double degrees) {
        return (float) Math.toRadians(degrees);
    }

    private static void logBindingOnce(JemModel model, JemAnimation animation, BindingResult result) {
        String identity = System.identityHashCode(model) + "/" + animation.name();
        if (!LOGGED_BINDINGS.add(identity)) {
            return;
        }
        int total = result.bound + result.missing.size();
        if (result.bound == 0) {
            Envoy.LOGGER.warn("[Model] animation '{}' matched 0 of {} bone(s): no part id matches",
                animation.name(), total);
        } else {
            Envoy.LOGGER.info("[Model] animation '{}' bound to {}/{} bone(s)",
                animation.name(), result.bound, total);
        }
        if (!result.missing.isEmpty()) {
            Object sample = result.missing.size() > 8
                ? result.missing.subList(0, 8) + " ..." : result.missing;
            Envoy.LOGGER.warn("[Model] unmatched animation bone(s): {}", sample);
        }
    }

    private static final class BindingResult {
        private int bound;
        private final List<String> missing = new ArrayList<>();
    }
}

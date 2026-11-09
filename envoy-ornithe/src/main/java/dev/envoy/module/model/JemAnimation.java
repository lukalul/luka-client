package dev.envoy.module.model;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class JemAnimation {
    public static final class Keyframe {
        public final double[] value;
        public final String easing;
        public final boolean catmullrom;

        public Keyframe(double[] value, String easing, boolean catmullrom) {
            this.value = value;
            this.easing = easing;
            this.catmullrom = catmullrom;
        }
    }

    public static final class Timeline {
        public final TreeMap<Double, Keyframe> rotation = new TreeMap<>();
        public final TreeMap<Double, Keyframe> position = new TreeMap<>();
        public final TreeMap<Double, Keyframe> scale = new TreeMap<>();
        public final TreeMap<Double, Keyframe> visible = new TreeMap<>();
    }

    private final String name;
    private final double length;
    private final boolean loop;
    private final Map<String, Timeline> bones;

    public JemAnimation(String name, double length, boolean loop, Map<String, Timeline> bones) {
        this.name = name;
        this.length = length;
        this.loop = loop;
        this.bones = bones;
    }

    public String name() { return this.name; }
    public double length() { return this.length; }
    public boolean loop() { return this.loop; }

    public Set<String> boneNames() {
        return Collections.unmodifiableSet(this.bones.keySet());
    }

    public double time(double seconds) {
        if (this.length <= 0.0D) return 0.0D;
        return this.loop ? seconds % this.length : Math.min(seconds, this.length);
    }

    public double[] sampleRotation(String bone, double time) {
        Timeline track = this.bones.get(bone);
        return track == null ? null : interpolate(track.rotation, time);
    }

    public double[] samplePosition(String bone, double time) {
        Timeline track = this.bones.get(bone);
        return track == null ? null : interpolate(track.position, time);
    }

    public double[] sampleScale(String bone, double time) {
        Timeline track = this.bones.get(bone);
        return track == null ? null : interpolate(track.scale, time);
    }

    public Boolean sampleVisible(String bone, double time) {
        Timeline track = this.bones.get(bone);
        if (track == null || track.visible.isEmpty()) return null;
        Map.Entry<Double, Keyframe> selected = track.visible.floorEntry(time);
        if (selected == null) selected = track.visible.firstEntry();
        return selected.getValue().value[0] >= 0.5D;
    }

    private static double[] interpolate(TreeMap<Double, Keyframe> keys, double moment) {
        if (keys.isEmpty()) return null;
        Map.Entry<Double, Keyframe> left = keys.floorEntry(moment);
        Map.Entry<Double, Keyframe> right = keys.ceilingEntry(moment);
        if (left == null || right == null) return (left == null ? right : left).getValue().value.clone();
        double span = right.getKey() - left.getKey();
        if (span < 1.0E-9D) return right.getValue().value.clone();

        double progress = (moment - left.getKey()) / span;
        if (right.getValue().catmullrom) {
            return spline(neighbor(keys, keys.lowerKey(left.getKey()), left.getValue()),
                left.getValue().value, right.getValue().value,
                neighbor(keys, keys.higherKey(right.getKey()), right.getValue()), progress);
        }
        return blend(left.getValue().value, right.getValue().value,
            easing(right.getValue().easing, progress));
    }

    private static double[] neighbor(TreeMap<Double, Keyframe> keys, Double key, Keyframe fallback) {
        return key == null ? fallback.value : keys.get(key).value;
    }

    private static double[] blend(double[] start, double[] end, double amount) {
        double[] result = new double[3];
        for (int axis = 0; axis < result.length; axis++) {
            result[axis] = start[axis] + (end[axis] - start[axis]) * amount;
        }
        return result;
    }

    private static double easing(String name, double x) {
        if (name == null || "linear".equals(name)) return x;
        if ("step".equals(name)) return 0.0D;
        if ("easeInSine".equals(name)) return 1.0D - Math.cos(x * Math.PI / 2.0D);
        if ("easeOutSine".equals(name)) return Math.sin(x * Math.PI / 2.0D);
        if ("easeInOutSine".equals(name)) return (1.0D - Math.cos(Math.PI * x)) / 2.0D;

        double overshoot = 1.70158D;
        if ("easeInBack".equals(name)) return x * x * ((overshoot + 1.0D) * x - overshoot);
        if ("easeOutBack".equals(name)) {
            double shifted = x - 1.0D;
            return 1.0D + shifted * shifted * ((overshoot + 1.0D) * shifted + overshoot);
        }
        if ("easeInOutBack".equals(name)) {
            double adjusted = overshoot * 1.525D;
            double doubled = x * 2.0D;
            if (doubled < 1.0D) return doubled * doubled * ((adjusted + 1.0D) * doubled - adjusted) / 2.0D;
            double shifted = doubled - 2.0D;
            return (shifted * shifted * ((adjusted + 1.0D) * shifted + adjusted) + 2.0D) / 2.0D;
        }
        return x;
    }

    private static double[] spline(double[] a, double[] b, double[] c, double[] d, double x) {
        double square = x * x;
        double cube = square * x;
        double[] result = new double[3];
        for (int axis = 0; axis < result.length; axis++) {
            result[axis] = (2.0D * cube - 3.0D * square + 1.0D) * b[axis]
                + (cube - 2.0D * square + x) * (c[axis] - a[axis]) * 0.5D
                + (-2.0D * cube + 3.0D * square) * c[axis]
                + (cube - square) * (d[axis] - b[axis]) * 0.5D;
        }
        return result;
    }
}

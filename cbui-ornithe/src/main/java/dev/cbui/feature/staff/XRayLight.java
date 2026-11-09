package dev.cbui.feature.staff;

import java.lang.reflect.Field;
import java.util.Arrays;

/** Fullbright for forced ore quads: the renderer's per-quad light data belongs to Celeritas (not obfuscated, no compile dependency), so it is reached by reflection. */
public final class XRayLight {
  private static final int FULL_LIGHT = (15 << 4) | (15 << 20);
  private static volatile boolean broken;
  private static Field quadLightField, brField, lmField;

  private XRayLight() {}

  public static void fullbright(Object renderer) {
    if (broken) return;
    try {
      if (quadLightField == null) {
        Field q = renderer.getClass().getDeclaredField("quadLight");
        q.setAccessible(true);
        Field br = q.getType().getDeclaredField("br");
        br.setAccessible(true);
        Field lm = q.getType().getDeclaredField("lm");
        lm.setAccessible(true);
        brField = br;
        lmField = lm;
        quadLightField = q;
      }
      Object data = quadLightField.get(renderer);
      Arrays.fill((float[]) brField.get(data), 1f);
      Arrays.fill((int[]) lmField.get(data), FULL_LIGHT);
    } catch (Throwable error) {
      broken = true;
    }
  }
}

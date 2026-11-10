package dev.cbui.hud.module;

import dev.cbui.hud.Anchor;
import dev.cbui.hud.Setting;
import net.minecraft.entity.Entity;
import net.minecraft.util.MovingObjectPosition;

/**
 * Display-only combo counter: consecutive swings at the same entity under the crosshair within the timeout. The count resets when you take damage
 * (hurtTime restarts), when the target changes, or when the timeout passes without another hit.
 */
public final class ComboModule extends LabelModule {
    private final Setting.Slider timeout = grouped("Behaviour", add(new Setting.Slider("Timeout (s)", 2, 0.5f, 5, 0.5f, "The combo resets after this many seconds without landing a hit")));

    private boolean wasSwinging;
    private int prevHurt, combo, shown = -1, idle;
    private Entity target;

    public ComboModule() { super("Combo", Anchor.TOP_CENTER, 0, 27, "0 combo"); }

    @Override public void tick() {
        if (mc.thePlayer == null) return;
        int hurt = mc.thePlayer.hurtTime;
        if (hurt > prevHurt) reset();
        prevHurt = hurt;

        boolean swinging = mc.thePlayer.isSwingInProgress;
        if (swinging && !wasSwinging) {
            MovingObjectPosition mop = mc.objectMouseOver;
            if (mop != null && mop.typeOfHit == net.minecraft.util.MovingObjectPosition$MovingObjectType.ENTITY && mop.entityHit != null) {
                if (mop.entityHit != target) combo = 0;
                target = mop.entityHit;
                combo++;
                idle = 0;
            }
        }
        wasSwinging = swinging;

        if (combo > 0 && ++idle > timeout.get() * 20) reset();
        if (combo != shown) { shown = combo; label(combo + " combo"); }
    }

    private void reset() { combo = 0; idle = 0; target = null; }
}

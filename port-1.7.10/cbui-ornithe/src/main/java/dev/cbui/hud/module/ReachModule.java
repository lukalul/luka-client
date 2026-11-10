package dev.cbui.hud.module;

import dev.cbui.hud.Anchor;
import net.minecraft.util.MovingObjectPosition;

/** Display-only: distance from your eyes to the entity under the crosshair when you last swung. */
public final class ReachModule extends LabelModule {
    private boolean wasSwinging;
    private double last = -1;
    private boolean dirty = true;

    public ReachModule() { super("Reach", Anchor.TOP_CENTER, 0, 49, "3.0 blocks"); }

    @Override public void tick() {
        if (mc.thePlayer == null) return;
        boolean swinging = mc.thePlayer.isSwingInProgress;
        if (swinging && !wasSwinging) {
            MovingObjectPosition mop = mc.objectMouseOver;
            if (mop != null && mop.typeOfHit == net.minecraft.util.MovingObjectPosition$MovingObjectType.ENTITY && mop.hitVec != null) {
                double dx = mop.hitVec.xCoord - mc.thePlayer.posX;
                double dy = mop.hitVec.yCoord - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
                double dz = mop.hitVec.zCoord - mc.thePlayer.posZ;
                last = Math.sqrt(dx * dx + dy * dy + dz * dz);
                dirty = true;
            }
        }
        wasSwinging = swinging;
        if (dirty) {
            dirty = false;
            long tenths = Math.round(last * 10);
            label(last < 0 ? "-.- blocks" : tenths / 10 + "." + tenths % 10 + " blocks");
        }
    }
}

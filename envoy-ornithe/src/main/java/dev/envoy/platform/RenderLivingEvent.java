package dev.envoy.platform;

import net.minecraft.entity.EntityLivingBase;

public abstract class RenderLivingEvent<T extends EntityLivingBase> {
    public final T entity;
    public final double x, y, z;
    private boolean canceled;
    protected RenderLivingEvent(T entity, double x, double y, double z) { this.entity = entity; this.x = x; this.y = y; this.z = z; }
    public void setCanceled(boolean canceled) { this.canceled = canceled; }
    public boolean isCanceled() { return canceled; }
    public static final class Pre<T extends EntityLivingBase> extends RenderLivingEvent<T> { public Pre(T e, double x, double y, double z) { super(e, x, y, z); } }
    public static final class Specials {
        public static final class Pre<T extends EntityLivingBase> extends RenderLivingEvent<T> { public Pre(T e, double x, double y, double z) { super(e, x, y, z); } }
    }
}

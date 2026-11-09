package dev.envoy.platform;

public abstract class TickEvent {
    public enum Phase { START, END }
    public final Phase phase;
    protected TickEvent(Phase phase) { this.phase = phase; }
    public static final class ClientTickEvent extends TickEvent { public ClientTickEvent(Phase phase) { super(phase); } }
    public static final class RenderTickEvent extends TickEvent {
        public final float renderTickTime;
        public RenderTickEvent(Phase phase, float renderTickTime) { super(phase); this.renderTickTime = renderTickTime; }
    }
}

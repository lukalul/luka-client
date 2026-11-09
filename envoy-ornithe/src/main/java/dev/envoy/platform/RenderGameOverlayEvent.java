package dev.envoy.platform;

public abstract class RenderGameOverlayEvent {
    public enum ElementType { ALL, BOSSHEALTH }
    public final ElementType type;
    private boolean canceled;
    protected RenderGameOverlayEvent(ElementType type) { this.type = type; }
    public void setCanceled(boolean canceled) { this.canceled = canceled; }
    public boolean isCanceled() { return canceled; }
    public static final class Pre extends RenderGameOverlayEvent { public Pre(ElementType type) { super(type); } }
    public static final class Post extends RenderGameOverlayEvent { public Post(ElementType type) { super(type); } }
}

package dev.envoy.render;

import dev.envoy.platform.RenderGameOverlayEvent;

public final class GlowOverlayListener {
    public void onRenderOverlayPost(RenderGameOverlayEvent.Post event) {
        compositeAfterCompleteHud(event.type);
    }

    private static void compositeAfterCompleteHud(RenderGameOverlayEvent.ElementType phase) {
        if (phase == RenderGameOverlayEvent.ElementType.ALL) GlowRenderer.displayFramebuffer();
    }
}

package dev.cbui.hud.module;

import dev.cbui.hud.AbstractHudModule;
import dev.cbui.hud.Anchor;

/** A switch with no HUD element: turns the blurred menu background on or off (see {@code GuiBlur}). */
public final class GuiBlurModule extends AbstractHudModule {
    public GuiBlurModule() {
        super("GUI blur", true, Anchor.TOP_LEFT, 0, 0, 0, 0);
        background.set(false);
    }

    @Override public boolean hasHud() { return false; }

    @Override protected void draw(float partialTicks, boolean editor) { }
}

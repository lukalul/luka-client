package dev.cbui.hud.module;

import dev.cbui.hud.AbstractHudModule;
import dev.cbui.hud.Anchor;

/**
 * Menu Blur: a card with no HUD element that turns the blurred menu background on or off (see {@code GuiBlur}). Off by default.
 * Until 2026-11-27 it was "GUI blur" on the General settings tab and on by default; the new name starts everyone at off.
 */
public final class GuiBlurModule extends AbstractHudModule {
    public GuiBlurModule() {
        super("Menu Blur", false, Anchor.TOP_LEFT, 0, 0, 0, 0);
        background.set(false);
    }

    @Override public boolean hasHud() { return false; }

    @Override protected void draw(float partialTicks, boolean editor) { }
}

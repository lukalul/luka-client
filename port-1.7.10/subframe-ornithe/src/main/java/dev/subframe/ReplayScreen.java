package dev.subframe;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.Locale;

/**
 * Transparent screen that keeps game input away from the replayed world. In the viewer it also draws the timeline:
 * click or drag to jump, wheel to zoom, drag the green and red handles (or press I and O) to choose which part of the
 * recording gets rendered. While rendering or diffing, Esc aborts.
 */
final class ReplayScreen extends GuiScreen {
    private static final int BAR_Y0 = 24, BAR_Y1 = 42, MARGIN = 14, HANDLE_GRAB = 5;
    private static final double[] STEPS = {0.1, 0.2, 0.5, 1, 2, 5, 10, 15, 30, 60, 120, 300, 600, 1800, 3600};
    private static final long MIN_VIEW_NS = 2_000_000_000L;

    private enum Drag { NONE, SEEK, IN, OUT }

    /** The visible window of the timeline, in ns from the recording start. */
    private long viewStart;
    private long viewLen = -1;
    private Drag drag = Drag.NONE;
    private long dragNs;
    private int mouseXNow, mouseYNow;

    // ---- geometry ----

    private int x0() {
        return MARGIN;
    }

    private int x1() {
        return width - MARGIN;
    }

    private long total(Replay r) {
        return Math.max(1, r.totalNs());
    }

    private void fitView(Replay r) {
        long t = total(r);
        if (viewLen < 0 || viewLen > t) {
            viewLen = t;
        }
        long minLen = Math.min(t, MIN_VIEW_NS);
        viewLen = Math.max(minLen, viewLen);
        viewStart = Math.max(0, Math.min(t - viewLen, viewStart));
    }

    private int toX(long ns) {
        return x0() + (int) ((ns - viewStart) * (double) (x1() - x0()) / viewLen);
    }

    private long toNs(Replay r, int x) {
        double f = (x - x0()) / (double) (x1() - x0());
        return Math.max(0, Math.min(total(r) - 1, viewStart + (long) (f * viewLen)));
    }

    private static String fmt(long ns) {
        return RenderRange.format(ns / 1e9);
    }

    // ---- drawing ----

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        mouseXNow = mouseX;
        mouseYNow = mouseY;
        Replay r = Replay.active;
        if (r == null || !r.isPreview()) {
            return;
        }
        fitView(r);
        // keep the playhead in view while playing
        long pos = drag == Drag.SEEK ? dragNs : r.posNs();
        if (drag == Drag.NONE && !r.isPaused() && viewLen < total(r)) {
            if (pos > viewStart + viewLen - viewLen / 20 || pos < viewStart) {
                viewStart = Math.max(0, Math.min(total(r) - viewLen, pos - viewLen / 10));
            }
        }
        int xa = x0(), xb = x1();
        drawRect(xa - 3, BAR_Y0 - 3, xb + 3, BAR_Y1 + 3, 0x99000000);
        drawRect(xa, BAR_Y0, xb, BAR_Y1, 0xCC202020);

        // selected range
        long in = drag == Drag.IN ? dragNs : r.inNs();
        long out = drag == Drag.OUT ? dragNs : r.outNs();
        boolean sel = r.rangeActive() || drag == Drag.IN || drag == Drag.OUT;
        if (sel) {
            int ix = Math.max(xa, Math.min(xb, toX(in)));
            int ox = Math.max(xa, Math.min(xb, toX(out)));
            if (ox > ix) {
                drawRect(ix, BAR_Y0, ox, BAR_Y1, 0x8844AAFF);
            }
        }

        // ticks and labels
        double secsPerPx = (viewLen / 1e9) / (xb - xa);
        double major = STEPS[STEPS.length - 1];
        for (double step : STEPS) {
            if (step / secsPerPx >= 70) {
                major = step;
                break;
            }
        }
        double minor = major / 5;
        double viewStartS = viewStart / 1e9, viewEndS = (viewStart + viewLen) / 1e9;
        if (minor / secsPerPx >= 6) {
            for (double t = Math.ceil(viewStartS / minor) * minor; t <= viewEndS; t += minor) {
                int x = toX((long) (t * 1e9));
                drawRect(x, BAR_Y1 - 4, x + 1, BAR_Y1, 0x99FFFFFF);
            }
        }
        for (double t = Math.ceil(viewStartS / major) * major; t <= viewEndS; t += major) {
            int x = toX((long) (t * 1e9));
            drawRect(x, BAR_Y1 - 8, x + 1, BAR_Y1, 0xFFCCCCCC);
            String label = major < 1 ? String.format(Locale.ROOT, "%.1f", t) : RenderRange.format(t).replace(".0", "");
            drawString(fontRendererObj, label, x + 2, BAR_Y0 + 2, 0xAAAAAA);
        }

        // range handles
        if (sel) {
            drawHandle(toX(in), 0xFF55FF55, true);
            drawHandle(toX(out), 0xFFFF5555, false);
        }

        // kills: a red marker above the bar at each one, with the victim under the pointer
        java.util.List<KillMarks.Kill> kills = r.kills();
        KillMarks.Kill hovered = null;
        for (KillMarks.Kill k : kills) {
            if (k.ns < viewStart || k.ns > viewStart + viewLen) {
                continue;
            }
            int kx = toX(k.ns);
            drawRect(kx - 1, BAR_Y0 - 9, kx + 2, BAR_Y1, 0xFFFF3B3B);
            drawRect(kx - 3, BAR_Y0 - 12, kx + 4, BAR_Y0 - 8, 0xFFFF3B3B);
            drawRect(kx - 2, BAR_Y0 - 11, kx + 3, BAR_Y0 - 9, 0xFFFFD0D0);
            if (hovered == null && Math.abs(mouseX - kx) <= 4 && mouseY >= BAR_Y0 - 14 && mouseY <= BAR_Y1 + 4) {
                hovered = k;
            }
        }
        if (hovered != null) {
            String tip = "Kill: " + hovered.victim + "  " + fmt(hovered.ns) + "  (click to jump 3 s before)";
            int w = fontRendererObj.getStringWidth(tip);
            int tx = Math.max(2, Math.min(width - w - 6, mouseX - w / 2));
            drawRect(tx - 2, BAR_Y1 + 36, tx + w + 3, BAR_Y1 + 48, 0xDD000000);
            drawString(fontRendererObj, tip, tx, BAR_Y1 + 38, 0xFF7B7B);
        }

        // playhead
        int px = Math.max(xa, Math.min(xb, toX(pos)));
        drawRect(px - 1, BAR_Y0 - 3, px + 1, BAR_Y1 + 3, 0xFFFFFFFF);
        drawRect(px - 3, BAR_Y0 - 5, px + 3, BAR_Y0 - 2, 0xFFFFFFFF);

        // text under the bar
        String line = fmt(r.posNs()) + " / " + fmt(total(r)) + (r.isPaused() ? "  (paused)" : "");
        if (r.rangeActive()) {
            line += "     Render: " + fmt(in) + " - " + fmt(out) + "  (" + String.format(Locale.ROOT, "%.1f", (out - in) / 1e9) + " s)";
        } else {
            line += "     Render: whole recording";
        }
        if (r.isLooping()) {
            line += "     looping the selection";
        }
        if (Freecam.on()) {
            line += "     " + Freecam.describe() + (Freecam.cursorFree() ? "  (Tab: steer)" : "  (right click a player: their POV, Tab: mouse)");
        }
        if (!kills.isEmpty()) {
            line += "     Kills: " + kills.size() + "  (J / K: previous / next)";
        }
        drawString(fontRendererObj, line, xa, BAR_Y1 + 6, 0xFFFFFF);
        drawString(fontRendererObj, "Click: jump   Wheel: zoom   I/O: render range   X: clear   L: loop   Space: pause   R: restart   J/K: kills   F: freecam   F5: view   Esc: menu",
                xa, 6, 0xBBBBBB);

        // time under the pointer
        if (mouseY >= BAR_Y0 - 3 && mouseY <= BAR_Y1 + 3 && mouseX >= xa && mouseX <= xb && drag == Drag.NONE) {
            String tip = fmt(toNs(r, mouseX));
            int w = fontRendererObj.getStringWidth(tip);
            int tx = Math.max(2, Math.min(width - w - 6, mouseX - w / 2));
            drawRect(tx - 2, BAR_Y1 + 20, tx + w + 3, BAR_Y1 + 32, 0xCC000000);
            drawString(fontRendererObj, tip, tx, BAR_Y1 + 22, 0xFFFFFF);
        }
    }

    private void drawHandle(int x, int color, boolean isIn) {
        drawRect(x - 1, BAR_Y0 - 2, x + 2, BAR_Y1 + 2, color);
        int dir = isIn ? 1 : -1;
        drawRect(x + (isIn ? 2 : -6), BAR_Y0 - 2, x + (isIn ? 6 : -2), BAR_Y0 + 3, color);
        drawRect(x + (isIn ? 2 : -6), BAR_Y1 - 3, x + (isIn ? 6 : -2), BAR_Y1 + 2, color);
    }

    // ---- mouse ----

    private boolean onBar(int x, int y) {
        return y >= BAR_Y0 - 14 && y <= BAR_Y1 + 4 && x >= x0() - 4 && x <= x1() + 4;
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        Replay r = Replay.active;
        if (r != null && r.isPreview() && Freecam.on() && button == 1) {
            Freecam.rightClick();
            return;
        }
        if (r == null || !r.isPreview() || button != 0 || !onBar(x, y) || !Freecam.cursorFree()) {
            return;
        }
        fitView(r);
        for (KillMarks.Kill k : r.kills()) {
            if (k.ns >= viewStart && k.ns <= viewStart + viewLen && Math.abs(x - toX(k.ns)) <= 4 && y < BAR_Y0 + 4) {
                r.seekRel(Math.max(0, k.ns - 3_000_000_000L));
                return;
            }
        }
        if (r.rangeActive() && Math.abs(x - toX(r.inNs())) <= HANDLE_GRAB) {
            drag = Drag.IN;
            dragNs = r.inNs();
        } else if (r.rangeActive() && Math.abs(x - toX(r.outNs())) <= HANDLE_GRAB) {
            drag = Drag.OUT;
            dragNs = r.outNs();
        } else {
            drag = Drag.SEEK;
            dragNs = toNs(r, x);
        }
    }

    @Override
    protected void mouseClickMove(int x, int y, int button, long timeSinceLastClick) {
        Replay r = Replay.active;
        if (r == null || drag == Drag.NONE) {
            return;
        }
        dragNs = toNs(r, x);
        if (drag == Drag.IN) {
            dragNs = Math.min(dragNs, r.outNs() - 200_000_000L);
        } else if (drag == Drag.OUT) {
            dragNs = Math.max(dragNs, r.inNs() + 200_000_000L);
        }
    }

    @Override
    protected void mouseReleased(int x, int y, int button) {
        Replay r = Replay.active;
        Drag d = drag;
        drag = Drag.NONE;
        if (r == null || d == Drag.NONE) {
            return;
        }
        if (d == Drag.SEEK) {
            r.seekRel(dragNs);
        } else if (d == Drag.IN) {
            r.setRange(dragNs, r.outNs());
        } else if (d == Drag.OUT) {
            r.setRange(r.inNs(), dragNs);
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        Replay r = Replay.active;
        if (wheel == 0 || r == null || !r.isPreview() || !Freecam.cursorFree()) {
            return;
        }
        int x = Mouse.getEventX() * width / mc.displayWidth;
        fitView(r);
        // zoom around the pointer so what is under it stays put
        long anchor = toNs(r, Math.max(x0(), Math.min(x1(), x)));
        double f = (anchor - viewStart) / (double) viewLen;
        viewLen = wheel > 0 ? (long) (viewLen * 0.8) : (long) (viewLen * 1.25);
        fitView(r);
        viewStart = anchor - (long) (f * viewLen);
        fitView(r);
    }

    // ---- keys ----

    /**
     * The game reports key presses to other mods (their hotkeys, HUD editors, ...) only while no screen is open, and
     * this screen is always open. So pass every key event on the way the game itself would: update the key binding
     * state and fire Forge's key input event.
     */
    @Override
    public void handleKeyboardInput() {
        super.handleKeyboardInput();
        try {
            int key = Keyboard.getEventKey() == 0 ? Keyboard.getEventCharacter() + 256 : Keyboard.getEventKey();
            boolean down = Keyboard.getEventKeyState();
            KeyBinding.setKeyBindState(key, down);
            if (down) {
                KeyBinding.onTick(key);
            }
            mc.dispatchKeypresses();
        } catch (Throwable t) {
            SubFrameMod.LOG.warn("Could not pass a key press on to other mods: " + t);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        Replay r = Replay.active;
        if (r == null) {
            return;
        }
        if (!r.isPreview()) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                Replay.abort();
            }
            return;
        }
        long step = isShiftKeyDown() ? 1_000_000_000L : 5_000_000_000L;
        if (keyCode == Keyboard.KEY_F) {
            Freecam.toggle();
            return;
        }
        if (keyCode == Keyboard.KEY_TAB) {
            Freecam.toggleCursor();
            return;
        }
        if (keyCode == mc.gameSettings.keyBindTogglePerspective.getKeyCode()) {
            cyclePerspective();
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (Freecam.on() && !Freecam.cursorFree()) {
                Freecam.toggleCursor(); // the menu needs the mouse
            }
            mc.displayGuiScreen(new ReplayMenuScreen());
        } else if (keyCode == SubFrameMod.clientMenuKey) {
            r.releaseForClientMenu();
        } else if (keyCode == Keyboard.KEY_SPACE) {
            r.togglePause();
        } else if (keyCode == Keyboard.KEY_R) {
            r.restartPreview();
        } else if (keyCode == Keyboard.KEY_I) {
            r.setRange(r.posNs(), r.rangeActive() ? Math.max(r.outNs(), r.posNs() + 200_000_000L) : r.totalNs());
        } else if (keyCode == Keyboard.KEY_O) {
            r.setRange(r.rangeActive() ? Math.min(r.inNs(), r.posNs() - 200_000_000L) : 0, r.posNs());
        } else if (keyCode == Keyboard.KEY_X) {
            r.clearRange();
        } else if (keyCode == Keyboard.KEY_L) {
            r.toggleLoop();
        } else if (keyCode == Keyboard.KEY_LEFT) {
            r.seekRel(r.posNs() - step);
        } else if (keyCode == Keyboard.KEY_RIGHT) {
            r.seekRel(r.posNs() + step);
        } else if (keyCode == Keyboard.KEY_K || keyCode == Keyboard.KEY_J) {
            long now = r.posNs() + (keyCode == Keyboard.KEY_J ? -2_000_000_000L : 4_000_000_000L);
            KillMarks.Kill pick = null;
            for (KillMarks.Kill k : r.kills()) {
                if (keyCode == Keyboard.KEY_K && k.ns > now && pick == null) pick = k;
                if (keyCode == Keyboard.KEY_J && k.ns < now) pick = k;
            }
            if (pick != null) r.seekRel(Math.max(0, pick.ns - 3_000_000_000L));
        } else if (keyCode == Keyboard.KEY_HOME) {
            r.seekRel(0);
        }
    }

    /** The perspective key (F5 by default): first person, behind, in front. Not while the free camera is on. */
    private void cyclePerspective() {
        if (Freecam.on()) {
            return;
        }
        mc.gameSettings.thirdPersonView = (mc.gameSettings.thirdPersonView + 1) % 3;
        mc.renderGlobal.onStaticEntitiesChanged();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}

package dev.motionblur;

/** Pure state machine describing when a frame is captured, blended, or ignored. */
public final class BlurState {
    public enum Action { OFF, SKIP, CAPTURE, BLEND }

    private boolean enabled;
    private boolean historyValid;
    private int width = -1;
    private int height = -1;
    private Object scene;
    private int frame;

    public void setEnabled(boolean value) {
        if (enabled == value) return;
        enabled = value;
        resetHistory();
        if (!value) {
            width = -1;
            height = -1;
            scene = null;
        }
    }

    public Action next(int newWidth, int newHeight, Object newScene, boolean allowed, int frameSkip) {
        if (!enabled) return Action.OFF;
        if (!allowed || newWidth <= 0 || newHeight <= 0 || newScene == null) {
            resetHistory();
            return Action.SKIP;
        }
        if (newWidth != width || newHeight != height || newScene != scene) {
            width = newWidth;
            height = newHeight;
            scene = newScene;
            resetHistory();
        }
        frame++;
        if (!historyValid) {
            historyValid = true;
            return Action.CAPTURE;
        }
        int interval = AlphaMath.clamp(frameSkip, 1, 10);
        return frame % interval == 0 ? Action.BLEND : Action.SKIP;
    }

    public void clearHistory() {
        resetHistory();
    }

    public boolean hasHistory() {
        return historyValid;
    }

    private void resetHistory() {
        historyValid = false;
        frame = 0;
    }
}

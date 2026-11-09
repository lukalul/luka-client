package dev.envoy.gui;

/** Contract used by the HUD layout editor. Coordinates are normalized screen fractions. */
public abstract class HudElement {
    public abstract String name();
    public abstract float getX();
    public abstract void setX(float value);
    public abstract float getY();
    public abstract void setY(float value);
    public abstract int halfWidth();
    public abstract int halfHeight();
    public abstract void renderPreview(int centerX, int centerY);

    final boolean contains(int mouseX, int mouseY, int screenWidth, int screenHeight) {
        int centerX = Math.round(getX() * screenWidth);
        int centerY = Math.round(getY() * screenHeight);
        return Math.abs(mouseX - centerX) <= halfWidth()
            && Math.abs(mouseY - centerY) <= halfHeight();
    }
}

package dev.envoy.module.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public final class JemPart {
    private static final float PIXEL = 0.0625F;
    private static final float DEGREES_PER_RADIAN = 57.295776F;

    public String id = "";
    public String attachTo = null;
    public boolean attach = false;
    public float translateX, translateY, translateZ;
    public float rotateX, rotateY, rotateZ;
    public float scale = 1.0F;
    public float animTranslateX, animTranslateY, animTranslateZ;
    public float animRotateX, animRotateY, animRotateZ;
    public boolean hidden;
    public boolean visible = true;
    public float animScaleX = 1.0F;
    public float animScaleY = 1.0F;
    public float animScaleZ = 1.0F;
    public ResourceLocation texture;

    private final List<JemBox> boxes = new ArrayList<>();
    private final List<JemPart> children = new ArrayList<>();
    private int displayList = -1;

    public void addBox(JemBox box) { this.boxes.add(box); }
    public void addChild(JemPart child) { this.children.add(child); }
    public List<JemPart> getChildren() { return this.children; }

    public void resetAnimation() {
        ArrayDeque<JemPart> pending = new ArrayDeque<>();
        pending.add(this);
        while (!pending.isEmpty()) {
            JemPart part = pending.removeFirst();
            part.clearPose();
            pending.addAll(part.children);
        }
    }

    private void clearPose() {
        this.animTranslateX = this.animTranslateY = this.animTranslateZ = 0.0F;
        this.animRotateX = this.animRotateY = this.animRotateZ = 0.0F;
        this.animScaleX = this.animScaleY = this.animScaleZ = 1.0F;
        this.hidden = false;
    }

    public JemPart find(String name) {
        ArrayDeque<JemPart> pending = new ArrayDeque<>();
        pending.add(this);
        while (!pending.isEmpty()) {
            JemPart candidate = pending.removeFirst();
            if (name.equals(candidate.id)) return candidate;
            for (int i = candidate.children.size() - 1; i >= 0; i--) {
                pending.addFirst(candidate.children.get(i));
            }
        }
        return null;
    }

    public void render() {
        if (this.hidden || !this.visible) return;
        GL11.glPushMatrix();
        applyTransform();
        drawOwnGeometry();
        for (JemPart child : this.children) child.render();
        GL11.glPopMatrix();
    }

    private void applyTransform() {
        GL11.glTranslatef((this.translateX + this.animTranslateX) * PIXEL,
            (this.translateY + this.animTranslateY) * PIXEL,
            (this.translateZ + this.animTranslateZ) * PIXEL);
        rotate(this.rotateZ + this.animRotateZ, 0.0F, 0.0F, 1.0F);
        rotate(this.rotateY + this.animRotateY, 0.0F, 1.0F, 0.0F);
        rotate(this.rotateX + this.animRotateX, 1.0F, 0.0F, 0.0F);
        float x = this.scale * this.animScaleX;
        float y = this.scale * this.animScaleY;
        float z = this.scale * this.animScaleZ;
        if (x != 1.0F || y != 1.0F || z != 1.0F) GL11.glScalef(x, y, z);
    }

    private static void rotate(float radians, float x, float y, float z) {
        if (radians != 0.0F) GL11.glRotatef(radians * DEGREES_PER_RADIAN, x, y, z);
    }

    private void drawOwnGeometry() {
        if (this.boxes.isEmpty()) return;
        if (this.texture != null) Minecraft.getMinecraft().getTextureManager().bindTexture(this.texture);
        if (this.displayList < 0) buildDisplayList();
        GL11.glCallList(this.displayList);
    }

    private void buildDisplayList() {
        this.displayList = GL11.glGenLists(1);
        GL11.glNewList(this.displayList, GL11.GL_COMPILE);
        GL11.glBegin(GL11.GL_QUADS);
        for (JemBox box : this.boxes) box.render();
        GL11.glEnd();
        GL11.glEndList();
    }

    public void dispose() {
        ArrayDeque<JemPart> pending = new ArrayDeque<>();
        pending.add(this);
        while (!pending.isEmpty()) {
            JemPart part = pending.removeFirst();
            if (part.displayList >= 0) {
                GL11.glDeleteLists(part.displayList, 1);
                part.displayList = -1;
            }
            pending.addAll(part.children);
        }
    }
}

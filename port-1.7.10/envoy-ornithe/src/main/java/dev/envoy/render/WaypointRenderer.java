package dev.envoy.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.lwjgl.opengl.GL11;

import dev.envoy.module.waypoint.WaypointState;
import dev.envoy.module.waypoint.LocalWaypoints;
import dev.envoy.EnvoySettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.entity.RenderManager;
import dev.luka.compat.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraft.util.Vec3;
import dev.envoy.platform.RenderWorldLastEvent;

/**
 * Draws the Apollo waypoints: a colored vertical beam (beacon) at the block, and a label (name +
 * distance) projected to the screen, clamped to the edge when the waypoint is off-screen.
 */
public final class WaypointRenderer {

    private static final float BEAM_HALF_WIDTH = 0.15F;
    private static final float BEAM_HEIGHT = 300.0F;
    private static final float BEAM_ALPHA = 0.4F;
    private static final float EDGE_MARGIN = 16.0F;
    private static final double LABEL_HEIGHT = 1.6D;
    private static final float TEXT_SCALE = 0.75F;
    private static final float DIST_SCALE = 0.8F;
    private static final double PAD = 2.0D;

    public void onRenderWorld(RenderWorldLastEvent event) {
        if (WaypointState.all().isEmpty() && LocalWaypoints.current().isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        Entity viewer = mc.renderViewEntity;
        if (viewer == null) {
            return;
        }

        RenderManager renderManager = RenderManager.instance;

        Vec3 camera = Vec3.createVectorHelper(renderManager.viewerPosX, renderManager.viewerPosY, renderManager.viewerPosZ);
        ScaledResolution resolution = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);

        // the camera the frame is drawn from (interpolated), not the entity's last tick position
        double vx = viewer.lastTickPosX + (viewer.posX - viewer.lastTickPosX) * event.partialTicks;
        double vy = viewer.lastTickPosY + (viewer.posY - viewer.lastTickPosY) * event.partialTicks + viewer.getEyeHeight();
        double vz = viewer.lastTickPosZ + (viewer.posZ - viewer.lastTickPosZ) * event.partialTicks;

        MatrixSnapshot matrices = MatrixSnapshot.capture();
        List<ScreenTag> labels = new ArrayList<ScreenTag>();
        List<WaypointState.Waypoint> displayed = new ArrayList<WaypointState.Waypoint>(EnvoySettings.waypointShowServer ? WaypointState.all() : java.util.Collections.<WaypointState.Waypoint>emptyList());
        for (LocalWaypoints.Waypoint local : LocalWaypoints.current()) {
            if (local.visible) displayed.add(new WaypointState.Waypoint(local.name, local.x, local.y, local.z,
                local.colorArgb, true, false, 2.0F, true, true, false, false));
        }
        for (WaypointState.Waypoint waypoint : displayed) {
            if (waypoint.hidden || !WaypointState.inCurrentWorld(waypoint)) {
                continue;
            }
            double cx = waypoint.x + 0.5D - camera.xCoord;
            double cy = waypoint.y - camera.yCoord;
            double cz = waypoint.z + 0.5D - camera.zCoord;
            double dx = waypoint.x + 0.5D - vx;
            double dy = waypoint.y + 0.5D - vy;
            double dz = waypoint.z + 0.5D - vz;
            double exactDistance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (EnvoySettings.waypointMaxDistance > 0 && exactDistance > EnvoySettings.waypointMaxDistance) continue;

            if (EnvoySettings.waypointBeam && waypoint.showBeam) {
                renderBeam(cx, cy, cz, waypoint.colorArgb, exactDistance);
            }
            if (waypoint.highlightBlock) {
                renderBlockHighlight(waypoint.x - camera.xCoord, waypoint.y - camera.yCoord,
                    waypoint.z - camera.zCoord, waypoint.colorArgb, waypoint.lineWidth);
            }

            boolean drawName = EnvoySettings.waypointName && waypoint.showText;
            boolean drawDistance = EnvoySettings.waypointDistance && waypoint.showDistance;
            if (!drawName && !drawDistance) {
                continue;
            }
            ProjectedPoint screen = matrices.toScreen(cx, cy + LABEL_HEIGHT, cz, resolution);
            // a label is only drawn while its waypoint is inside the view: nothing behind the camera, nothing off the edges
            if (screen != null && !screen.behind && screen.x >= 0.0D && screen.x <= resolution.getScaledWidth()
                && screen.y >= 0.0D && screen.y <= resolution.getScaledHeight()) {
                labels.add(new ScreenTag(screen.x, screen.y, screen.behind, waypoint, (int) Math.round(exactDistance),
                    drawName, drawDistance));
            }
        }

        if (labels.isEmpty()) {
            return;
        }

        int screenWidth = resolution.getScaledWidth();
        int screenHeight = resolution.getScaledHeight();
        setupOverlay(resolution);
        FontRenderer font = mc.fontRendererObj;
        float scale = Math.max(0.5F, Math.min(3.0F, EnvoySettings.waypointTextScale)) * TEXT_SCALE;

        // size each label, keep it on screen, then push overlapping ones apart so names stay readable
        for (ScreenTag label : labels) {
            label.measure(font, scale);
            double halfW = label.width / 2.0D;
            label.x = Math.max(EDGE_MARGIN + halfW, Math.min(screenWidth - EDGE_MARGIN - halfW, label.x));
            label.y = Math.max(EDGE_MARGIN + label.height, Math.min(screenHeight - EDGE_MARGIN - 6.0D, label.y));
        }
        Collections.sort(labels, new Comparator<ScreenTag>() {
            public int compare(ScreenTag a, ScreenTag b) {
                return Double.compare(a.y, b.y);
            }
        });
        for (int i = 0; i < labels.size(); i++) {
            ScreenTag cur = labels.get(i);
            for (int pass = 0; pass < 4; pass++) {
                boolean moved = false;
                for (int j = 0; j < i; j++) {
                    ScreenTag other = labels.get(j);
                    if (Math.abs(cur.x - other.x) < (cur.width + other.width) / 2.0D + 2.0D
                        && cur.y - cur.height < other.y + 6.0D && cur.y + 6.0D > other.y - other.height) {
                        cur.y = other.y + 6.0D + cur.height + 2.0D;
                        moved = true;
                    }
                }
                if (!moved) break;
            }
        }
        for (ScreenTag label : labels) {
            label.paint(font, scale);
        }
        cleanupOverlay();
    }

    /** One waypoint's text box and marker, positioned in scaled screen coordinates (sub-pixel, so it moves smoothly). */
    private static final class ScreenTag {
        double x;
        double y;
        final boolean offScreen;
        final WaypointState.Waypoint waypoint;
        final int distance;
        final boolean drawName;
        final boolean drawDistance;
        float nameWidth;
        float distWidth;
        double width;
        double height;
        String distText;

        ScreenTag(double x, double y, boolean offScreen, WaypointState.Waypoint waypoint, int distance,
              boolean drawName, boolean drawDistance) {
            this.x = x;
            this.y = y;
            this.offScreen = offScreen;
            this.waypoint = waypoint;
            this.distance = distance;
            this.drawName = drawName;
            this.drawDistance = drawDistance;
        }

        void measure(FontRenderer font, float scale) {
            distText = distance >= 1000 ? String.format("%.1fkm", distance / 1000.0D) : distance + "m";
            nameWidth = drawName ? font.getStringWidth(waypoint.name) * scale : 0.0F;
            distWidth = drawDistance ? font.getStringWidth(distText) * scale * DIST_SCALE : 0.0F;
            width = Math.max(nameWidth, distWidth) + PAD * 2.0D;
            height = (drawName ? 9.0D * scale : 0.0D) + (drawDistance ? 9.0D * scale * DIST_SCALE : 0.0D) + PAD * 2.0D;
        }

        void paint(FontRenderer font, float scale) {
            int rgb = waypoint.colorArgb & 0xFFFFFF;
            double left = x - width / 2.0D;
            double top = y - height - 4.0D;
            if (EnvoySettings.waypointLabelBackground) {
                fillRect(left, top, left + width, top + height, ((int) (EnvoySettings.waypointLabelBackgroundOpacity * 255.0F) & 0xFF) << 24);
            }
            fillRect(left, top + height - 1.0D, left + width, top + height, 0xFF000000 | rgb);
            double ty = top + PAD;
            if (drawName) {
                text(font, waypoint.name, x - nameWidth / 2.0D, ty, scale, 0xFF000000 | rgb);
                ty += 9.0D * scale;
            }
            if (drawDistance) {
                text(font, distText, x - distWidth / 2.0D, ty, scale * DIST_SCALE, 0xFFE0E0E0);
            }
            if (EnvoySettings.waypointMarker) {
                if (!offScreen) {
                    diamond(x, y, 3.0D, 0xFF000000 | rgb);
                } else {
                    diamond(x, y + 4.0D, 2.5D, 0xFF000000 | rgb);
                }
            }
        }

        private static void text(FontRenderer font, String text, double tx, double ty, float scale, int color) {
            GlStateManager.pushMatrix();
            GlStateManager.translate((float) tx, (float) ty, 0.0F);
            GlStateManager.scale(scale, scale, 1.0F);
            font.drawString(text, 0, 0, color, EnvoySettings.waypointTextShadow);
            GlStateManager.popMatrix();
        }
    }

    private static void fillRect(double x1, double y1, double x2, double y2, int argb) {
        quad(x1, y1, x2, y1, x2, y2, x1, y2, argb);
    }

    private static void diamond(double cx, double cy, double r, int argb) {
        quad(cx, cy - r, cx + r, cy, cx, cy + r, cx - r, cy, argb);
    }

    private static void quad(double x1, double y1, double x2, double y2, double x3, double y3, double x4, double y4, int argb) {
        float a = ((argb >> 24) & 0xFF) / 255.0F;
        float r = ((argb >> 16) & 0xFF) / 255.0F;
        float g = ((argb >> 8) & 0xFF) / 255.0F;
        float b = (argb & 0xFF) / 255.0F;
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawing(GL11.GL_QUADS);
        tessellator.setColorRGBA_F(r, g, b, a); tessellator.addVertex(x1, y1, 0.0D);
        tessellator.setColorRGBA_F(r, g, b, a); tessellator.addVertex(x4, y4, 0.0D);
        tessellator.setColorRGBA_F(r, g, b, a); tessellator.addVertex(x3, y3, 0.0D);
        tessellator.setColorRGBA_F(r, g, b, a); tessellator.addVertex(x2, y2, 0.0D);
        tessellator.draw();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void beamVertex(double x, double y, double z, float r, float g, float b, float a) {
        GL11.glColor4f(r, g, b, a);
        GL11.glVertex3d(x, y, z);
    }

    private static void renderBeam(double rx, double ry, double rz, int argb, double distance) {
        float r = ((argb >> 16) & 0xFF) / 255.0F;
        float g = ((argb >> 8) & 0xFF) / 255.0F;
        float b = (argb & 0xFF) / 255.0F;
        float alpha = Math.max(0.0F, Math.min(1.0F, EnvoySettings.waypointBeamOpacity));
        // fades out as you walk up to it, and gets wider with distance so it stays visible far away
        alpha *= (float) Math.max(0.0D, Math.min(1.0D, (distance - 2.0D) / 10.0D));
        if (alpha <= 0.0F) {
            return;
        }
        float half = (float) Math.min(1.2D, Math.max(BEAM_HALF_WIDTH, distance * 0.002D)) * EnvoySettings.waypointBeamWidth;

        GlStateManager.pushMatrix();
        GlStateManager.translate(rx, ry, rz);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0); // additive turned every beam white against a bright sky
        GlStateManager.depthMask(false);
        GlStateManager.disableDepth();

        // plain GL immediate mode: one beam is 8 vertices, no buffer round trip
        GlStateManager.shadeModel(GL11.GL_SMOOTH); // a flat shade model left by another pass turns the fade into one colour (the last vertex: fully transparent)
        GL11.glBegin(GL11.GL_QUADS);
        beamVertex(-half, 0.0D, 0.0D, r, g, b, alpha);
        beamVertex(half, 0.0D, 0.0D, r, g, b, alpha);
        beamVertex(half, BEAM_HEIGHT, 0.0D, r, g, b, 0.0F);
        beamVertex(-half, BEAM_HEIGHT, 0.0D, r, g, b, 0.0F);
        beamVertex(0.0D, 0.0D, -half, r, g, b, alpha);
        beamVertex(0.0D, 0.0D, half, r, g, b, alpha);
        beamVertex(0.0D, BEAM_HEIGHT, half, r, g, b, 0.0F);
        beamVertex(0.0D, BEAM_HEIGHT, -half, r, g, b, 0.0F);
        GL11.glEnd();
        // the last vertex left the current colour fully transparent, and GlStateManager's cache still says white, so its color(1,1,1,1) below would do nothing
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    private static void renderBlockHighlight(double rx, double ry, double rz, int argb, float lineWidth) {
        float r = ((argb >> 16) & 0xFF) / 255.0F;
        float g = ((argb >> 8) & 0xFF) / 255.0F;
        float b = (argb & 0xFF) / 255.0F;

        GlStateManager.pushMatrix();
        GlStateManager.translate(rx, ry, rz);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.depthMask(false);
        GlStateManager.disableDepth();
        GL11.glLineWidth(lineWidth <= 0.0F ? 2.0F : lineWidth);

        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawing(GL11.GL_LINES);
        edge(tessellator, 0, 0, 0, 1, 0, 0, r, g, b);
        edge(tessellator, 1, 0, 0, 1, 0, 1, r, g, b);
        edge(tessellator, 1, 0, 1, 0, 0, 1, r, g, b);
        edge(tessellator, 0, 0, 1, 0, 0, 0, r, g, b);
        edge(tessellator, 0, 1, 0, 1, 1, 0, r, g, b);
        edge(tessellator, 1, 1, 0, 1, 1, 1, r, g, b);
        edge(tessellator, 1, 1, 1, 0, 1, 1, r, g, b);
        edge(tessellator, 0, 1, 1, 0, 1, 0, r, g, b);
        edge(tessellator, 0, 0, 0, 0, 1, 0, r, g, b);
        edge(tessellator, 1, 0, 0, 1, 1, 0, r, g, b);
        edge(tessellator, 1, 0, 1, 1, 1, 1, r, g, b);
        edge(tessellator, 0, 0, 1, 0, 1, 1, r, g, b);
        tessellator.draw();

        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GL11.glLineWidth(1.0F);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    private static void edge(Tessellator tessellator, double x1, double y1, double z1,
                             double x2, double y2, double z2, float r, float g, float b) {
        tessellator.setColorRGBA_F(r, g, b, 1.0F); tessellator.addVertex(x1, y1, z1);
        tessellator.setColorRGBA_F(r, g, b, 1.0F); tessellator.addVertex(x2, y2, z2);
    }

    private static final class ProjectedPoint {
        final double x, y;
        final boolean behind;
        ProjectedPoint(double x, double y, boolean behind) { this.x = x; this.y = y; this.behind = behind; }
    }

    private static final class MatrixSnapshot {
        final float[] model = new float[16];
        final float[] projection = new float[16];

        static MatrixSnapshot capture() {
            Projection.ensure();
            MatrixSnapshot result = new MatrixSnapshot();
            for (int i = 0; i < 16; i++) {
                result.model[i] = Projection.MODELVIEW.get(i);
                result.projection[i] = Projection.PROJECTION.get(i);
            }
            return result;
        }

        ProjectedPoint toScreen(double x, double y, double z, ScaledResolution resolution) {
        float ex = model[0] * (float) x + model[4] * (float) y + model[8] * (float) z + model[12];
        float ey = model[1] * (float) x + model[5] * (float) y + model[9] * (float) z + model[13];
        float ez = model[2] * (float) x + model[6] * (float) y + model[10] * (float) z + model[14];
        float ew = model[3] * (float) x + model[7] * (float) y + model[11] * (float) z + model[15];

        float cx = projection[0] * ex + projection[4] * ey + projection[8] * ez + projection[12] * ew;
        float cy = projection[1] * ex + projection[5] * ey + projection[9] * ez + projection[13] * ew;
        float cw = projection[3] * ex + projection[7] * ey + projection[11] * ez + projection[15] * ew;

        if (Math.abs(cw) <= 1.0E-4F) {
            return null;
        }

        boolean behind = cw < 0.0F;
        float ndcX = cx / cw;
        float ndcY = cy / cw;
        if (behind) {
            // behind the camera: point the label towards where the waypoint is and park it on the screen edge
            ndcX = -ndcX;
            ndcY = -ndcY;
            float m = Math.max(Math.abs(ndcX), Math.abs(ndcY));
            if (m < 1.0E-4F) {
                ndcX = 0.0F;
                ndcY = -1.0F;
                m = 1.0F;
            }
            ndcX = ndcX / m * 10.0F;
            ndcY = ndcY / m * 10.0F;
        }
        double screenX = (ndcX + 1.0D) / 2.0D * resolution.getScaledWidth();
        double screenY = (1.0D - ndcY) / 2.0D * resolution.getScaledHeight();
        return new ProjectedPoint(screenX, screenY, behind);
        }
    }

    private static void setupOverlay(ScaledResolution resolution) {
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0D, resolution.getScaledWidth(), resolution.getScaledHeight(), 0.0D, -1.0D, 1.0D);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();

        GlStateManager.disableDepth();
        GlStateManager.disableLighting();
    }

    private static void cleanupOverlay() {
        GlStateManager.enableDepth();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
    }
}

package dev.envoy.render;

import dev.envoy.module.model.JemAnimation;
import dev.envoy.module.model.JemModel;
import dev.envoy.module.model.ModelAnimator;
import dev.envoy.module.model.ModelRegistry;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.AxisAlignedBB;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import dev.envoy.platform.RenderLivingEvent;
import dev.envoy.platform.TickEvent;

public final class CustomModelRenderer {
    private static final float MODEL_OFFSET_Y = -1.5078125F;
    private static final float DEFAULT_RENDER_DISTANCE = 256.0F;

    private static float partialTicks;
    private static final Map<Entity, Float> RENDER_RANGE = new WeakHashMap<Entity, Float>();

    private static final Set<Entity> RENDERED_FRAME = Collections.newSetFromMap(new WeakHashMap<Entity, Boolean>());

    public static void beginFrame() {
        RENDERED_FRAME.clear();
    }

    public static void renderMissed(Entity viewer, ICamera camera, float partialTicks) {
        if (RENDER_RANGE.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        RenderManager renderManager = mc.getRenderManager();
        CameraPosition origin = new CameraPosition(renderManager.viewerPosX, renderManager.viewerPosY,
            renderManager.viewerPosZ);
        Frustum own = null;

        for (Map.Entry<Entity, Float> entry : RENDER_RANGE.entrySet()) {
            Entity entity = entry.getKey();
            if (entity == viewer || entity.isDead || RENDERED_FRAME.contains(entity)) {
                continue;
            }
            float range = entry.getValue();
            if (!origin.contains(entity, range)) {
                continue;
            }
            AxisAlignedBB box = entity.getEntityBoundingBox();
            boolean cameraFrustum = camera != null && camera.isBoundingBoxInFrustum(box);
            boolean ownFrustum = cameraFrustum;
            if (!cameraFrustum && !entity.ignoreFrustumCheck) {
                if (own == null) {
                    own = new Frustum();
                    own.setPosition(origin.x, origin.y, origin.z);
                }
                ownFrustum = own.isBoundingBoxInFrustum(box);
            }
            if (!entity.ignoreFrustumCheck && !ownFrustum) {
                continue;
            }
            renderManager.renderEntitySimple(entity, partialTicks);
        }
    }
    public static float renderRange(Entity entity) {
        Float range = RENDER_RANGE.get(entity);
        return range == null ? 0.0F : range;
    }

    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            partialTicks = event.renderTickTime;
        }
    }

    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        ModelRegistry.ensureLoaded();
        if (ModelRegistry.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) {
            return;
        }
        RENDER_RANGE.clear();
        for (Entity entity : mc.theWorld.loadedEntityList) {
            ModelRegistry.Entry entry = ModelRegistry.findForEntity(entity);
            if (entry == null) {
                continue;
            }
            if (entry.ignoreCull) {
                entity.ignoreFrustumCheck = true;
            }
            float distance = configuredDistance(entry);
            if (distance > 0.0F) {
                entity.renderDistanceWeight = distanceWeight(entity, distance);
                RENDER_RANGE.put(entity, distance);
            }
        }
    }

    private static float configuredDistance(ModelRegistry.Entry entry) {
        if (entry.renderDistance > 0.0F) return entry.renderDistance;
        return entry.ignoreCull ? DEFAULT_RENDER_DISTANCE : 0.0F;
    }

    private static double distanceWeight(Entity entity, float blocks) {
        double edge = entity.getEntityBoundingBox().getAverageEdgeLength();
        if (Double.isNaN(edge) || edge <= 0.0D) {
            edge = 1.0D;
        }
        return blocks / (edge * 64.0D);
    }

    public void onRenderLiving(RenderLivingEvent.Pre<EntityLivingBase> event) {
        if (ModelRegistry.isEmpty()) {
            return;
        }
        EntityLivingBase entity = event.entity;
        ModelRegistry.Entry entry = ModelRegistry.findForEntity(entity);
        if (entry == null) {
            return;
        }
        JemModel model = ModelRegistry.getModel(entry);
        if (model == null) {
            return;
        }

        event.setCanceled(true);
        RENDERED_FRAME.add(entity);

        model.resetAnimation();
        JemAnimation animation = ModelRegistry.getAnimation(entry);
        if (animation != null) {
            ModelAnimator.apply(model, animation, (entity.ticksExisted + partialTicks) / 20.0D);
        }
        drawModel(entity, model, entry.scale, event.x, event.y, event.z);
    }

    private static void drawModel(EntityLivingBase entity, JemModel model, float scale,
                               double x, double y, double z) {
        float yaw = entity.prevRenderYawOffset
            + (entity.renderYawOffset - entity.prevRenderYawOffset) * partialTicks;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.rotate(180.0F - yaw, 0.0F, 1.0F, 0.0F);
        GlStateManager.enableRescaleNormal();
        GlStateManager.scale(-1.0F, -1.0F, 1.0F);
        if (scale != 1.0F) {
            GlStateManager.scale(scale, scale, scale);
        }
        GlStateManager.translate(0.0F, MODEL_OFFSET_Y, 0.0F);

        model.render();

        GlStateManager.disableRescaleNormal();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    private static final class CameraPosition {
        private final double x, y, z;

        private CameraPosition(double x, double y, double z) {
            this.x = x; this.y = y; this.z = z;
        }

        private boolean contains(Entity entity, float radius) {
            double dx = entity.posX - this.x;
            double dy = entity.posY - this.y;
            double dz = entity.posZ - this.z;
            return dx * dx + dy * dy + dz * dz < (double) radius * radius;
        }
    }
}

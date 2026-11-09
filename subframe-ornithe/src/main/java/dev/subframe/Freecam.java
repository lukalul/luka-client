package dev.subframe;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.UUID;

/**
 * The viewer's free camera: the view leaves the recorded player and flies on its own (W A S D, jump key up, sneak key down, sprint key faster,
 * wheel for speed). While it is on, a right click on a player shows the world through that player's eyes; another right click,
 * a movement key or F goes back to flying. Tab frees the mouse for the timeline.
 */
public final class Freecam {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private static boolean on;
    /** The player whose eyes are the camera, or null while flying. */
    private static Entity pov;
    private static EntityOtherPlayerMP camera;
    private static double x, y, z;
    private static float yaw, pitch;
    private static double speed = 10.0; // blocks per second
    private static long lastNs;
    private static int savedView;
    private static boolean cursor;
    private static int moveLogs;

    private Freecam() {}

    static boolean on() {
        return on;
    }

    /** True while the camera is not the recorded player's own view (the first-person hand is not drawn then). */
    public static boolean detached() {
        return on && pov != mc.thePlayer;
    }

    /** True when the mouse is free to click the timeline (otherwise it steers the camera). */
    static boolean cursorFree() {
        return !on || cursor;
    }

    static String describe() {
        if (!on) {
            return "";
        }
        return pov != null ? "POV: " + pov.getName() : "Freecam";
    }

    static void toggle() {
        if (on) {
            exit();
        } else {
            enter();
        }
    }

    private static void enter() {
        if (mc.theWorld == null || mc.thePlayer == null) {
            return;
        }
        GameSettings gs = mc.gameSettings;
        savedView = gs.thirdPersonView;
        gs.thirdPersonView = 0;
        // start where the current view is, so the picture does not jump
        Entity view = mc.getRenderViewEntity() == null ? mc.thePlayer : mc.getRenderViewEntity();
        float partial = ((dev.subframe.mixin.MinecraftAccessor) mc).subframe$getTimer().renderPartialTicks;
        x = view.prevPosX + (view.posX - view.prevPosX) * partial;
        y = view.prevPosY + (view.posY - view.prevPosY) * partial + view.getEyeHeight();
        z = view.prevPosZ + (view.posZ - view.prevPosZ) * partial;
        yaw = view.prevRotationYaw + (view.rotationYaw - view.prevRotationYaw) * partial;
        pitch = view.prevRotationPitch + (view.rotationPitch - view.prevRotationPitch) * partial;
        camera = new EntityOtherPlayerMP(mc.theWorld, new GameProfile(UUID.randomUUID(), "freecam"));
        pov = null;
        on = true;
        cursor = false;
        lastNs = System.nanoTime();
        mc.setRenderViewEntity(camera);
        apply();
        Mouse.setGrabbed(true);
        SubFrameMod.LOG.info(String.format("[freecam] on at %.1f %.1f %.1f", x, y, z));
        mc.renderGlobal.setDisplayListEntitiesDirty();
    }

    static void exit() {
        if (!on) {
            return;
        }
        on = false;
        pov = null;
        cursor = false;
        SubFrameMod.LOG.info("[freecam] off");
        try {
            mc.gameSettings.thirdPersonView = savedView;
            if (mc.thePlayer != null) {
                mc.setRenderViewEntity(mc.thePlayer);
            }
            Mouse.setGrabbed(false);
            if (mc.renderGlobal != null) {
                mc.renderGlobal.setDisplayListEntitiesDirty();
            }
        } catch (Throwable t) {
            SubFrameMod.LOG.warn("Could not leave the free camera cleanly: " + t);
        }
        camera = null;
    }

    /** Tab: free the mouse to click the timeline, or take it back to steer. */
    static void toggleCursor() {
        if (!on) {
            return;
        }
        cursor = !cursor;
        Mouse.setGrabbed(!cursor);
    }

    /** Right click: the player under the crosshair becomes the camera; a right click while looking through someone goes back to flying. */
    static void rightClick() {
        if (!on) {
            return;
        }
        if (pov != null) {
            leavePov();
            return;
        }
        Vec3 from = new Vec3(x, y, z);
        Vec3 look = lookVector();
        Entity best = null;
        double bestDist = 160.0;
        for (Object o : mc.theWorld.loadedEntityList) {
            if (!(o instanceof net.minecraft.entity.EntityLivingBase)) {
                continue;
            }
            Entity e = (Entity) o;
            AxisAlignedBB box = e.getEntityBoundingBox().expand(0.3, 0.3, 0.3);
            Vec3 to = from.addVector(look.xCoord * 160.0, look.yCoord * 160.0, look.zCoord * 160.0);
            net.minecraft.util.MovingObjectPosition hit = box.calculateIntercept(from, to);
            if (hit != null) {
                double d = from.distanceTo(hit.hitVec);
                if (d < bestDist) {
                    bestDist = d;
                    best = e;
                }
            } else if (box.isVecInside(from)) {
                best = e;
                bestDist = 0;
            }
        }
        if (best == null) {
            SubFrameMod.LOG.info("[freecam] right click: nobody under the crosshair");
            return;
        }
        pov = best;
        SubFrameMod.LOG.info("[freecam] looking through " + best.getName());
        mc.setRenderViewEntity(best);
        mc.renderGlobal.setDisplayListEntitiesDirty();
    }

    private static void leavePov() {
        if (pov != null) {
            float partial = ((dev.subframe.mixin.MinecraftAccessor) mc).subframe$getTimer().renderPartialTicks;
            x = pov.prevPosX + (pov.posX - pov.prevPosX) * partial;
            y = pov.prevPosY + (pov.posY - pov.prevPosY) * partial + pov.getEyeHeight();
            z = pov.prevPosZ + (pov.posZ - pov.prevPosZ) * partial;
            yaw = pov.prevRotationYaw + (pov.rotationYaw - pov.prevRotationYaw) * partial;
            pitch = pov.prevRotationPitch + (pov.rotationPitch - pov.prevRotationPitch) * partial;
        }
        pov = null;
        if (camera != null) {
            mc.setRenderViewEntity(camera);
            apply();
        }
        mc.renderGlobal.setDisplayListEntitiesDirty();
    }

    private static Vec3 lookVector() {
        float cy = MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI);
        float sy = MathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);
        float cp = -MathHelper.cos(-pitch * 0.017453292F);
        float sp = MathHelper.sin(-pitch * 0.017453292F);
        return new Vec3(sy * cp, sp, cy * cp);
    }

    /** Once per displayed frame: keys and mouse move the camera. */
    static void input() {
        if (!on) {
            return;
        }
        long now = System.nanoTime();
        double dt = Math.min(0.1, (now - lastNs) / 1e9);
        double beforeX = x, beforeY = y, beforeZ = z;
        lastNs = now;
        // the recorded world may have been rebuilt (restart): drop a camera that belongs to another world
        if (mc.theWorld == null || mc.thePlayer == null) {
            exit();
            return;
        }
        if (camera != null && camera.worldObj != mc.theWorld) {
            // a seek rebuilt the world: keep flying from the same spot in the new one
            camera = new EntityOtherPlayerMP(mc.theWorld, new GameProfile(UUID.randomUUID(), "freecam"));
            pov = null;
            mc.setRenderViewEntity(camera);
        }
        if (pov != null && (pov.isDead || pov.worldObj != mc.theWorld)) {
            leavePov();
        }
        GameSettings gs = mc.gameSettings;
        boolean forward = gs.keyBindForward.isKeyDown();
        boolean back = gs.keyBindBack.isKeyDown();
        boolean left = gs.keyBindLeft.isKeyDown();
        boolean right = gs.keyBindRight.isKeyDown();
        boolean up = gs.keyBindJump.isKeyDown();
        boolean down = gs.keyBindSneak.isKeyDown();
        if (pov != null) {
            if (forward || back || left || right || up || down) {
                leavePov();
            } else {
                return;
            }
        }
        if (!cursor) {
            int dx = Mouse.getDX();
            int dy = Mouse.getDY();
            float f = gs.mouseSensitivity * 0.6F + 0.2F;
            float f1 = f * f * f * 8.0F;
            yaw += dx * f1 * 0.15F;
            pitch = MathHelper.clamp_float(pitch - dy * f1 * 0.15F, -90.0F, 90.0F);
        } else {
            Mouse.getDX();
            Mouse.getDY();
        }
        int wheel = Mouse.getDWheel();
        if (wheel != 0 && !cursor) {
            speed = Math.max(1.0, Math.min(200.0, speed * (wheel > 0 ? 1.25 : 0.8)));
        }
        double v = speed * dt * (gs.keyBindSprint.isKeyDown() ? 3.0 : 1.0);
        Vec3 look = lookVector();
        double rx = -look.zCoord, rz = look.xCoord; // to the right, flat
        double rl = Math.sqrt(rx * rx + rz * rz);
        if (rl > 1e-6) {
            rx /= rl;
            rz /= rl;
        }
        double mx = 0, my = 0, mz = 0;
        if (forward) { mx += look.xCoord; my += look.yCoord; mz += look.zCoord; }
        if (back) { mx -= look.xCoord; my -= look.yCoord; mz -= look.zCoord; }
        if (right) { mx += rx; mz += rz; }
        if (left) { mx -= rx; mz -= rz; }
        if (up) { my += 1; }
        if (down) { my -= 1; }
        x += mx * v;
        y += my * v;
        z += mz * v;
        if ((x != beforeX || y != beforeY || z != beforeZ) && ++moveLogs <= 3) {
            SubFrameMod.LOG.info(String.format("[freecam] moved to %.1f %.1f %.1f", x, y, z));
        }
        apply();
    }

    /** Puts the camera entity at the flying position (called every frame, right before the world is drawn). */
    static void apply() {
        if (!on || camera == null || pov != null) {
            return;
        }
        camera.setPositionAndRotation(x, y - camera.getEyeHeight(), z, yaw, pitch);
        camera.prevPosX = camera.lastTickPosX = camera.posX;
        camera.prevPosY = camera.lastTickPosY = camera.posY;
        camera.prevPosZ = camera.lastTickPosZ = camera.posZ;
        camera.prevRotationYaw = camera.rotationYaw = yaw;
        camera.prevRotationPitch = camera.rotationPitch = pitch;
        camera.rotationYawHead = camera.prevRotationYawHead = yaw;
        if (mc.getRenderViewEntity() != camera) {
            mc.setRenderViewEntity(camera);
        }
    }
}

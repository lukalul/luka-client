package dev.subframe;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.shader.Framebuffer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

/** Builds the text that goes into the render log: the environment once, and a state snapshot when something looks wrong. */
final class RenderDiag {
    /** legacy-lwjgl3's Display has no isFullscreen(): without this guard the whole render log failed to open there. */
    private static String fullscreen() {
        try {
            return String.valueOf(Display.isFullscreen());
        } catch (Throwable notThere) {
            return "unknown";
        }
    }

    private RenderDiag() {
    }

    /** A cheap fingerprint of a frame: a sample of its bytes. Equal fingerprints on consecutive frames mean a frozen picture. */
    static long hash(byte[] px) {
        long h = 1125899906842597L;
        int step = Math.max(1, px.length / 60000) | 1;
        for (int i = 0; i < px.length; i += step) {
            h = 31 * h + px[i];
        }
        return h * 31 + px.length;
    }

    static String environment(Recording rec) {
        Minecraft mc = Minecraft.getMinecraft();
        StringBuilder sb = new StringBuilder();
        sb.append("java ").append(System.getProperty("java.version")).append(", ").append(System.getProperty("os.name")).append('\n');
        try {
            sb.append("GL vendor=").append(GL11.glGetString(GL11.GL_VENDOR)).append(" renderer=").append(GL11.glGetString(GL11.GL_RENDERER))
                    .append(" version=").append(GL11.glGetString(GL11.GL_VERSION)).append('\n');
        } catch (Throwable t) {
            sb.append("GL info unavailable: ").append(t).append('\n');
        }
        sb.append("window ").append(Display.getWidth()).append('x').append(Display.getHeight())
                .append(" active=").append(Display.isActive()).append(" visible=").append(Display.isVisible())
                .append(" fullscreen=").append(fullscreen()).append('\n');
        Framebuffer fb = mc.getFramebuffer();
        sb.append("framebuffer ").append(fb.framebufferWidth).append('x').append(fb.framebufferHeight).append('\n');
        sb.append("settings: fov=").append(mc.gameSettings.fovSetting).append(" renderDistance=").append(mc.gameSettings.renderDistanceChunks)
                .append(" fancy=").append(mc.gameSettings.fancyGraphics).append(" particles=").append(mc.gameSettings.particleSetting)
                .append(" limitFramerate=").append(mc.gameSettings.limitFramerate).append(" vsync=").append(mc.gameSettings.enableVsync)
                .append('\n');
        sb.append("SubFrame: fps=").append(SubFrameMod.outputFps).append(" subframes=").append(SubFrameMod.subframes)
                .append(" blend=").append(SubFrameMod.blend).append(" png=").append(SubFrameMod.outputPng)
                .append(" shutter=").append(SubFrameMod.shutter).append(" weighting=").append(SubFrameMod.weighting).append(" maxSeconds=").append(SubFrameMod.maxSeconds)
                .append(" useRecordedSettings=").append(SubFrameMod.useRecordedSettings).append('\n');
        sb.append("recording: frames=").append(rec.frameCount).append(" packets=").append(rec.packets.size())
                .append(" tickSnapshots=").append(rec.ticks.size()).append(" joinTick=").append(rec.joinTick)
                .append(" firstFrameG=").append(rec.frameG[0]).append(" lastFrameG=").append(rec.frameG[rec.frameCount - 1])
                .append(" seconds=").append((rec.endTime() - rec.startTime()) / 1e9).append('\n');
        sb.append("mods: ");
        try {
            for (ModContainer m : FabricLoader.getInstance().getAllMods()) {
                sb.append(m.getMetadata().getId()).append(' ')
                        .append(m.getMetadata().getVersion().getFriendlyString()).append("; ");
            }
        } catch (Throwable t) {
            sb.append("unavailable: ").append(t);
        }
        return sb.toString();
    }

    /** Type and name of the colour attachment of the framebuffer that is bound: what the game really draws into. */
    static String colorAttachment() {
        try {
            int type = org.lwjgl.opengl.GL30.glGetFramebufferAttachmentParameteri(0x8D40, 0x8CE0, 0x8CD0);
            int name = org.lwjgl.opengl.GL30.glGetFramebufferAttachmentParameteri(0x8D40, 0x8CE0, 0x8CD1);
            return (type == 0x1702 ? "texture " : type == 0x8D41 ? "renderbuffer " : "type" + type + " ") + name;
        } catch (Throwable t) {
            return "?";
        }
    }

    private static int glInt(int pname) {
        try {
            return GL11.glGetInteger(pname);
        } catch (Throwable t) {
            return -1;
        }
    }

    /** Game and graphics state, written when the picture stops changing or a frame takes far too long. */
    static String snapshot() {
        Minecraft mc = Minecraft.getMinecraft();
        StringBuilder sb = new StringBuilder();
        EntityPlayerSP p = mc.thePlayer;
        sb.append("screen=").append(mc.currentScreen == null ? "none" : mc.currentScreen.getClass().getName());
        sb.append(" world=").append(mc.theWorld != null);
        if (mc.theWorld != null) {
            sb.append(" worldTime=").append(mc.theWorld.getWorldTime()).append(" entities=").append(mc.theWorld.loadedEntityList.size());
        }
        if (p != null) {
            sb.append(" | player id=").append(p.getEntityId()).append(" pos=").append(String.format("%.2f,%.2f,%.2f", p.posX, p.posY, p.posZ))
                    .append(" yaw=").append(p.rotationYaw).append(" pitch=").append(p.rotationPitch)
                    .append(" burning=").append(p.isBurning()).append(" dead=").append(p.isDead).append(" hurtTime=").append(p.hurtTime)
                    .append(" health=").append(p.getHealth());
        } else {
            sb.append(" | player=null");
        }
        net.minecraft.entity.Entity view = mc.getRenderViewEntity();
        sb.append(" | renderView=").append(view == null ? "null" : view.getEntityId() + (view == p ? "(same as player)" : "(NOT the player)"));
        sb.append(" | window ").append(Display.getWidth()).append('x').append(Display.getHeight())
                .append(" active=").append(Display.isActive()).append(" visible=").append(Display.isVisible());
        Framebuffer fb = mc.getFramebuffer();
        sb.append(" | framebuffer ").append(fb.framebufferWidth).append('x').append(fb.framebufferHeight).append(" id=").append(fb.framebufferObject);
        sb.append(" | colorAttachment=").append(colorAttachment()).append(" mcTexture=").append(fb.framebufferTexture);
        try {
            // what the renderer drew last frame and where it thinks the camera is: a world that is there but not drawn shows here
            sb.append(" | ").append(mc.renderGlobal.getDebugInfoRenders()).append(" ").append(mc.renderGlobal.getDebugInfoEntities());
            net.minecraft.client.renderer.entity.RenderManager rm = mc.getRenderManager();
            sb.append(" | viewer ").append(String.format("%.2f,%.2f,%.2f", rm.viewerPosX, rm.viewerPosY, rm.viewerPosZ));
        } catch (Throwable t) {
            sb.append(" | renderer info unavailable: ").append(t);
        }
        sb.append(" | GL error=").append(GL11.glGetError());
        sb.append(" drawFbo=").append(glInt(0x8CA6)).append(" readFbo=").append(glInt(0x8CAA))
                .append(" packBuffer=").append(glInt(0x88ED)).append(" unpackBuffer=").append(glInt(0x88EF));
        return sb.toString();
    }
}

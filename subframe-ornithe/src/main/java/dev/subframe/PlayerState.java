package dev.subframe;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.RenderGlobal;
import dev.subframe.mixin.EntityRendererAccessor;
import dev.subframe.mixin.RenderGlobalAccessor;

import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Client-only state of the local player at the end of one client tick. None of this is sent by the
 * server, so a packet-only replay cannot reproduce it. Previous-tick values are stored too, because
 * the renderer interpolates between prev and current with the partial tick.
 */
final class PlayerState {
    double x, y, z, px, py, pz;
    float yaw, pitch, pyaw, ppitch;
    float walked, pwalked;
    float camYaw, pcamYaw, camPitch, pcamPitch;
    float armYaw, parmYaw, armPitch, parmPitch;
    float swing, pswing;
    int swingInt;
    boolean swinging;
    int slot;
    boolean sprinting, sneaking, onGround;
    int useCount;
    /** RenderGlobal's cloud tick counter: it survives world changes, so it differs between sessions. */
    int cloud;

    private static final String[] CLOUD_FIELD = {"cloudTickCounter", "field_72773_u"};

    /**
     * EntityRenderer values that are smoothed or random-walked every tick and are never reset when a world
     * loads, so they depend on everything the client did before: fog brightness (2), hand FOV (2), boss
     * colour (2), torch flicker (2). Each entry is {MCP name, SRG name}.
     */
    private static final String[][] SMOOTHED = {
            {"fogColor1", "field_78539_ae"}, {"fogColor2", "field_78535_ad"},
            {"fovModifierHand", "field_78507_R"}, {"fovModifierHandPrev", "field_78506_S"},
            {"bossColorModifier", "field_82831_U"}, {"bossColorModifierPrev", "field_82832_V"},
            {"torchFlickerX", "field_78514_e"}, {"torchFlickerDX", "field_175075_L"}};
    float[] smoothed = new float[SMOOTHED.length];

    /** The pose the game itself worked out: legs, body, head and cape. Clips made before this was saved do not have it. */
    boolean hasPose;
    float limb, limbAmount, plimbAmount;
    float body, pbody, head, phead;
    double chaseX, chaseY, chaseZ, pchaseX, pchaseY, pchaseZ;
    private static final int POSE_BYTES = 7 * 4 + 6 * 8;

    static PlayerState capture(EntityPlayerSP p) {
        PlayerState s = new PlayerState();
        s.cloud = ((RenderGlobalAccessor) Minecraft.getMinecraft().renderGlobal).subframe$getCloudTickCounter();
        EntityRendererAccessor renderer = (EntityRendererAccessor) Minecraft.getMinecraft().entityRenderer;
        s.smoothed[0] = renderer.subframe$getFogColor1();
        s.smoothed[1] = renderer.subframe$getFogColor2();
        s.smoothed[2] = renderer.subframe$getFovModifierHand();
        s.smoothed[3] = renderer.subframe$getFovModifierHandPrev();
        s.smoothed[4] = renderer.subframe$getBossColorModifier();
        s.smoothed[5] = renderer.subframe$getBossColorModifierPrev();
        s.smoothed[6] = renderer.subframe$getTorchFlickerX();
        s.smoothed[7] = renderer.subframe$getTorchFlickerDX();
        s.x = p.posX; s.y = p.posY; s.z = p.posZ;
        s.px = p.prevPosX; s.py = p.prevPosY; s.pz = p.prevPosZ;
        s.yaw = p.rotationYaw; s.pitch = p.rotationPitch;
        s.pyaw = p.prevRotationYaw; s.ppitch = p.prevRotationPitch;
        s.walked = p.distanceWalkedModified; s.pwalked = p.prevDistanceWalkedModified;
        s.camYaw = p.cameraYaw; s.pcamYaw = p.prevCameraYaw;
        s.camPitch = p.cameraPitch; s.pcamPitch = p.prevCameraPitch;
        s.armYaw = p.renderArmYaw; s.parmYaw = p.prevRenderArmYaw;
        s.armPitch = p.renderArmPitch; s.parmPitch = p.prevRenderArmPitch;
        s.swing = p.swingProgress; s.pswing = p.prevSwingProgress;
        s.swingInt = p.swingProgressInt;
        s.swinging = p.isSwingInProgress;
        s.slot = p.inventory.currentItem;
        s.sprinting = p.isSprinting();
        s.sneaking = p.isSneaking();
        s.onGround = p.onGround;
        s.useCount = p.isUsingItem() ? p.getItemInUseCount() : 0;
        s.hasPose = true;
        s.limb = p.limbSwing; s.limbAmount = p.limbSwingAmount; s.plimbAmount = p.prevLimbSwingAmount;
        s.body = p.renderYawOffset; s.pbody = p.prevRenderYawOffset;
        s.head = p.rotationYawHead; s.phead = p.prevRotationYawHead;
        s.chaseX = p.chasingPosX; s.chaseY = p.chasingPosY; s.chaseZ = p.chasingPosZ;
        s.pchaseX = p.prevChasingPosX; s.pchaseY = p.prevChasingPosY; s.pchaseZ = p.prevChasingPosZ;
        return s;
    }

    void applyTo(EntityPlayerSP p) {
        ((RenderGlobalAccessor) Minecraft.getMinecraft().renderGlobal).subframe$setCloudTickCounter(cloud);
        EntityRendererAccessor renderer = (EntityRendererAccessor) Minecraft.getMinecraft().entityRenderer;
        renderer.subframe$setFogColor1(smoothed[0]);
        renderer.subframe$setFogColor2(smoothed[1]);
        renderer.subframe$setFovModifierHand(smoothed[2]);
        renderer.subframe$setFovModifierHandPrev(smoothed[3]);
        renderer.subframe$setBossColorModifier(smoothed[4]);
        renderer.subframe$setBossColorModifierPrev(smoothed[5]);
        renderer.subframe$setTorchFlickerX(smoothed[6]);
        renderer.subframe$setTorchFlickerDX(smoothed[7]);
        p.setPosition(x, y, z);
        p.prevPosX = px; p.prevPosY = py; p.prevPosZ = pz;
        p.lastTickPosX = px; p.lastTickPosY = py; p.lastTickPosZ = pz;
        p.rotationYaw = yaw; p.rotationPitch = pitch;
        p.prevRotationYaw = pyaw; p.prevRotationPitch = ppitch;
        p.motionX = 0; p.motionY = 0; p.motionZ = 0;
        p.distanceWalkedModified = walked; p.prevDistanceWalkedModified = pwalked;
        p.cameraYaw = camYaw; p.prevCameraYaw = pcamYaw;
        p.cameraPitch = camPitch; p.prevCameraPitch = pcamPitch;
        p.renderArmYaw = armYaw; p.prevRenderArmYaw = parmYaw;
        p.renderArmPitch = armPitch; p.prevRenderArmPitch = parmPitch;
        p.swingProgress = swing; p.prevSwingProgress = pswing;
        p.swingProgressInt = swingInt;
        p.isSwingInProgress = swinging;
        p.inventory.currentItem = slot;
        p.setSprinting(sprinting);
        p.setSneaking(sneaking);
        p.onGround = onGround;
        if (useCount > 0) {
            p.setItemInUse(p.getHeldItem(), useCount);
        } else {
            p.clearItemInUse();
        }
        if (hasPose) {
            p.limbSwing = limb; p.limbSwingAmount = limbAmount; p.prevLimbSwingAmount = plimbAmount;
            p.renderYawOffset = body; p.prevRenderYawOffset = pbody;
            p.rotationYawHead = head; p.prevRotationYawHead = phead;
            p.chasingPosX = chaseX; p.chasingPosY = chaseY; p.chasingPosZ = chaseZ;
            p.prevChasingPosX = pchaseX; p.prevChasingPosY = pchaseY; p.prevChasingPosZ = pchaseZ;
        }
    }

    void write(DataOutput o) throws IOException {
        o.writeDouble(x); o.writeDouble(y); o.writeDouble(z);
        o.writeDouble(px); o.writeDouble(py); o.writeDouble(pz);
        o.writeFloat(yaw); o.writeFloat(pitch); o.writeFloat(pyaw); o.writeFloat(ppitch);
        o.writeFloat(walked); o.writeFloat(pwalked);
        o.writeFloat(camYaw); o.writeFloat(pcamYaw); o.writeFloat(camPitch); o.writeFloat(pcamPitch);
        o.writeFloat(armYaw); o.writeFloat(parmYaw); o.writeFloat(armPitch); o.writeFloat(parmPitch);
        o.writeFloat(swing); o.writeFloat(pswing);
        o.writeInt(swingInt);
        o.writeBoolean(swinging);
        o.writeInt(slot);
        o.writeBoolean(sprinting); o.writeBoolean(sneaking); o.writeBoolean(onGround);
        o.writeInt(useCount);
        o.writeInt(cloud);
        for (float f : smoothed) {
            o.writeFloat(f);
        }
        o.writeFloat(limb); o.writeFloat(limbAmount); o.writeFloat(plimbAmount);
        o.writeFloat(body); o.writeFloat(pbody); o.writeFloat(head); o.writeFloat(phead);
        o.writeDouble(chaseX); o.writeDouble(chaseY); o.writeDouble(chaseZ);
        o.writeDouble(pchaseX); o.writeDouble(pchaseY); o.writeDouble(pchaseZ);
    }

    static PlayerState read(DataInputStream i) throws IOException {
        PlayerState s = new PlayerState();
        s.x = i.readDouble(); s.y = i.readDouble(); s.z = i.readDouble();
        s.px = i.readDouble(); s.py = i.readDouble(); s.pz = i.readDouble();
        s.yaw = i.readFloat(); s.pitch = i.readFloat(); s.pyaw = i.readFloat(); s.ppitch = i.readFloat();
        s.walked = i.readFloat(); s.pwalked = i.readFloat();
        s.camYaw = i.readFloat(); s.pcamYaw = i.readFloat(); s.camPitch = i.readFloat(); s.pcamPitch = i.readFloat();
        s.armYaw = i.readFloat(); s.parmYaw = i.readFloat(); s.armPitch = i.readFloat(); s.parmPitch = i.readFloat();
        s.swing = i.readFloat(); s.pswing = i.readFloat();
        s.swingInt = i.readInt();
        s.swinging = i.readBoolean();
        s.slot = i.readInt();
        s.sprinting = i.readBoolean(); s.sneaking = i.readBoolean(); s.onGround = i.readBoolean();
        s.useCount = i.readInt();
        s.cloud = i.readInt();
        for (int k = 0; k < s.smoothed.length; k++) {
            s.smoothed[k] = i.readFloat();
        }
        if (i.available() >= POSE_BYTES) {
            s.hasPose = true;
            s.limb = i.readFloat(); s.limbAmount = i.readFloat(); s.plimbAmount = i.readFloat();
            s.body = i.readFloat(); s.pbody = i.readFloat(); s.head = i.readFloat(); s.phead = i.readFloat();
            s.chaseX = i.readDouble(); s.chaseY = i.readDouble(); s.chaseZ = i.readDouble();
            s.pchaseX = i.readDouble(); s.pchaseY = i.readDouble(); s.pchaseZ = i.readDouble();
        }
        return s;
    }
}

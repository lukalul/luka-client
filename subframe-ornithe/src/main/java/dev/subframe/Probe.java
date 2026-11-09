package dev.subframe;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.EntityZombie;

/** Debug snapshot of what a frame is about to draw, recorded live and compared in the diff to find where a replay diverges. */
final class Probe {
    static final int SIZE = 25;
    static final String[] NAMES = {"eyeX", "eyeY", "eyeZ", "yaw", "pitch", "entities", "zombies",
            "zX", "zY", "zZ", "zHurt", "worldTime",
            "pHurt", "pDeath", "viewEntityId", "health", "slot", "items", "using",
            "particles", "nearDist", "nearScale", "nearAge", "nearMaxAge", "nearClassHash"};

    private Probe() {
    }

    static double[] capture(float partial) {
        double[] v = new double[SIZE];
        if (!SubFrameMod.captureLive) {
            return v;
        }
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP p = mc.thePlayer;
        if (p == null || mc.theWorld == null) {
            return v;
        }
        v[0] = p.lastTickPosX + (p.posX - p.lastTickPosX) * partial;
        v[1] = p.lastTickPosY + (p.posY - p.lastTickPosY) * partial;
        v[2] = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * partial;
        v[3] = p.prevRotationYaw + (p.rotationYaw - p.prevRotationYaw) * partial;
        v[4] = p.prevRotationPitch + (p.rotationPitch - p.prevRotationPitch) * partial;
        v[5] = mc.theWorld.loadedEntityList.size();
        EntityZombie first = null;
        int zombies = 0;
        for (Object o : mc.theWorld.loadedEntityList) {
            if (o instanceof EntityZombie) {
                zombies++;
                if (first == null) {
                    first = (EntityZombie) o;
                }
            }
        }
        v[6] = zombies;
        if (first != null) {
            Entity z = first;
            v[7] = z.lastTickPosX + (z.posX - z.lastTickPosX) * partial;
            v[8] = z.lastTickPosY + (z.posY - z.lastTickPosY) * partial;
            v[9] = z.lastTickPosZ + (z.posZ - z.lastTickPosZ) * partial;
            v[10] = first.hurtTime;
        }
        v[11] = mc.theWorld.getWorldTime();
        v[12] = p.hurtTime;
        v[13] = p.deathTime;
        v[14] = mc.getRenderViewEntity() == null ? -1 : mc.getRenderViewEntity().getEntityId();
        v[15] = p.getHealth();
        v[16] = p.inventory.currentItem;
        int items = 0;
        for (net.minecraft.item.ItemStack st : p.inventory.mainInventory) {
            if (st != null) {
                items += 1;
            }
        }
        v[17] = items;
        v[18] = p.isUsingItem() ? 1 : 0;
        try {
            java.util.List<?>[][] layers = Reflect.get(
                    net.minecraft.client.particle.EffectRenderer.class, mc.effectRenderer, "fxLayers", "field_78876_b");
            int count = 0;
            double bestD = 1e9;
            double scale = 0, age = 0, maxAge = 0, classHash = 0;
            for (java.util.List<?>[] layer : layers) {
                for (java.util.List<?> l : layer) {
                    for (Object o : l) {
                        net.minecraft.client.particle.EntityFX f = (net.minecraft.client.particle.EntityFX) o;
                        count++;
                        double dx = f.posX - v[0], dy = f.posY - v[1], dz = f.posZ - v[2];
                        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        if (d < bestD) {
                            bestD = d;
                            classHash = f.getClass().getName().hashCode();
                            scale = Reflect.<Float>get(net.minecraft.client.particle.EntityFX.class, f,
                                    "particleScale", "field_70544_f");
                            age = Reflect.<Integer>get(net.minecraft.client.particle.EntityFX.class, f,
                                    "particleAge", "field_70546_d");
                            maxAge = Reflect.<Integer>get(net.minecraft.client.particle.EntityFX.class, f,
                                    "particleMaxAge", "field_70547_e");
                        }
                    }
                }
            }
            v[19] = count;
            v[20] = count == 0 ? 0 : bestD;
            v[21] = scale;
            v[22] = age;
            v[23] = maxAge;
            v[24] = classHash;
        } catch (Throwable ignored) {
            // probe only
        }
        return v;
    }
}

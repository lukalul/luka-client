package dev.subframe;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.MathHelper;
import net.minecraft.world.WorldServer;

/**
 * Self-test fighter: an armoured player with a sword fights armoured zombies, which stand in for players.
 * It aims smoothly every frame, sprint-hits, jumps for crits, strafes and blocks, so the recording has
 * hurt flashes, knockback, crit particles, sword blocking and armoured entities with held items.
 */
final class PvpBot {
    private static final boolean VULNERABLE = Boolean.getBoolean("subframe.pvp.vulnerable");

    private final Minecraft mc = Minecraft.getMinecraft();

    /** The integrated server's copy of the local player (the server's player list is not accessible on Ornithe). */
    private EntityPlayerMP player(IntegratedServer srv) {
        return srv.getConfigurationManager().getPlayerByUsername(mc.getSession().getUsername());
    }
    private int tick;
    private boolean serialiseChecked;

    /** A real server hands the recorder decoded packets; make sure a decoded spawn-mob packet can be written back. */
    private void checkDecodedSpawnPacket(EntityZombie z) {
        try {
            net.minecraft.network.play.server.S0FPacketSpawnMob orig = new net.minecraft.network.play.server.S0FPacketSpawnMob(z);
            io.netty.buffer.ByteBuf b = io.netty.buffer.Unpooled.buffer();
            orig.writePacketData(new net.minecraft.network.PacketBuffer(b));
            byte[] a = new byte[b.readableBytes()];
            b.readBytes(a);
            net.minecraft.network.play.server.S0FPacketSpawnMob decoded = new net.minecraft.network.play.server.S0FPacketSpawnMob();
            decoded.readPacketData(new net.minecraft.network.PacketBuffer(io.netty.buffer.Unpooled.wrappedBuffer(a)));
            byte[] c = Recorder.serialize(decoded);
            SubFrameMod.LOG.info("[pvp] decoded spawn-mob packet written back: ok, same bytes=" + java.util.Arrays.equals(a, c)
                    + " (" + a.length + " vs " + c.length + ")");
        } catch (Throwable t) {
            SubFrameMod.LOG.error("[pvp] decoded spawn-mob packet could NOT be written back", t);
        }
    }

    /** Gives the player gear and a damage buffer on the server thread. */
    void setup() {
        final IntegratedServer srv = mc.getIntegratedServer();
        srv.addScheduledTask(new Runnable() {
            public void run() {
                if ((player(srv) == null)) {
                    return;
                }
                EntityPlayerMP sp = player(srv);
                // the test world is reused between runs and keeps the game mode it was first created with
                sp.setGameType(net.minecraft.world.WorldSettings.GameType.SURVIVAL);
                sp.inventory.mainInventory[0] = new ItemStack(Items.diamond_sword);
                if (!VULNERABLE) {
                    sp.inventory.armorInventory[0] = new ItemStack(Items.diamond_boots);
                    sp.inventory.armorInventory[1] = new ItemStack(Items.diamond_leggings);
                    sp.inventory.armorInventory[2] = new ItemStack(Items.diamond_chestplate);
                    sp.inventory.armorInventory[3] = new ItemStack(Items.diamond_helmet);
                    sp.addPotionEffect(new PotionEffect(Potion.resistance.id, 20 * 600, 2));
                }
                sp.inventoryContainer.detectAndSendChanges();
                // fixed daytime, like the time-changer mods most players use
                WorldServer w = srv.worldServerForDimension(0);
                w.getGameRules().setOrCreateGameRule("doDaylightCycle", "false");
                w.setWorldTime(6000L);
                w.getWorldInfo().setRaining(false);
            }
        });
    }

    private void spawnOpponent() {
        final IntegratedServer srv = mc.getIntegratedServer();
        srv.addScheduledTask(new Runnable() {
            public void run() {
                if ((player(srv) == null)) {
                    return;
                }
                EntityPlayerMP sp = player(srv);
                sp.setHealth(sp.getMaxHealth());
                WorldServer w = srv.worldServerForDimension(0);
                EntityZombie z = new EntityZombie(w);
                double rad = Math.toRadians(sp.rotationYaw);
                z.setLocationAndAngles(sp.posX - Math.sin(rad) * 5, sp.posY, sp.posZ + Math.cos(rad) * 5,
                        sp.rotationYaw + 180f, 0f);
                z.setCurrentItemOrArmor(0, new ItemStack(Items.iron_sword));
                z.setCurrentItemOrArmor(1, new ItemStack(Items.iron_boots));
                z.setCurrentItemOrArmor(2, new ItemStack(Items.iron_leggings));
                z.setCurrentItemOrArmor(3, new ItemStack(Items.iron_chestplate));
                z.setCurrentItemOrArmor(4, new ItemStack(Items.iron_helmet));
                z.enablePersistence();
                w.spawnEntityInWorld(z);
            }
        });
    }

    private EntityZombie nearest() {
        EntityPlayerSP p = mc.thePlayer;
        EntityZombie best = null;
        double bestD = Double.MAX_VALUE;
        for (Object o : mc.theWorld.loadedEntityList) {
            if (o instanceof EntityZombie && !((EntityZombie) o).isDead && ((EntityZombie) o).getHealth() > 0) {
                double d = p.getDistanceSqToEntity((Entity) o);
                if (d < bestD) {
                    bestD = d;
                    best = (EntityZombie) o;
                }
            }
        }
        return best;
    }

    void onTick() {
        EntityPlayerSP p = mc.thePlayer;
        if (p == null || mc.theWorld == null) {
            return;
        }
        tick++;
        if (VULNERABLE && tick % 10 == 0 && p.getHealth() < 8f) {
            final IntegratedServer srv = mc.getIntegratedServer();
            srv.addScheduledTask(new Runnable() {
                public void run() {
                    if (!(player(srv) == null)) {
                        EntityPlayerMP sp = player(srv);
                        sp.setHealth(sp.getMaxHealth());
                    }
                }
            });
        }
        if (VULNERABLE && tick % 40 == 20) {
            // scripted hit on the player from the nearest zombie: hurt animation, camera tilt, knockback
            final IntegratedServer srv = mc.getIntegratedServer();
            srv.addScheduledTask(new Runnable() {
                public void run() {
                    if ((player(srv) == null)) {
                        return;
                    }
                    EntityPlayerMP sp = player(srv);
                    EntityZombie z = null;
                    for (Object o : srv.worldServerForDimension(0).loadedEntityList) {
                        if (o instanceof EntityZombie && ((EntityZombie) o).getHealth() > 0) {
                            z = (EntityZombie) o;
                            break;
                        }
                    }
                    if (z != null) {
                        boolean hit = sp.attackEntityFrom(net.minecraft.util.DamageSource.causeMobDamage(z), 3f);
                        SubFrameMod.LOG.info("[pvp] scripted hit accepted=" + hit + " health=" + sp.getHealth()
                                + " gameType=" + sp.theItemInWorldManager.getGameType() + " difficulty="
                                + srv.getDifficulty());
                    } else {
                        SubFrameMod.LOG.info("[pvp] scripted hit skipped: no zombie");
                    }
                }
            });
        }
        GameSettings gs = mc.gameSettings;
        EntityZombie target = nearest();
        if (target == null) {
            if (tick % 30 == 0) {
                spawnOpponent();
            }
            KeyBinding.setKeyBindState(gs.keyBindForward.getKeyCode(), false);
            return;
        }
        if (!serialiseChecked) {
            serialiseChecked = true;
            checkDecodedSpawnPacket(target);
        }
        double dist = p.getDistanceToEntity(target);
        KeyBinding.setKeyBindState(gs.keyBindForward.getKeyCode(), dist > 2.2);
        KeyBinding.setKeyBindState(gs.keyBindSprint.getKeyCode(), dist > 2.2);
        boolean strafeLeft = (tick / 25) % 2 == 0;
        KeyBinding.setKeyBindState(gs.keyBindLeft.getKeyCode(), dist < 4 && strafeLeft);
        KeyBinding.setKeyBindState(gs.keyBindRight.getKeyCode(), dist < 4 && !strafeLeft);
        KeyBinding.setKeyBindState(gs.keyBindJump.getKeyCode(), tick % 22 < 2 && dist < 5);
        // sword block for a few ticks every second or so
        boolean block = tick % 50 >= 30 && tick % 50 < 42;
        int use = gs.keyBindUseItem.getKeyCode();
        if (block && !gs.keyBindUseItem.isKeyDown()) {
            KeyBinding.setKeyBindState(use, true);
            KeyBinding.onTick(use);
        } else if (!block && gs.keyBindUseItem.isKeyDown()) {
            KeyBinding.setKeyBindState(use, false);
        }
        if (!block && dist < 3.0 && tick % 4 == 0) {
            p.swingItem();
            mc.playerController.attackEntity(p, target);
        }
    }

    /** Called at the start of every rendered frame: turn smoothly towards the opponent, like a mouse would. */
    void aim() {
        EntityPlayerSP p = mc.thePlayer;
        if (p == null || mc.theWorld == null) {
            return;
        }
        EntityZombie t = nearest();
        if (t == null) {
            return;
        }
        double dx = t.posX - p.posX, dz = t.posZ - p.posZ;
        double dy = (t.posY + t.getEyeHeight() * 0.8) - (p.posY + p.getEyeHeight());
        float wantYaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90f;
        float wantPitch = (float) -(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 180.0 / Math.PI);
        float dYaw = MathHelper.wrapAngleTo180_float(wantYaw - p.rotationYaw) * 0.10f;
        float dPitch = (wantPitch - p.rotationPitch) * 0.10f;
        // same bookkeeping as Entity.setAngles, so interpolation stays consistent
        p.rotationYaw += dYaw;
        p.prevRotationYaw += dYaw;
        p.rotationPitch += dPitch;
        p.prevRotationPitch += dPitch;
    }

    void stop() {
        GameSettings gs = mc.gameSettings;
        for (KeyBinding k : new KeyBinding[]{gs.keyBindForward, gs.keyBindSprint, gs.keyBindLeft, gs.keyBindRight,
                gs.keyBindJump, gs.keyBindUseItem}) {
            KeyBinding.setKeyBindState(k.getKeyCode(), false);
        }
    }
}

package dev.subframe;

import dev.subframe.mixin.EntityCrit2FXAccessor;
import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.client.particle.EntityCrit2FX;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

/**
 * Routes every new particle through ParticleState so its random starting values are recorded or restored, and
 * seeds crit emitters. In 1.7.10 an emitter is an ordinary particle (EntityCrit2FX) added with addEffect; it holds its
 * target entity and particle name and spawns a burst of sprites from its own Random over the next ticks.
 */
final class DeterministicEffectRenderer extends EffectRenderer {
    /** The numbers these two particle names have in the emitter records. */
    static final int CRIT = 9, CRIT_MAGIC = 10;

    private int emitTick = Integer.MIN_VALUE;
    private final Map<String, Integer> occurrences = new HashMap<String, Integer>();
    private boolean injecting;

    DeterministicEffectRenderer(World world, TextureManager textures) {
        super(world, textures);
    }

    @Override
    public void addEffect(EntityFX fx) {
        if (fx instanceof EntityCrit2FX) {
            super.addEffect(fx);
            try {
                seedEmitter((EntityCrit2FX) fx);
            } catch (Throwable t) {
                SubFrameMod.problem("addEffect emitter", t);
            }
        } else if (ParticleState.onSpawn(fx)) {
            super.addEffect(fx);
        }
    }

    private void seedEmitter(EntityCrit2FX fx) {
        int tick = ParticleState.currentTick();
        if (tick < 0 || !Determinism.active) {
            return;
        }
        EntityCrit2FXAccessor emitter = (EntityCrit2FXAccessor) fx;
        Entity target = emitter.subframe$getTheEntity();
        int id = target == null ? -1 : target.getEntityId();
        int typeId = "magicCrit".equals(emitter.subframe$getParticleName()) ? CRIT_MAGIC : CRIT;
        if (tick != emitTick) {
            emitTick = tick;
            occurrences.clear();
        }
        String k = id + ":" + typeId;
        Integer n = occurrences.get(k);
        int occurrence = n == null ? 0 : n;
        occurrences.put(k, occurrence + 1);
        // independent of call order, so a replay that makes the same emitters in a different order still matches
        long seed = Determinism.mix(Determinism.mix(Determinism.mix(Determinism.seed, tick), id * 131L + typeId), occurrence);
        ParticleState.reseed(fx, seed);
        if (injecting) {
            return;
        }
        Recorder r = Recorder.active;
        if (r != null) {
            r.emitter(tick, id, typeId);
        }
        Replay p = Replay.active;
        if (p != null) {
            p.emitterSeen(tick, id, typeId);
        }
    }

    /** Adds a particle rebuilt from the recording, bypassing the spawn hook. */
    void injectEffect(EntityFX fx) {
        super.addEffect(fx);
    }

    /** Creates an emitter the live game made locally (from the player's own input) and the replayed game did not. */
    void inject(Entity target, int typeId) {
        injecting = true;
        try {
            addEffect(typeId == CRIT_MAGIC
                    ? new EntityCrit2FX(target.worldObj, target, "magicCrit")
                    : new EntityCrit2FX(target.worldObj, target));
        } finally {
            injecting = false;
        }
    }
}

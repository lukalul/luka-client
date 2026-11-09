package dev.subframe;

import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.particle.EntityParticleEmitter;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Routes every new particle through ParticleState so its random starting values are recorded or restored, and
 * seeds particle emitters (crit sparks and the like), which the game creates without calling addEffect.
 */
final class DeterministicEffectRenderer extends EffectRenderer {
    private static final String[] EMITTERS = {"particleEmitters", "field_178933_d", "f_67293148"};

    private int emitTick = Integer.MIN_VALUE;
    private final Map<String, Integer> occurrences = new HashMap<String, Integer>();
    private boolean injecting;

    DeterministicEffectRenderer(World world, TextureManager textures) {
        super(world, textures);
    }

    @Override
    public void addEffect(EntityFX fx) {
        if (ParticleState.onSpawn(fx)) {
            super.addEffect(fx);
        }
    }

    @Override
    public void emitParticleAtEntity(Entity target, EnumParticleTypes type) {
        super.emitParticleAtEntity(target, type);
        try {
            seedEmitter(target, type);
        } catch (Throwable t) {
            SubFrameMod.problem("emitParticleAtEntity", t);
        }
    }

    private void seedEmitter(Entity target, EnumParticleTypes type) {
        int tick = ParticleState.currentTick();
        if (tick < 0 || !Determinism.active) {
            return;
        }
        int id = target.getEntityId();
        int typeId = type.getParticleID();
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
        List<EntityParticleEmitter> list = Reflect.get(EffectRenderer.class, this, EMITTERS);
        if (!list.isEmpty()) {
            ParticleState.reseed(list.get(list.size() - 1), seed);
        }
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
    void inject(Entity target, EnumParticleTypes type) {
        injecting = true;
        try {
            emitParticleAtEntity(target, type);
        } finally {
            injecting = false;
        }
    }
}

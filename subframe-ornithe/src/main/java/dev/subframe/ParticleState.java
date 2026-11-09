package dev.subframe;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.entity.DataWatcher;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import dev.subframe.mixin.EntityAccessor;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * A particle's constructor draws its velocity, size, colour, lifetime and texture from Math.random(), which
 * cannot be seeded. Instead the finished particle is recorded when it is spawned: its primitive fields and the
 * few references that matter (texture sprite, block position, block state). A replay overwrites the numbers of
 * the particle its own game code just created, and rebuilds from the recording any particle that the replayed
 * game did not create (ones the live game made from the player's own input).
 */
final class ParticleState {
    private static final String[] ENTITY_RAND = {"rand", "field_70146_Z"};
    private static final String[] ENTITY_DATAWATCHER = {"dataWatcher", "field_70180_af"};
    private static final Map<Class<?>, Field[]> FIELDS = new HashMap<Class<?>, Field[]>();
    private static final Map<Class<?>, Field[]> REF_FIELDS = new HashMap<Class<?>, Field[]>();

    /** One recorded particle. */
    static final class Rec {
        String cls;
        int tick;
        long seed;
        double x, y, z;
        String[] names;
        char[] types;
        double[] values;
        String[] refNames;
        char[] refKinds;
        Object[] refValues;
        /** False if the particle holds a reference we cannot record, so it cannot be rebuilt. */
        boolean injectable = true;
    }

    private ParticleState() {
    }

    /** Returns false if the particle must be dropped: a replay-only extra that the live game did not have. */
    static boolean onSpawn(EntityFX fx) {
        try {
            Recorder r = Recorder.active;
            if (r != null) {
                r.particle(fx);
                return true;
            }
            Replay p = Replay.active;
            if (p != null) {
                return p.particle(fx);
            }
        } catch (Throwable t) {
            SubFrameMod.LOG.error("Particle sync failed for " + fx.getClass().getName(), t);
        }
        return true;
    }

    /** The tick counter of whatever is driving the game: the recorder while recording, the replay while replaying. */
    static int currentTick() {
        Recorder r = Recorder.active;
        if (r != null) {
            return r.currentTick();
        }
        Replay p = Replay.active;
        return p != null ? p.currentTick() : -1;
    }

    static int quantise(double v) {
        return (int) Math.floor(v * 1024.0);
    }

    static String key(int tick, EntityFX fx) {
        return tick + "|" + fx.getClass().getName() + "|" + quantise(fx.posX) + "|" + quantise(fx.posY) + "|" + quantise(fx.posZ);
    }

    static void reseed(Entity fx, long seed) {
        Random rnd = ((EntityAccessor) fx).subframe$getRandom();
        rnd.setSeed(seed);
    }

    /** Primitive instance fields of the particle's class chain; from Entity itself only positions, motion and sizes. */
    private static synchronized Field[] fields(Class<?> c) {
        Field[] cached = FIELDS.get(c);
        if (cached != null) {
            return cached;
        }
        List<Field> out = new ArrayList<Field>();
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                int m = f.getModifiers();
                Class<?> t = f.getType();
                if (Modifier.isStatic(m) || Modifier.isFinal(m) || !t.isPrimitive()) {
                    continue;
                }
                if (k == Entity.class && t != double.class && t != float.class) {
                    continue;
                }
                f.setAccessible(true);
                out.add(f);
            }
        }
        cached = out.toArray(new Field[0]);
        FIELDS.put(c, cached);
        return cached;
    }

    /** Reference fields declared below Entity (in EntityFX and the particle subclasses). */
    private static synchronized Field[] refFields(Class<?> c) {
        Field[] cached = REF_FIELDS.get(c);
        if (cached != null) {
            return cached;
        }
        List<Field> out = new ArrayList<Field>();
        for (Class<?> k = c; k != null && k != Entity.class && k != Object.class; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) {
                    continue;
                }
                f.setAccessible(true);
                out.add(f);
            }
        }
        cached = out.toArray(new Field[0]);
        REF_FIELDS.put(c, cached);
        return cached;
    }

    private static char typeOf(Class<?> t) {
        if (t == int.class) {
            return 'I';
        }
        if (t == float.class) {
            return 'F';
        }
        if (t == double.class) {
            return 'D';
        }
        if (t == boolean.class) {
            return 'Z';
        }
        if (t == long.class) {
            return 'J';
        }
        if (t == short.class) {
            return 'S';
        }
        if (t == byte.class) {
            return 'B';
        }
        return 'C';
    }

    static void write(DataOutput o, EntityFX fx) throws IOException {
        Field[] fs = fields(fx.getClass());
        o.writeInt(fs.length);
        try {
            for (Field f : fs) {
                o.writeUTF(f.getDeclaringClass().getName() + "#" + f.getName());
                char type = typeOf(f.getType());
                o.writeChar(type);
                switch (type) {
                    case 'I': o.writeDouble(f.getInt(fx)); break;
                    case 'F': o.writeDouble(f.getFloat(fx)); break;
                    case 'D': o.writeDouble(f.getDouble(fx)); break;
                    case 'Z': o.writeDouble(f.getBoolean(fx) ? 1 : 0); break;
                    case 'J': o.writeDouble(f.getLong(fx)); break;
                    case 'S': o.writeDouble(f.getShort(fx)); break;
                    case 'B': o.writeDouble(f.getByte(fx)); break;
                    default: o.writeDouble(f.getChar(fx)); break;
                }
            }
            Field[] refs = refFields(fx.getClass());
            o.writeInt(refs.length);
            for (Field f : refs) {
                o.writeUTF(f.getDeclaringClass().getName() + "#" + f.getName());
                Object v = f.get(fx);
                if (v == null) {
                    o.writeChar('N');
                } else if (v instanceof BlockPos) {
                    o.writeChar('P');
                    o.writeInt(((BlockPos) v).getX());
                    o.writeInt(((BlockPos) v).getY());
                    o.writeInt(((BlockPos) v).getZ());
                } else if (v instanceof TextureAtlasSprite) {
                    o.writeChar('S');
                    o.writeUTF(((TextureAtlasSprite) v).getIconName());
                } else if (v instanceof net.minecraft.block.state.IBlockState) {
                    o.writeChar('B');
                    o.writeInt(Block.getStateId((net.minecraft.block.state.IBlockState) v));
                } else if (v instanceof Entity) {
                    o.writeChar('E');
                    o.writeInt(((Entity) v).getEntityId());
                } else {
                    o.writeChar('X');
                    o.writeUTF(v.getClass().getName());
                }
            }
        } catch (IllegalAccessException e) {
            throw new IOException(e);
        }
    }

    static Rec read(DataInput i, long seed) throws IOException {
        Rec r = new Rec();
        r.seed = seed;
        int n = i.readInt();
        r.names = new String[n];
        r.types = new char[n];
        r.values = new double[n];
        for (int k = 0; k < n; k++) {
            r.names[k] = i.readUTF();
            r.types[k] = i.readChar();
            r.values[k] = i.readDouble();
        }
        int nr = i.readInt();
        r.refNames = new String[nr];
        r.refKinds = new char[nr];
        r.refValues = new Object[nr];
        for (int k = 0; k < nr; k++) {
            r.refNames[k] = i.readUTF();
            char kind = i.readChar();
            r.refKinds[k] = kind;
            switch (kind) {
                case 'P': r.refValues[k] = new int[]{i.readInt(), i.readInt(), i.readInt()}; break;
                case 'S': r.refValues[k] = i.readUTF(); break;
                case 'B': r.refValues[k] = i.readInt(); break;
                case 'E': r.refValues[k] = i.readInt(); break;
                case 'X': r.refValues[k] = i.readUTF(); r.injectable = false; break;
                default: break;
            }
        }
        return r;
    }

    /** Overwrites the freshly constructed particle's random-derived fields with the recorded ones. */
    static void apply(Rec r, EntityFX fx) throws IllegalAccessException {
        Map<String, Integer> index = new HashMap<String, Integer>();
        for (int k = 0; k < r.names.length; k++) {
            index.put(r.names[k], k);
        }
        for (Field f : fields(fx.getClass())) {
            Integer k = index.get(f.getDeclaringClass().getName() + "#" + f.getName());
            if (k == null) {
                continue;
            }
            double v = r.values[k];
            switch (r.types[k]) {
                case 'I': f.setInt(fx, (int) v); break;
                case 'F': f.setFloat(fx, (float) v); break;
                case 'D': f.setDouble(fx, v); break;
                case 'Z': f.setBoolean(fx, v != 0); break;
                case 'J': f.setLong(fx, (long) v); break;
                case 'S': f.setShort(fx, (short) v); break;
                case 'B': f.setByte(fx, (byte) v); break;
                default: f.setChar(fx, (char) v); break;
            }
        }
    }

    /**
     * Builds a particle from a recording without running its constructor (which needs the arguments and the
     * random numbers the live game used).
     */
    static EntityFX rebuild(Rec r) throws Exception {
        throw new UnsupportedOperationException("Particle reconstruction is not available on the Java 8-safe Ornithe build");
        /*Minecraft mc = Minecraft.getMinecraft();
        Class<?> c = Class.forName(r.cls, true, EntityFX.class.getClassLoader());
        EntityFX fx = (EntityFX) unsafe().allocateInstance(c);
        fx.worldObj = mc.theWorld;
        // what Entity's constructor registers; the movement code reads the flags byte
        DataWatcher watcher = new DataWatcher(fx);
        watcher.addObject(0, Byte.valueOf((byte) 0));
        watcher.addObject(1, Short.valueOf((short) 300));
        watcher.addObject(3, Byte.valueOf((byte) 0));
        watcher.addObject(2, "");
        watcher.addObject(4, Byte.valueOf((byte) 0));
        Reflect.set(Entity.class, fx, watcher, ENTITY_DATAWATCHER);
        Reflect.set(Entity.class, fx, new Random(r.seed), ENTITY_RAND);
        apply(r, fx);
        double hw = fx.width / 2.0;
        fx.setEntityBoundingBox(new AxisAlignedBB(fx.posX - hw, fx.posY, fx.posZ - hw, fx.posX + hw, fx.posY + fx.height, fx.posZ + hw));
        Map<String, Integer> index = new HashMap<String, Integer>();
        for (int k = 0; k < r.refNames.length; k++) {
            index.put(r.refNames[k], k);
        }
        for (Field f : refFields(c)) {
            Integer k = index.get(f.getDeclaringClass().getName() + "#" + f.getName());
            if (k == null) {
                continue;
            }
            Object v = null;
            switch (r.refKinds[k]) {
                case 'P': {
                    int[] p = (int[]) r.refValues[k];
                    v = new BlockPos(p[0], p[1], p[2]);
                    break;
                }
                case 'S':
                    v = mc.getTextureMapBlocks().getAtlasSprite((String) r.refValues[k]);
                    break;
                case 'B':
                    v = Block.getStateById((Integer) r.refValues[k]);
                    break;
                case 'E':
                    v = mc.theWorld.getEntityByID((Integer) r.refValues[k]);
                    break;
                default:
                    break;
            }
            f.set(fx, v);
        }
        return fx;*/
    }
}

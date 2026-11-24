package dev.subframe;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.DataWatcher;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ResourceLocation;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.entity.Entity;
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
 * few references that matter (texture sprite, block). A replay overwrites the numbers of
 * the particle its own game code just created, and rebuilds from the recording any particle that the replayed
 * game did not create (ones the live game made from the player's own input).
 */
final class ParticleState {
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
                } else if (v instanceof TextureAtlasSprite) {
                    o.writeChar('S');
                    o.writeUTF(((TextureAtlasSprite) v).getIconName());
                } else if (v instanceof Block) {
                    o.writeChar('B');
                    o.writeInt(Block.getIdFromBlock((Block) v));
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

    private static Object unsafe;
    private static java.lang.reflect.Method allocateInstance;

    /**
     * A particle object made without running any constructor. sun.misc.Unsafe is reached by reflection: the mod compiles
     * for Java 8 (where javac hides it), and the game runs on Java 8 to 25, which all still have allocateInstance.
     */
    private static synchronized Object allocate(Class<?> c) throws Exception {
        if (allocateInstance == null) {
            Class<?> u = Class.forName("sun.misc.Unsafe");
            Field f = u.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            unsafe = f.get(null);
            allocateInstance = u.getMethod("allocateInstance", Class.class);
        }
        return allocateInstance.invoke(unsafe, c);
    }

    /** Entity.boundingBox is final on 1.7.10 and only its constructor fills it. */
    private static Field boundingBoxField;

    /**
     * Builds a particle from a recording without running its constructor (which needs the arguments and the random
     * numbers the live game used): a blank instance gets what Entity's constructor would register (data watcher,
     * random, bounding box), every recorded number, its position and its recorded references. Used for particles the
     * replayed game does not make by itself: the ones the live game made from the player's own input (own hits), and
     * any whose replayed spawn did not land exactly where the recorded one did.
     */
    static EntityFX rebuild(Rec r) throws Exception {
        Minecraft mc = Minecraft.getMinecraft();
        Class<?> c = Class.forName(r.cls, true, EntityFX.class.getClassLoader());
        EntityFX fx = (EntityFX) allocate(c);
        fx.worldObj = mc.theWorld;
        DataWatcher watcher = new DataWatcher(fx);
        watcher.addObject(0, Byte.valueOf((byte) 0));
        watcher.addObject(1, Short.valueOf((short) 300));
        ((EntityAccessor) fx).subframe$setDataWatcher(watcher);
        ((EntityAccessor) fx).subframe$setRandom(new Random(r.seed));
        synchronized (ParticleState.class) {
            if (boundingBoxField == null) {
                for (Field f : Entity.class.getDeclaredFields()) {
                    if (f.getType() == AxisAlignedBB.class && !Modifier.isStatic(f.getModifiers())) {
                        f.setAccessible(true);
                        boundingBoxField = f;
                        break;
                    }
                }
            }
        }
        boundingBoxField.set(fx, AxisAlignedBB.getBoundingBox(0, 0, 0, 0, 0, 0));
        apply(r, fx);
        fx.setPosition(fx.posX, fx.posY, fx.posZ);
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
                case 'S': {
                    // layer 1 draws from the block atlas, layer 2 from the item atlas
                    ResourceLocation atlas = fx.getFXLayer() == 2 ? TextureMap.locationItemsTexture : TextureMap.locationBlocksTexture;
                    v = ((TextureMap) mc.getTextureManager().getTexture(atlas)).getAtlasSprite((String) r.refValues[k]);
                    break;
                }
                case 'B':
                    v = Block.getBlockById((Integer) r.refValues[k]);
                    break;
                case 'E':
                    v = mc.theWorld.getEntityByID((Integer) r.refValues[k]);
                    break;
                default:
                    break;
            }
            f.set(fx, v);
        }
        return fx;
    }
}

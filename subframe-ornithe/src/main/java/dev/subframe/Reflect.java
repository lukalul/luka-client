package dev.subframe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Field access by name with the Field cached. FML's ReflectionHelper looks the field up again on every call, and in
 * a real (obfuscated) game the readable name never exists, so each call also throws and catches an exception: that
 * was done several times per rendered frame.
 */
final class Reflect {
    private static final ConcurrentHashMap<String, Field> CACHE = new ConcurrentHashMap<String, Field>();

    private Reflect() {
    }

    static Field field(Class<?> c, String... names) {
        String key = c.getName() + "#" + names[0];
        Field f = CACHE.get(key);
        if (f != null) {
            return f;
        }
        NoSuchFieldException last = null;
        for (String n : names) {
            try {
                f = c.getDeclaredField(n);
                f.setAccessible(true);
                CACHE.put(key, f);
                return f;
            } catch (NoSuchFieldException e) {
                last = e;
            }
        }
        throw new IllegalStateException("No field " + java.util.Arrays.toString(names) + " in " + c.getName(), last);
    }

    @SuppressWarnings("unchecked")
    static <T> T get(Class<?> c, Object instance, String... names) {
        try {
            return (T) field(c, names).get(instance);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    static void set(Class<?> c, Object instance, Object value, String... names) {
        try {
            field(c, names).set(instance, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    static Method method(Class<?> c, String[] names, Class<?>... parameterTypes) {
        NoSuchMethodException last = null;
        for (String name : names) {
            try {
                Method method = c.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException e) {
                last = e;
            }
        }
        throw new IllegalStateException("No method " + java.util.Arrays.toString(names) + " in " + c.getName(), last);
    }
}

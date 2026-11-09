package org.polyfrost.oneconfig.utils.v1;

/** Minimal test-runtime stand-in for OneConfig's separate utils jar; never included in CBUI's output. */
public final class WrappingUtils {
    public static Class<?> getUnwrapped(Class<?> type){if(type==Boolean.class)return Boolean.TYPE;if(type==Integer.class)return Integer.TYPE;if(type==Long.class)return Long.TYPE;if(type==Float.class)return Float.TYPE;if(type==Double.class)return Double.TYPE;if(type==Short.class)return Short.TYPE;if(type==Byte.class)return Byte.TYPE;if(type==Character.class)return Character.TYPE;return type;}
    public static Class<?> getWrapped(Class<?> type){if(type==Boolean.TYPE)return Boolean.class;if(type==Integer.TYPE)return Integer.class;if(type==Long.TYPE)return Long.class;if(type==Float.TYPE)return Float.class;if(type==Double.TYPE)return Double.class;if(type==Short.TYPE)return Short.class;if(type==Byte.TYPE)return Byte.class;if(type==Character.TYPE)return Character.class;return type;}
    @SuppressWarnings("unchecked") public static <T> T richCast(Object value,Class<T> type){return (T)value;}
    private WrappingUtils(){ }
}

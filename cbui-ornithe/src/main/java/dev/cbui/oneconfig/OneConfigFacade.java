package dev.cbui.oneconfig;

import java.lang.reflect.Method;

/** Keeps OneConfig classes out of the verifier unless Fabric reports that OneConfig v1 is installed. */
public final class OneConfigFacade {
    private static Boolean present;
    public static boolean loaded() {
        if(present!=null)return present;
        try { Class<?> loader=Class.forName("net.fabricmc.loader.api.FabricLoader");Object instance=loader.getMethod("getInstance").invoke(null);return present=(Boolean)loader.getMethod("isModLoaded",String.class).invoke(instance,"oneconfigv1"); }
        catch(Throwable ignored){present=Boolean.FALSE;return false;}
    }
    public static ConfigSnapshot snapshot() {
        if(!loaded())return ConfigSnapshot.empty();
        try { Class<?> bridge=Class.forName("dev.cbui.oneconfig.ConfigBridge");Method method=bridge.getMethod("snapshot");return (ConfigSnapshot)method.invoke(null); }
        catch(Throwable error){System.err.println("[CBUI] OneConfig bridge unavailable: "+error);return ConfigSnapshot.empty();}
    }
    private OneConfigFacade(){ }
}

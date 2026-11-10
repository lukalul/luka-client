package dev.cbui.render;

import dev.cbui.Cbui;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Optional;

/**
 * Sets the game's Windows process priority. Above normal removes nearly all frames over 4 ms in a crowd, and unlike High it does not
 * starve voice chat or capture software. Goes through java.lang.foreign by reflection because this mod is compiled for Java 8.
 */
public final class GamePriority {
  private static final int NORMAL_PRIORITY_CLASS = 0x20;
  private static final int ABOVE_NORMAL_PRIORITY_CLASS = 0x8000;

  private static boolean initialised;
  private static MethodHandle setPriorityClass;
  private static Object currentProcess;

  private GamePriority() {}

  public static synchronized void apply(boolean aboveNormal) {
    try {
      if (!initialised) {
        initialised = true;
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) init();
      }
      if (setPriorityClass != null) setPriorityClass.invokeWithArguments(currentProcess, aboveNormal ? ABOVE_NORMAL_PRIORITY_CLASS : NORMAL_PRIORITY_CLASS);
    } catch (Throwable error) {
      setPriorityClass = null;
      Cbui.reportOnce("process priority", error);
    }
  }

  private static void init() throws Throwable {
    Class<?> linkerType, arenaType, lookupType, descriptorType, layoutType, valueLayoutType, segmentType, optionType;
    try {
      linkerType = Class.forName("java.lang.foreign.Linker");
    } catch (ClassNotFoundException olderJava) {
      return;
    }
    arenaType = Class.forName("java.lang.foreign.Arena");
    lookupType = Class.forName("java.lang.foreign.SymbolLookup");
    descriptorType = Class.forName("java.lang.foreign.FunctionDescriptor");
    layoutType = Class.forName("java.lang.foreign.MemoryLayout");
    valueLayoutType = Class.forName("java.lang.foreign.ValueLayout");
    segmentType = Class.forName("java.lang.foreign.MemorySegment");
    optionType = Class.forName("java.lang.foreign.Linker$Option");

    Object linker = linkerType.getMethod("nativeLinker").invoke(null);
    Object arena = arenaType.getMethod("global").invoke(null);
    Object kernel32 = lookupType.getMethod("libraryLookup", String.class, arenaType).invoke(null, "kernel32", arena);
    Method find = lookupType.getMethod("find", String.class);
    Object address = valueLayoutType.getField("ADDRESS").get(null);
    Object javaInt = valueLayoutType.getField("JAVA_INT").get(null);
    Method describe = descriptorType.getMethod("of", layoutType, Array.newInstance(layoutType, 0).getClass());
    Object noOptions = Array.newInstance(optionType, 0);
    Method downcall = linkerType.getMethod("downcallHandle", segmentType, descriptorType, noOptions.getClass());

    Object noArguments = Array.newInstance(layoutType, 0);
    Object oneHandleOneInt = Array.newInstance(layoutType, 2);
    Array.set(oneHandleOneInt, 0, address);
    Array.set(oneHandleOneInt, 1, javaInt);

    MethodHandle getCurrentProcess = (MethodHandle) downcall.invoke(linker, ((Optional<?>) find.invoke(kernel32, "GetCurrentProcess")).get(),
        describe.invoke(null, address, noArguments), noOptions);
    setPriorityClass = (MethodHandle) downcall.invoke(linker, ((Optional<?>) find.invoke(kernel32, "SetPriorityClass")).get(),
        describe.invoke(null, javaInt, oneHandleOneInt), noOptions);
    currentProcess = getCurrentProcess.invokeWithArguments();
  }
}

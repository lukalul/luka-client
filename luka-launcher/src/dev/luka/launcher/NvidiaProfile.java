package dev.luka.launcher;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Sets Nvidia's "Vulkan/OpenGL present method" to Prefer native for luka's own Java, which saves a fraction of a millisecond per frame.
 * It works through one driver profile named "luka" that holds only luka's java executable, so other programs are not affected; turning
 * the option off deletes that profile. The native calls need Java 22 or newer, so this runs as a short child process on the game's Java 25.
 */
final class NvidiaProfile {
  private static final String PROFILE = "luka";
  private static final int INIT = 0x0150E828, CREATE_SESSION = 0x0694D52E, LOAD = 0x375DBD6B, SAVE = 0xFCBC7E14, DESTROY_SESSION = 0xDAD9CFF8,
      FIND_PROFILE = 0x7E4A9A0B, FIND_APP = 0xEEE566B2, CREATE_PROFILE = 0xCC176068, DELETE_PROFILE = 0x17093206,
      CREATE_APP = 0x4347A9DE, SET_SETTING = 0x577DD202, GET_SETTING = 0x73BF8338;
  private static final int PRESENT_METHOD = 0x20D690F8, PREFER_NATIVE = 0;
  private static final int PROFILE_NOT_FOUND = -163, EXECUTABLE_NOT_FOUND = -166, BAD_STRUCT_VERSION = -9;
  private static final int NAME_BYTES = 4096, PROFILE_SIZE = 4116, APP_SIZE = 12296;
  /** Drivers built before 64-bit values were added to the setting structure use the smaller layout. */
  private static final int[][] SETTING_LAYOUTS = {{12328, 8224}, {12320, 8220}};

  private NvidiaProfile() {}

  // ---- called by the launcher ----

  /** Applies or removes the setting before the game starts. Failure only means the game runs with the driver's default. */
  static void apply(Settings settings, File java, Consumer<String> log) {
    try {
      if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) return;
      boolean on = settings.nvOptimize();
      if (!on && !settings.nvApplied()) return;
      File jar = new File(NvidiaProfile.class.getProtectionDomain().getCodeSource().getLocation().toURI());
      List<String> command = new ArrayList<>();
      command.add(java.getPath());
      command.add("--enable-native-access=ALL-UNNAMED");
      command.add("-cp");
      command.add(jar.getPath());
      command.add(NvidiaProfile.class.getName());
      command.add(on ? "on" : "off");
      command.add(java.getPath());
      Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
      try (BufferedReader out = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
        for (String line; (line = out.readLine()) != null; ) log.accept("[nvidia] " + line);
      }
      if (!process.waitFor(10, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        return;
      }
      if (process.exitValue() == 0) {
        settings.set("nvApplied", String.valueOf(on));
        settings.save();
      }
    } catch (Throwable error) {
      log.accept("[nvidia] not applied: " + error);
    }
  }

  // ---- the child process: on | off | status, then the java executable ----

  public static void main(String[] args) {
    try {
      String mode = args[0], app = args[1].toLowerCase(Locale.ROOT);
      Native nvapi = new Native();
      check(nvapi.call(INIT), "initialize");
      Object sessionOut = nvapi.memory(8);
      check(nvapi.call(CREATE_SESSION, sessionOut), "session");
      Object session = nvapi.pointerIn(sessionOut);
      try {
        check(nvapi.call(LOAD, session), "load");
        if (mode.equals("status")) status(nvapi, session, app);
        else if (mode.equals("on")) turnOn(nvapi, session, app);
        else if (mode.equals("off")) turnOff(nvapi, session);
        else throw new IllegalArgumentException(mode);
      } finally {
        nvapi.call(DESTROY_SESSION, session);
      }
      System.exit(0);
    } catch (Unavailable none) {
      System.out.println(none.getMessage());
      System.exit(2);
    } catch (Throwable error) {
      System.out.println("failed: " + error);
      System.exit(1);
    }
  }

  private static void turnOn(Native nvapi, Object session, String app) throws Throwable {
    Object out = nvapi.memory(8);
    int found = nvapi.call(FIND_PROFILE, session, nvapi.wide(PROFILE), out);
    if (found == PROFILE_NOT_FOUND) {
      Object info = nvapi.memory(PROFILE_SIZE);
      ByteBuffer fields = nvapi.fields(info, PROFILE_SIZE);
      fields.putInt(0, PROFILE_SIZE | (1 << 16));
      putWide(fields, 4, PROFILE);
      check(nvapi.call(CREATE_PROFILE, session, info, out), "create profile");
    } else {
      check(found, "find profile");
    }
    Object profile = nvapi.pointerIn(out);
    long ours = nvapi.value(out);

    Object owner = nvapi.memory(8), existing = nvapi.memory(APP_SIZE);
    nvapi.fields(existing, APP_SIZE).putInt(0, APP_SIZE | (1 << 16));
    int known = nvapi.call(FIND_APP, session, nvapi.wide(app), owner, existing);
    if (known == 0 && nvapi.value(owner) != ours && app.equals(readWide(nvapi.fields(existing, APP_SIZE), 8))) {
      System.out.println("left alone: this exact Java already belongs to another Nvidia profile");
      return;
    }
    // the driver also matches a plain "javaw.exe" in some other profile; luka's full path is still added to its own
    if (known == EXECUTABLE_NOT_FOUND || (known == 0 && nvapi.value(owner) != ours)) {
      Object info = nvapi.memory(APP_SIZE);
      ByteBuffer fields = nvapi.fields(info, APP_SIZE);
      fields.putInt(0, APP_SIZE | (1 << 16));
      putWide(fields, 8, app);
      check(nvapi.call(CREATE_APP, session, profile, info), "add java to profile");
    } else if (known != 0) {
      check(known, "find java");
    }

    int result = BAD_STRUCT_VERSION;
    for (int[] layout : SETTING_LAYOUTS) {
      Object setting = nvapi.memory(layout[0]);
      ByteBuffer fields = nvapi.fields(setting, layout[0]);
      fields.putInt(0, layout[0] | (1 << 16));
      fields.putInt(4100, PRESENT_METHOD);
      fields.putInt(4104, 0); // a plain number
      fields.putInt(layout[1], PREFER_NATIVE);
      result = nvapi.call(SET_SETTING, session, profile, setting);
      if (result != BAD_STRUCT_VERSION) break;
    }
    check(result, "set present method");
    check(nvapi.call(SAVE, session), "save");
    System.out.println("present method set to Prefer native for luka's Java");
  }

  private static void turnOff(Native nvapi, Object session) throws Throwable {
    Object out = nvapi.memory(8);
    int found = nvapi.call(FIND_PROFILE, session, nvapi.wide(PROFILE), out);
    if (found == PROFILE_NOT_FOUND) return;
    check(found, "find profile");
    check(nvapi.call(DELETE_PROFILE, session, nvapi.pointerIn(out)), "delete profile");
    check(nvapi.call(SAVE, session), "save");
    System.out.println("luka's Nvidia profile removed");
  }

  /** Reads only: says whether the luka profile exists and which setting layout this driver accepts. */
  private static void status(Native nvapi, Object session, String app) throws Throwable {
    Object out = nvapi.memory(8);
    System.out.println("luka profile: " + (nvapi.call(FIND_PROFILE, session, nvapi.wide(PROFILE), out) == 0 ? "present" : "absent"));
    Object base = nvapi.memory(8);
    check(nvapi.call(FIND_PROFILE, session, nvapi.wide("Base Profile"), base), "find base profile");
    for (int[] layout : SETTING_LAYOUTS) {
      Object setting = nvapi.memory(layout[0]);
      nvapi.fields(setting, layout[0]).putInt(0, layout[0] | (1 << 16));
      System.out.println("setting layout " + layout[0] + " answer: " + nvapi.call(GET_SETTING, session, nvapi.pointerIn(base), PRESENT_METHOD, setting));
    }
    Object owner = nvapi.memory(8), existing = nvapi.memory(APP_SIZE);
    nvapi.fields(existing, APP_SIZE).putInt(0, APP_SIZE | (1 << 16));
    System.out.println("java lookup answer: " + nvapi.call(FIND_APP, session, nvapi.wide(app), owner, existing));
  }

  private static void check(int status, String step) {
    if (status != 0) throw new IllegalStateException(step + " returned " + status);
  }

  private static String readWide(ByteBuffer fields, int offset) {
    StringBuilder text = new StringBuilder();
    for (int i = 0; i < 2048; i++) {
      char c = fields.getChar(offset + i * 2);
      if (c == 0) break;
      text.append(c);
    }
    return text.toString().toLowerCase(Locale.ROOT);
  }

  private static void putWide(ByteBuffer fields, int offset, String text) {
    for (int i = 0; i < text.length(); i++) fields.putChar(offset + i * 2, text.charAt(i));
  }

  private static final class Unavailable extends Exception {
    Unavailable(String message) { super(message); }
  }

  /** nvapi64.dll through java.lang.foreign, by reflection because the launcher is compiled for Java 8. */
  private static final class Native {
    private final Object linker, arena, query, intLayout, addressLayout, emptyOptions;
    private final Class<?> linkerType, segmentType, descriptorType, layoutType, optionType;

    Native() throws Throwable {
      Class<?> arenaType, lookupType, valueLayout;
      try {
        linkerType = Class.forName("java.lang.foreign.Linker");
      } catch (ClassNotFoundException old) {
        throw new Unavailable("needs Java 22 or newer");
      }
      arenaType = Class.forName("java.lang.foreign.Arena");
      lookupType = Class.forName("java.lang.foreign.SymbolLookup");
      valueLayout = Class.forName("java.lang.foreign.ValueLayout");
      segmentType = Class.forName("java.lang.foreign.MemorySegment");
      descriptorType = Class.forName("java.lang.foreign.FunctionDescriptor");
      layoutType = Class.forName("java.lang.foreign.MemoryLayout");
      optionType = Class.forName("java.lang.foreign.Linker$Option");
      linker = linkerType.getMethod("nativeLinker").invoke(null);
      arena = arenaType.getMethod("global").invoke(null);
      intLayout = valueLayout.getField("JAVA_INT").get(null);
      addressLayout = valueLayout.getField("ADDRESS").get(null);
      emptyOptions = Array.newInstance(optionType, 0);
      Object lookup;
      try {
        lookup = lookupType.getMethod("libraryLookup", String.class, arenaType).invoke(null, "nvapi64.dll", arena);
      } catch (java.lang.reflect.InvocationTargetException noDriver) {
        throw new Unavailable("no Nvidia driver");
      }
      java.util.Optional<?> symbol = (java.util.Optional<?>) lookupType.getMethod("find", String.class).invoke(lookup, "nvapi_QueryInterface");
      if (!symbol.isPresent()) throw new Unavailable("no Nvidia driver");
      query = handle(symbol.get(), addressLayout, intLayout);
    }

    private Object handle(Object function, Object result, Object... arguments) throws Exception {
      Object layouts = Array.newInstance(layoutType, arguments.length);
      for (int i = 0; i < arguments.length; i++) Array.set(layouts, i, arguments[i]);
      Object descriptor = descriptorType.getMethod("of", layoutType, layouts.getClass()).invoke(null, result, layouts);
      return linkerType.getMethod("downcallHandle", segmentType, descriptorType, emptyOptions.getClass()).invoke(linker, function, descriptor, emptyOptions);
    }

    int call(int id, Object... arguments) throws Throwable {
      Object function = ((MethodHandle) query).invokeWithArguments(id);
      if (((Long) segmentType.getMethod("address").invoke(function)) == 0L) throw new Unavailable("this Nvidia driver lacks function " + Integer.toHexString(id));
      Object[] layouts = new Object[arguments.length];
      for (int i = 0; i < arguments.length; i++) layouts[i] = arguments[i] instanceof Integer ? intLayout : addressLayout;
      return (Integer) ((MethodHandle) handle(function, intLayout, layouts)).invokeWithArguments(arguments);
    }

    Object memory(int bytes) throws Exception {
      return Class.forName("java.lang.foreign.SegmentAllocator").getMethod("allocate", long.class, long.class).invoke(arena, (long) bytes, 8L);
    }

    ByteBuffer fields(Object segment, int bytes) throws Exception {
      return ((ByteBuffer) segmentType.getMethod("asByteBuffer").invoke(segment)).order(ByteOrder.LITTLE_ENDIAN);
    }

    Object wide(String text) throws Exception {
      Object segment = memory(NAME_BYTES);
      putWide(fields(segment, NAME_BYTES), 0, text);
      return segment;
    }

    long value(Object out) throws Exception {
      return fields(out, 8).getLong(0);
    }

    Object pointerIn(Object out) throws Exception {
      return segmentType.getMethod("ofAddress", long.class).invoke(null, value(out));
    }
  }
}

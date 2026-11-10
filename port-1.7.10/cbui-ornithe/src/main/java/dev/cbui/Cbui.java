package dev.cbui;

import dev.cbui.gui.LukaMainMenu;
import dev.cbui.hud.HudManager;
import dev.cbui.config.ConfigStore;
import dev.cbui.gui.ModuleMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;

import java.util.Arrays;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;

import java.util.HashSet;
import java.util.Set;

public final class Cbui implements ClientModInitializer {
    public static final String VERSION = "1.0.0";
    private static final Set<String> REPORTED = new HashSet<>();
    private static boolean vanillaMenuOnce;
    private static HudManager hud;
    private static ConfigStore config;
    private static KeyBinding menuKey, profileKey, cosmeticsKey;
    private static long noticedRulesVersion = Long.MIN_VALUE;

    @Override public void onInitializeClient() {
        System.out.println("[CBUI] Initializing private offline UI");
        try {
            hud = new HudManager();
            config = new ConfigStore(new java.io.File(Minecraft.getMinecraft().mcDataDir,"config/cbui.json"));
            config.load(hud);
        }
        catch (Throwable error) { reportOnce("HUD initialization", error); }
    }

    public static HudManager hud() { return hud; }
    /** The settings object does not exist yet while the mod initializes, so the keys are added on the first tick. */
    private static void ensureKeys() {
        if (menuKey != null) return;
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.gameSettings == null) return;
            KeyBinding menu = new KeyBinding("Open luka module menu", Keyboard.KEY_RSHIFT, "luka");
            KeyBinding profile = new KeyBinding("Toggle FPS profiler", Keyboard.KEY_NONE, "FPS Profiler");
            KeyBinding cosmetics = new KeyBinding("Open luka cosmetics", Keyboard.KEY_NONE, "luka");
            // options.txt was read before these existed, so take their saved codes from it
            java.io.File options = new java.io.File(mc.mcDataDir, "options.txt");
            if (options.isFile()) for (String line : java.nio.file.Files.readAllLines(options.toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
                for (KeyBinding key : new KeyBinding[] {menu, profile, cosmetics}) {
                    String prefix = "key_" + key.getKeyDescription() + ":";
                    if (line.startsWith(prefix)) try { key.setKeyCode(Integer.parseInt(line.substring(prefix.length()).trim())); } catch (NumberFormatException ignored) { }
                }
            }
            KeyBinding[] old = mc.gameSettings.keyBindings;
            KeyBinding[] all = Arrays.copyOf(old, old.length + 3);
            all[old.length] = menu;
            all[old.length + 1] = profile;
            all[old.length + 2] = cosmetics;
            mc.gameSettings.keyBindings = all;
            KeyBinding.resetKeyBindingArrayAndHash();
            menuKey = menu;
            profileKey = profile;
            cosmeticsKey = cosmetics;
        } catch (Throwable error) { reportOnce("key registration", error); menuKey = null; }
    }

    private static boolean priorityApplied;

    public static void tick() { if (!priorityApplied) { priorityApplied = true; dev.cbui.render.GamePriority.apply(dev.cbui.config.GlobalOptions.highPriority); dev.cbui.config.VideoOptions.startup(); } dev.cbui.server.ServerRulesBridge.refresh(); dev.cbui.render.EntityCull.tick(); ensureKeys(); keepOurMenuSafely(); noticeServerRules(); try { dev.cbui.feature.Features.syncAll(); } catch (Throwable error) { reportOnce("feature sync", error); } if (profileKey != null) try { if (dev.cbui.feature.Features.on(dev.cbui.feature.profiler.FpsProfilerModule.class)) Profiler.poll(profileKey.getKeyCode()); } catch (Throwable error) { reportOnce("profiler", error); } if (hud != null) try { hud.tick(); if(menuKey!=null&&menuKey.isPressed()&&!(Minecraft.getMinecraft().currentScreen instanceof ModuleMenu))Minecraft.getMinecraft().displayGuiScreen(new ModuleMenu()); if(cosmeticsKey!=null&&cosmeticsKey.isPressed()&&Minecraft.getMinecraft().currentScreen==null)Minecraft.getMinecraft().displayGuiScreen(new dev.cbui.gui.CosmeticsScreen(null)); } catch(Throwable error) { reportOnce("HUD tick", error); } }

    private static void noticeServerRules() {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.theWorld == null || mc.thePlayer == null) return;
            long version = dev.cbui.server.ServerRulesBridge.version();
            if (version == noticedRulesVersion) return;
            noticedRulesVersion = version;
            java.util.List<String> items = dev.cbui.server.ServerRulesBridge.summary();
            if (!items.isEmpty()) mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText(
                    "\u00a75[luka] \u00a7fThis server restricts: " + String.join(", ", items)));
        } catch (Throwable ignored) { }
    }
    public static void leftClick() { if (hud != null) try { hud.leftClick(); } catch(Throwable error) { reportOnce("CPS click", error); } }
    public static void guiBegin() { if (hud != null) hud.guiBegin(); }
    public static void guiEnd() { if (hud != null) hud.guiEnd(); }
    public static void overlay(float partialTicks) { try { if (Release.COSMETICS) dev.cbui.cosmetics.Capes.tick(); dev.cbui.config.Borderless.tick(); } catch (Throwable ignored) { } Profiler.frame(); Profiler.indicator(); dev.cbui.render.Shapes.captureOverlayState(); if (hud != null) try { hud.render(partialTicks); } catch(Throwable error) { reportOnce("HUD overlay", error); } }
    public static ConfigStore config(){return config;}

    public static GuiScreen replaceScreen(GuiScreen screen) {
        if (!(screen instanceof GuiMainMenu) || screen instanceof LukaMainMenu) return screen;
        if (vanillaMenuOnce) { vanillaMenuOnce = false; deliberateVanilla = screen; return screen; }
        try { return new LukaMainMenu(); }
        catch (Throwable error) { reportOnce("main-menu construction", error); return screen; }
    }

    public static void openVanillaMenuOnce() { vanillaMenuOnce = true; }

    /** The vanilla title screen the user asked for with the VANILLA button; any other vanilla title screen is swapped for ours. */
    private static GuiScreen deliberateVanilla;

    /** Safety net: whatever route produced a vanilla title screen (a mod's Back button, a resize), show ours instead. */
    private static void keepOurMenuSafely() { try { keepOurMenu(); } catch (Throwable error) { reportOnce("menu guard", error); } }

    private static void keepOurMenu() {
        Minecraft mc = Minecraft.getMinecraft();
        GuiScreen cur = mc.currentScreen;
        if (cur instanceof GuiMainMenu && !(cur instanceof LukaMainMenu) && cur != deliberateVanilla && mc.theWorld == null)
            mc.displayGuiScreen(cur);
    }

    public static synchronized void reportOnce(String area, Throwable error) {
        if (!REPORTED.add(area)) return;
        System.err.println("[CBUI] Disabled failing " + area + "; vanilla behavior remains available.");
        error.printStackTrace();
    }

    public Cbui() { }
}

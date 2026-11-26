package dev.cbui.mixin;

import dev.cbui.Cbui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @ModifyVariable(method = "displayGuiScreen", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private GuiScreen cbui$replaceMainMenu(GuiScreen screen) {
        try { GuiScreen replaced = dev.cbui.gui.LukaPauseMenu.swap(Cbui.replaceScreen(screen)); dev.cbui.render.GuiBlur.onScreen(replaced); return replaced; }
        catch (Throwable error) { Cbui.reportOnce("main-menu hook", error); return screen; }
    }

    /** Every left click in the world (vanilla calls this once per button press), so fast clicks are not missed between frames. */
    @org.spongepowered.asm.mixin.injection.Inject(method="clickMouse", at=@At("HEAD"), require=0)
    private void cbui$countClick(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) { Cbui.leftClick(); }

    /**
     * F11 while the Borderless setting is on switches between the borderless window and a normal window (never exclusive fullscreen);
     * the setting itself stays on and is turned off in the luka settings. With the setting off F11 is vanilla fullscreen.
     */
    @org.spongepowered.asm.mixin.injection.Inject(method="toggleFullscreen", at=@At("HEAD"), cancellable=true, require=0)
    private void cbui$f11(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (!dev.cbui.config.GlobalOptions.borderless || dev.cbui.config.Borderless.internalToggle) return;
        if (dev.cbui.config.Borderless.userToggle()) ci.cancel();
    }

    /** Background window: 30 FPS instead of burning the GPU. */
    @org.spongepowered.asm.mixin.injection.Inject(method="getLimitFramerate", at=@At("RETURN"), cancellable=true, require=0)
    private void cbui$unfocusedLimit(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Integer> cir) {
        // not while SubFrame renders a replay (it sets luka.rendering): a render must not slow down when the window is in the background
        if (dev.cbui.config.GlobalOptions.unfocusedLimit && !org.lwjgl.opengl.Display.isActive() && cir.getReturnValue() > 30
                && System.getProperty("luka.rendering") == null) cir.setReturnValue(30);
    }

    /** The window title: "luka" instead of "Minecraft 1.8.9". */
    @org.spongepowered.asm.mixin.injection.Inject(method="createDisplay", at=@At("RETURN"), require=0)
    private void cbui$title(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) { org.lwjgl.opengl.Display.setTitle("luka"); if (dev.cbui.config.GlobalOptions.borderless) dev.cbui.config.Borderless.apply(true); }

    /** Once per frame: the Resolution setting (Video Settings). */
    @org.spongepowered.asm.mixin.injection.Inject(method="runGameLoop", at=@At("HEAD"), require=0)
    private void cbui$resolution(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        // Borderless also from here, so a borderless start waits for the game in the main menu too (the HUD tick only runs in a world)
        if (dev.cbui.config.GlobalOptions.borderless) dev.cbui.config.Borderless.tick();
        dev.cbui.config.Resolution.frame();
        dev.cbui.feature.rawinput.RawInputModule.sync();
    }

    @org.spongepowered.asm.mixin.injection.Inject(method="runTick", at=@At("RETURN"), require=0)
    private void cbui$tick(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) { Cbui.tick(); }

    /** Debug mode (-Dluka.debug=true): one line a second of how the game is doing (report.DebugRecorder). */
    @org.spongepowered.asm.mixin.injection.Inject(method="runGameLoop", at=@At("HEAD"), require=0)
    private void cbui$debugFrame(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) { dev.cbui.report.DebugRecorder.frame(); }
}

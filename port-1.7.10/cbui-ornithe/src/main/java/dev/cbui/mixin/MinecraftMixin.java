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

    /**
     * Vanilla 1.7.10 leaves fullscreen as soon as the window loses focus (a leftover from exclusive fullscreen; 1.8.9 dropped
     * it). On the Vulkan window borderless is the game's own fullscreen, so alt-tab threw the player back into a normal window.
     * With the Borderless setting on that toggle is skipped; F11 and the settings still switch as before.
     */
    @org.spongepowered.asm.mixin.injection.Redirect(method="runGameLoop", at=@At(value="INVOKE", target="Lnet/minecraft/client/Minecraft;toggleFullscreen()V"), require=0)
    private void cbui$keepBorderlessOnFocusLoss(net.minecraft.client.Minecraft mc) {
        if (dev.cbui.config.GlobalOptions.borderless) return;
        mc.toggleFullscreen();
    }

    /** Background window: 30 FPS instead of burning the GPU. */
    @org.spongepowered.asm.mixin.injection.Inject(method="getLimitFramerate", at=@At("RETURN"), cancellable=true, require=0)
    private void cbui$unfocusedLimit(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Integer> cir) {
        if (dev.cbui.config.GlobalOptions.unfocusedLimit && !org.lwjgl.opengl.Display.isActive() && cir.getReturnValue() > 30) cir.setReturnValue(30);
    }

    /** The window title: "luka" instead of "Minecraft 1.8.9". */
    @org.spongepowered.asm.mixin.injection.Inject(method="startGame", at=@At("RETURN"), require=0)
    private void cbui$title(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) { org.lwjgl.opengl.Display.setTitle("luka"); if (dev.cbui.config.GlobalOptions.borderless) dev.cbui.config.Borderless.apply(true); }

    /** Once per frame: the Resolution setting (Video Settings). */
    @org.spongepowered.asm.mixin.injection.Inject(method="runGameLoop", at=@At("HEAD"), require=0)
    private void cbui$resolution(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) { dev.cbui.config.Resolution.frame(); }

    @org.spongepowered.asm.mixin.injection.Inject(method="runTick", at=@At("RETURN"), require=0)
    private void cbui$tick(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) { Cbui.tick(); }
}

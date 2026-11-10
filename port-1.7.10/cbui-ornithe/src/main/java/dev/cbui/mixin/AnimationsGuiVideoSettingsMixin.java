package dev.cbui.mixin;

import dev.cbui.gui.AnimationsScreen;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiVideoSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiVideoSettings.class)
public abstract class AnimationsGuiVideoSettingsMixin extends GuiScreen {
  private static final int CBUI_ANIMATIONS = 19042;

  @Inject(method = "initGui", at = @At("RETURN"), require = 0)
  private void cbui$addAnimationsButton(CallbackInfo ci) {
    buttonList.add(new GuiButton(CBUI_ANIMATIONS, width / 2 - 100, height - 52, 200, 20, "Animations..."));
    for (Object object : buttonList) {
      GuiButton button = (GuiButton) object;
      if (button.id == 200) button.yPosition = height - 28;
    }
  }

  @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$openAnimations(GuiButton button, CallbackInfo ci) {
    if (button.id == CBUI_ANIMATIONS) {
      mc.displayGuiScreen(new AnimationsScreen(this));
      ci.cancel();
    }
  }
}

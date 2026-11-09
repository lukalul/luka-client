package dev.cbui.mixin;

import dev.cbui.gui.VideoSettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Every route to Video Settings ends here, whichever renderer is installed. */
@Mixin(Minecraft.class)
public abstract class VideoSettingsRedirectMixin {
  @ModifyVariable(method = "displayGuiScreen", at = @At("HEAD"), argsOnly = true, require = 0)
  private GuiScreen cbui$vanillaVideoSettings(GuiScreen screen) {
    return VideoSettingsScreen.replace(screen);
  }
}

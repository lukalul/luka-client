package dev.cbui.mixin;

import net.minecraft.client.gui.GuiTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** {@code GuiTextField}'s position is private in 1.7.10 and our screens move their fields around. */
@Mixin(GuiTextField.class)
public interface GuiTextFieldAccessor {
  @Accessor("xPosition") int cbui$getX();
  @Accessor("xPosition") void cbui$setX(int x);
  @Accessor("yPosition") int cbui$getY();
  @Accessor("yPosition") void cbui$setY(int y);
}

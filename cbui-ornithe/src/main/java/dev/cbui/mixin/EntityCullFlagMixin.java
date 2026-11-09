package dev.cbui.mixin;

import dev.cbui.render.EntityCull;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Entity.class)
public abstract class EntityCullFlagMixin implements EntityCull.Flag {
  @Shadow public double posX;
  @Shadow public double posY;
  @Shadow public double posZ;
  @Unique private boolean cbui$culledFlag;
  @Unique private double cbui$checkedX, cbui$checkedY, cbui$checkedZ;

  @Override public boolean cbui$culled() { return cbui$culledFlag; }

  @Override public void cbui$culled(boolean culled) {
    cbui$culledFlag = culled;
    cbui$checkedX = posX;
    cbui$checkedY = posY;
    cbui$checkedZ = posZ;
  }

  @Override public boolean cbui$movedSinceCheck(double limit) {
    return Math.abs(posX - cbui$checkedX) > limit || Math.abs(posY - cbui$checkedY) > limit || Math.abs(posZ - cbui$checkedZ) > limit;
  }
}

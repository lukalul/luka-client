package dev.cbui.mixin;

import net.minecraft.entity.projectile.EntityArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Whether an arrow is lying in a block, for the hide-arrows switches. */
@Mixin(EntityArrow.class)
public interface EntityArrowAccessor {
  @Accessor("inGround")
  boolean cbui$inGround();
}

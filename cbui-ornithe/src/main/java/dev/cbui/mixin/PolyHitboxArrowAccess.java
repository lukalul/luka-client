package dev.cbui.mixin;

import net.minecraft.entity.projectile.EntityArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** PolyHitbox: whether an arrow is stuck in a block (the "Show Grounded Arrows" option). */
@Mixin(EntityArrow.class)
public interface PolyHitboxArrowAccess {
  @Accessor("inGround")
  boolean cbui$inGround();
}

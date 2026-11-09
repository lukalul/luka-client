package dev.hcfopt.mixin;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** {@code setRotation} lives in {@code Entity}, so a shadow on {@code EntityLivingBase} cannot find it; this reaches it through the superclass. */
@Mixin(Entity.class)
public interface EntityAccessor {
    @Invoker("setRotation")
    void hcfopt$setRotation(float yaw, float pitch);
}

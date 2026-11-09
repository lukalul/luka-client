package dev.subframe.mixin;

import dev.subframe.SubFrameMod;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
abstract class WorldMixin {
    @Inject(method = "spawnEntityInWorld", at = @At("HEAD"))
    private void subframe$entityJoined(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        SubFrameMod.onEntityJoined(entity, (World) (Object) this);
    }
}

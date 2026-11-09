package dev.subframe.mixin;

import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityRenderer.class)
public interface EntityRendererAccessor {
    @Accessor("fogColor1") float subframe$getFogColor1();
    @Accessor("fogColor1") void subframe$setFogColor1(float value);
    @Accessor("fogColor2") float subframe$getFogColor2();
    @Accessor("fogColor2") void subframe$setFogColor2(float value);
    @Accessor("fovModifierHand") float subframe$getFovModifierHand();
    @Accessor("fovModifierHand") void subframe$setFovModifierHand(float value);
    @Accessor("fovModifierHandPrev") float subframe$getFovModifierHandPrev();
    @Accessor("fovModifierHandPrev") void subframe$setFovModifierHandPrev(float value);
    @Accessor("bossColorModifier") float subframe$getBossColorModifier();
    @Accessor("bossColorModifier") void subframe$setBossColorModifier(float value);
    @Accessor("bossColorModifierPrev") float subframe$getBossColorModifierPrev();
    @Accessor("bossColorModifierPrev") void subframe$setBossColorModifierPrev(float value);
    @Accessor("torchFlickerX") float subframe$getTorchFlickerX();
    @Accessor("torchFlickerX") void subframe$setTorchFlickerX(float value);
    @Accessor("torchFlickerDX") float subframe$getTorchFlickerDX();
    @Accessor("torchFlickerDX") void subframe$setTorchFlickerDX(float value);
}

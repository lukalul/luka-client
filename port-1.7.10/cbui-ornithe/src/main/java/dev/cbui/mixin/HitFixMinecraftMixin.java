package dev.cbui.mixin;

import dev.cbui.feature.hitfix.HitFixModule;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** HitFix (1.7.10): server packets are applied at the start of every frame and every tick, as on 1.8.9. */
@Mixin(Minecraft.class)
public abstract class HitFixMinecraftMixin {
  @Inject(method = "runGameLoop", at = @At("HEAD"), require = 0)
  private void cbui$hitFixFrame(CallbackInfo ci) {
    try { HitFixModule.applyWaitingPackets(); } catch (Throwable error) { dev.cbui.Cbui.reportOnce("HitFix frame", error); }
  }

  @Inject(method = "runTick", at = @At("HEAD"), require = 0)
  private void cbui$hitFixTick(CallbackInfo ci) {
    try { HitFixModule.applyWaitingPackets(); } catch (Throwable error) { dev.cbui.Cbui.reportOnce("HitFix tick", error); }
  }

  @Inject(method = "runTick", at = @At("RETURN"), require = 0)
  private void cbui$hitFixAnnounce(CallbackInfo ci) {
    try { HitFixModule.releaseHurt(); } catch (Throwable error) { dev.cbui.Cbui.reportOnce("HitFix hurt", error); }
    try { HitFixModule.announce(); } catch (Throwable error) { dev.cbui.Cbui.reportOnce("HitFix announce", error); }
  }
}

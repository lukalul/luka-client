package yt.ren.astatine.mixin.terrain;

import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.game.SignEditScreen;
import net.minecraft.client.render.block.entity.SignRenderer;
import net.minecraft.client.render.model.block.entity.SignModel;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yt.ren.astatine.extension.LidBaked;

/** luka: a baked sign only needs its words drawn. The crack overlay and the sign editor still draw the whole sign. */
@Mixin(SignRenderer.class)
public abstract class SignRendererMixin {
	@Unique private boolean astatine$boardInChunk;

	@Inject(method = "render(Lnet/minecraft/block/entity/SignBlockEntity;DDDFI)V", at = @At("HEAD"), cancellable = true)
	private void astatine$skipEmpty(SignBlockEntity sign, double x, double y, double z, float tickDelta, int destroyStage, CallbackInfo callback) {
		this.astatine$boardInChunk = destroyStage < 0 && ((LidBaked) sign).astatine$baked()
			&& !(Minecraft.getInstance().screen instanceof SignEditScreen);
		if (!this.astatine$boardInChunk) return;
		for (Text line : sign.lines) {
			if (line != null && !line.getString().isEmpty()) return;
		}
		callback.cancel();
	}

	@Redirect(method = "render(Lnet/minecraft/block/entity/SignBlockEntity;DDDFI)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/model/block/entity/SignModel;render()V"))
	private void astatine$board(SignModel model) {
		if (!this.astatine$boardInChunk) model.render();
	}
}

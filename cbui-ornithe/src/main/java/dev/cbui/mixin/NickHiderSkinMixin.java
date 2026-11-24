package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.authlib.GameProfile;
import dev.cbui.feature.nickhider.NickHiderModule;
import java.util.UUID;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Nick Hider's skin switches. The player model, first-person arm and tab list heads all take their skin from the tab
 * list entry, so answering here with the default skin (Steve or Alex, as the game picks for that player) covers them.
 */
@Mixin(NetworkPlayerInfo.class)
public abstract class NickHiderSkinMixin {
  @Shadow public abstract GameProfile getGameProfile();

  @ModifyReturnValue(method = "getLocationSkin", at = @At("RETURN"), require = 0)
  private ResourceLocation cbui$defaultSkin(ResourceLocation skin) {
    UUID id = getGameProfile() == null ? null : getGameProfile().getId();
    return NickHiderModule.hideSkin(id) ? DefaultPlayerSkin.getDefaultSkin(id) : skin;
  }

  @ModifyReturnValue(method = "getSkinType", at = @At("RETURN"), require = 0)
  private String cbui$defaultModel(String type) {
    UUID id = getGameProfile() == null ? null : getGameProfile().getId();
    return NickHiderModule.hideSkin(id) ? DefaultPlayerSkin.getSkinType(id) : type;
  }
}

package dev.cbui.skins;

import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import dev.cbui.Cbui;
import dev.cbui.mixin.skins.SkinsDownloadedImageAccessor;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.util.ResourceLocation;

/** Which players wear a slim ("Alex") skin, and whether a skin texture is in the 64x64 layout. */
public final class PlayerSkins {
  /** Written by the skin loader, read by the renderer; players without an entry use the classic model. */
  private static final Map<UUID, Boolean> SLIM = new ConcurrentHashMap<UUID, Boolean>();

  private PlayerSkins() {
  }

  /** Reads the arm model from the skin's metadata; Mojang marks slim skins with {@code "model": "slim"}. */
  public static void remember(UUID player, MinecraftProfileTexture skin) {
    if (player == null || skin == null) return;
    boolean slim = false;
    try {
      slim = "slim".equals(skin.getMetadata("model"));
    } catch (Throwable error) {
      // An authlib without texture metadata leaves everyone on the classic model.
      Cbui.reportOnce("slim skin metadata", error);
    }
    SLIM.put(player, slim);
  }

  public static boolean isSlim(UUID player) {
    return player != null && Boolean.TRUE.equals(SLIM.get(player));
  }

  /**
   * True only once the downloaded image behind {@code skin} really is 64x64. Until then (still downloading, the default
   * Steve texture, or our conversion failed) the texture is 64x32 and only the vanilla model maps it correctly.
   */
  public static boolean isModern(ResourceLocation skin) {
    if (skin == null) return false;
    ITextureObject texture = Minecraft.getMinecraft().getTextureManager().getTexture(skin);
    if (!(texture instanceof SkinsDownloadedImageAccessor)) return false;
    BufferedImage image = ((SkinsDownloadedImageAccessor) texture).cbui$image();
    return image != null && image.getWidth() == SkinImage.SIZE && image.getHeight() == SkinImage.SIZE;
  }
}

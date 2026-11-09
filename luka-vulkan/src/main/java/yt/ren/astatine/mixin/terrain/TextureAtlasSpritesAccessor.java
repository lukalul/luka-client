package yt.ren.astatine.mixin.terrain;

import java.util.Map;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.render.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TextureAtlas.class)
public interface TextureAtlasSpritesAccessor {
	@Accessor("sourcedSprites")
	Map<String, TextureAtlasSprite> getAstatineSourcedSprites();
}

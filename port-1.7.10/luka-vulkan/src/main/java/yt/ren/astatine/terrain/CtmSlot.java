package yt.ren.astatine.terrain;

/**
 * One spare reference on every Block and block atlas sprite, added by mixin, so connected textures find the rules for
 * a face without a map lookup. Holds a ConnectedTextures.Entry[] or null.
 */
public interface CtmSlot {
	Object astatine$ctm();

	void astatine$setCtm(Object value);
}

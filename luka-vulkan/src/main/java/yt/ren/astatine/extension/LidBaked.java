package yt.ren.astatine.extension;

/** luka: a block entity whose static shape can live in the chunk mesh. */
public interface LidBaked {
	/** The chunk builder reports what it just decided. */
	void astatine$built(boolean baked);

	boolean astatine$baked();
}

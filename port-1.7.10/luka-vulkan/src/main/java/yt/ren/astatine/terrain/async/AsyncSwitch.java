package yt.ren.astatine.terrain.async;

/**
 * luka: whether sections are meshed on worker threads. Read once at startup, before any game class loads, so it
 * holds no references to them. {@code -Dluka.asyncTerrain=false} or {@code ASTATINE_ASYNC_TERRAIN=0} turns it off;
 * then none of the async mixins apply and every section is built on the game thread exactly as before.
 */
public final class AsyncSwitch {
	public static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("luka.asyncTerrain"))
		&& !"0".equals(System.getenv("ASTATINE_ASYNC_TERRAIN"));

	/** Debug: also build a share of the async sections on the game thread and log any difference in the vertices. */
	public static final boolean VERIFY = ENABLED && "true".equalsIgnoreCase(System.getProperty("luka.asyncTerrain.verify"));

	/** Debug, with VERIFY: compare every section instead of one in eight ({@code -Dluka.asyncTerrain.verifyAll=true}). */
	public static final boolean VERIFY_ALL = VERIFY && "true".equalsIgnoreCase(System.getProperty("luka.asyncTerrain.verifyAll"));

	private AsyncSwitch() {
	}
}

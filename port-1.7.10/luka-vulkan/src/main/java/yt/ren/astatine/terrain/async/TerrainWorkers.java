package yt.ren.astatine.terrain.async;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.WorldRegion;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import yt.ren.astatine.terrain.SectionCapture;

/**
 * luka: meshing chunk sections on worker threads (1.7.10). The game thread copies what a section needs into a
 * {@link SectionSnapshot} and queues it; a {@link TerrainWorker} meshes it and hands it to the native mesher, which
 * already takes results from any thread and drops results for an old version of a section. What stays on the game
 * thread: sections the native side marks urgent, the sections around a block the player changes, sections with
 * stairs or piston heads, and any section whose worker build failed (it is built again on the game thread, so the
 * native side always gets a result for what it scheduled).
 *
 * <p>Builds hold the read side of a lock; the game thread takes the write side while the block atlas is stitched
 * (sprites change under the renderer) and when the terrain is cleared or reloaded, which also drops the queue.
 */
public final class TerrainWorkers {
	private static final Logger LOGGER = LogManager.getLogger("Astatine/AsyncTerrain");

	static final class Job {
		final int x, y, z, version, epoch;
		final SectionSnapshot snapshot;
		int cameraX, cameraY, cameraZ;
		int[][] expected;

		Job(int x, int y, int z, int version, int epoch, SectionSnapshot snapshot) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.version = version;
			this.epoch = epoch;
			this.snapshot = snapshot;
		}
	}

	/** A section to build on the game thread after its worker build failed: x, y, z, version. */
	public interface Retry {
		void build(int x, int y, int z, int version);
	}

	private static final LinkedBlockingQueue<Job> QUEUE = new LinkedBlockingQueue<>();
	private static final ConcurrentLinkedQueue<Job> FAILED = new ConcurrentLinkedQueue<>();
	private static final ConcurrentLinkedQueue<SectionSnapshot> SPARE = new ConcurrentLinkedQueue<>();
	private static final ReentrantReadWriteLock LOCK = new ReentrantReadWriteLock();
	private static final AtomicInteger IN_FLIGHT = new AtomicInteger();
	private static final AtomicLong VERIFIED = new AtomicLong();
	private static final AtomicLong MISMATCHED = new AtomicLong();
	private static final AtomicLong OUTSIDE = new AtomicLong();
	private static volatile int epoch;
	private static volatile boolean errorLogged;
	private static List<TerrainWorker> workers;
	private static boolean usable;
	private static boolean decided;
	private static int submitted;

	private TerrainWorkers() {
	}

	/** True when sections can go to workers: switched on and the block bounds rewrite is in place. */
	public static boolean active() {
		if (!decided) {
			decided = true;
			usable = AsyncSwitch.ENABLED && (Object) Blocks.STONE instanceof BoundsAccess;
			if (AsyncSwitch.ENABLED && !usable) LOGGER.warn("Async terrain off: the block bounds mixins did not apply");
			if (usable) start();
		}
		return usable;
	}

	private static void start() {
		int cores = Runtime.getRuntime().availableProcessors();
		int count = Math.max(1, Math.min(Math.min(3, cores - 2), BlockBounds.SLOTS));
		workers = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			TerrainWorker worker = new TerrainWorker(i);
			workers.add(worker);
			worker.start();
		}
		LOGGER.info("Async terrain: {} mesh worker(s){}", count, AsyncSwitch.VERIFY ? ", verifying against the game thread" : "");
	}

	/** How many more sections can be queued now. */
	public static int room() {
		return Math.max(0, workers.size() * 3 - IN_FLIGHT.get());
	}

	/**
	 * Copies the section on the game thread and queues it for a worker; the section's block entities that have a
	 * renderer are added to {@code rendered}. Returns false, with {@code rendered} left empty, when the section is
	 * not queued (empty, or it has a block that must be drawn on the game thread): the caller then builds it there.
	 */
	public static boolean submit(ClientWorld world, int x, int y, int z, int version, List<BlockEntity> rendered) {
		int minX = x << 4, minY = y << 4, minZ = z << 4;
		WorldRegion region = new WorldRegion(world, minX - 1, minY - 1, minZ - 1, minX + 17, minY + 17, minZ + 17, 1);
		if (region.isEmpty()) return false; // nothing to mesh: the game thread's build of it is nearly free
		SectionSnapshot snapshot = SPARE.poll();
		if (snapshot == null) snapshot = new SectionSnapshot();
		if (!snapshot.fill(world, region, x, y, z, rendered)) {
			rendered.clear();
			SPARE.offer(snapshot);
			return false;
		}
		Job job = new Job(x, y, z, version, epoch, snapshot);
		net.minecraft.entity.living.LivingEntity camera = Minecraft.getInstance().camera;
		job.cameraX = MathHelper.floor(camera.x);
		job.cameraY = MathHelper.floor(camera.y);
		job.cameraZ = MathHelper.floor(camera.z);
		if (AsyncSwitch.VERIFY && (AsyncSwitch.VERIFY_ALL || (submitted++ & 7) == 0)) job.expected = SectionCapture.expected(world, x, y, z);
		IN_FLIGHT.incrementAndGet();
		QUEUE.offer(job);
		return true;
	}

	static void loop(TerrainWorker worker) {
		while (true) {
			Job job;
			try {
				job = QUEUE.take();
			} catch (InterruptedException interrupted) {
				continue;
			}
			boolean failed = false;
			LOCK.readLock().lock();
			try {
				if (job.epoch != epoch) continue; // the terrain was cleared since: nothing waits for this section
				try {
					int opaqueCount = worker.mesh(job);
					if (job.expected != null) check(worker, job);
					worker.deliver(job, opaqueCount);
				} catch (SectionSnapshot.Outside outside) {
					OUTSIDE.incrementAndGet();
					failed = true;
				} catch (Throwable error) {
					if (!errorLogged) {
						errorLogged = true;
						LOGGER.error("Async terrain: building section {} {} {} on a worker failed; it and any later failure are built on the game thread", job.x, job.y, job.z, error);
					}
					failed = true;
				}
			} finally {
				LOCK.readLock().unlock();
				// a failed job's snapshot goes back to the pool once the game thread has rebuilt it (retryFailed)
				if (failed) FAILED.offer(job);
				else SPARE.offer(job.snapshot);
				IN_FLIGHT.decrementAndGet();
			}
		}
	}

	private static final AtomicLong NORMAL_ONLY = new AtomicLong();
	private static final java.util.Set<String> REPORTED = java.util.concurrent.ConcurrentHashMap.newKeySet();

	private static void check(TerrainWorker worker, Job job) {
		String difference = worker.difference(job.expected, job);
		long verified = VERIFIED.incrementAndGet();
		if (difference != null) {
			MISMATCHED.incrementAndGet();
			// each kind (layer, field, block) once, the first 20 kinds
			String kind = worker.differenceKind;
			if (REPORTED.size() < 20 && REPORTED.add(kind)) {
				LOGGER.warn("Async terrain verify: section {} {} {}: {}", job.x, job.y, job.z, difference);
			}
		} else if (worker.normalOnly) {
			NORMAL_ONLY.incrementAndGet();
		}
		if ((verified & 63) == 0) {
			LOGGER.info("Async terrain verify: {} sections compared, {} differed, {} differed only in the unused normal int, {} fell back for reading outside the copy",
				verified, MISMATCHED.get(), NORMAL_ONLY.get(), OUTSIDE.get());
		}
	}

	/** On the game thread, each frame: builds the sections whose worker build failed, if they still belong to this terrain. */
	public static void retryFailed(Retry retry) {
		releaseStitchLock();
		Job job;
		while ((job = FAILED.poll()) != null) {
			if (job.epoch == epoch) retry.build(job.x, job.y, job.z, job.version);
			SPARE.offer(job.snapshot);
		}
	}

	/** The terrain is being cleared or reloaded: waits for running builds, then drops everything queued. */
	public static void reset() {
		if (workers == null) return;
		LOCK.writeLock().lock();
		try {
			epoch++;
			Job job;
			while ((job = QUEUE.poll()) != null) {
				IN_FLIGHT.decrementAndGet();
				SPARE.offer(job.snapshot);
			}
			while ((job = FAILED.poll()) != null) SPARE.offer(job.snapshot);
		} finally {
			LOCK.writeLock().unlock();
		}
	}

	/** The block atlas is about to be stitched: no worker may be reading sprites until it is done. */
	public static void pauseForStitch() {
		if (workers == null || LOCK.isWriteLockedByCurrentThread()) return;
		try {
			if (!LOCK.writeLock().tryLock(2, TimeUnit.SECONDS)) LOGGER.warn("Async terrain: workers still busy at an atlas stitch");
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
		}
	}

	public static void resumeAfterStitch() {
		releaseStitchLock();
	}

	private static void releaseStitchLock() {
		while (LOCK.isWriteLockedByCurrentThread()) LOCK.writeLock().unlock();
	}
}

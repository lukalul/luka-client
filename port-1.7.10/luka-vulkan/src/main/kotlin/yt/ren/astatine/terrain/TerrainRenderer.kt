package yt.ren.astatine.terrain

import net.minecraft.block.entity.BlockEntity
import net.minecraft.client.Minecraft
import net.minecraft.client.render.world.RenderChunk
import net.minecraft.client.render.world.WorldRenderer
import net.minecraft.client.world.ClientWorld
import net.minecraft.entity.living.LivingEntity
import net.minecraft.util.math.MathHelper
import org.apache.logging.log4j.LogManager
import yt.ren.astatine.Native
import yt.ren.astatine.memory.LongObjectMap
import yt.ren.astatine.memory.Memory
import yt.ren.astatine.terrain.async.TerrainWorkers
import kotlin.math.abs

/**
 * 1.7.10 terrain. The native side still owns sections, culling, translucent sorting and drawing, but the geometry
 * comes from the game's own block renderer: Minecraft 1.7.10 has no block models to bake, and its blocks can only
 * be rendered by running their render code, which mutates shared block state and so has to stay on the game thread.
 * See [SectionCapture].
 */
object TerrainRenderer {
    private val logger = LogManager.getLogger("Astatine/Terrain")
    private const val UPLOAD_BUDGET = 24L shl 20
    private const val BUDGET_SHARE = 0.25
    private const val MIN_BUDGET_NANOS = 500_000L
    private const val MAX_BUDGET_NANOS = 6_000_000L
    private const val SCHEDULE_ENTRIES = 256
    private const val TRANSLUCENT_SORTS = 15

    private val scheduled = Memory.allocate(SCHEDULE_ENTRIES * 5 * 4L)
    private var tablesReady = false
    private var world: ClientWorld? = null
    private var renderer: WorldRenderer? = null
    private val blockEntities = LongObjectMap<List<BlockEntity>>()
    private val important = LinkedHashSet<Long>()
    private var lastFrameNanos = 0L
    private var frameNanos = 4_000_000.0
    private var buildNanos = 2_000_000.0

    private var frame = 0

    private fun budgetNanos(): Long = (frameNanos * BUDGET_SHARE).toLong().coerceIn(MIN_BUDGET_NANOS, MAX_BUDGET_NANOS)

    @JvmStatic
    fun key(x: Int, y: Int, z: Int): Long =
        ((x.toLong() and 0x3FFFFF) shl 26) or ((z.toLong() and 0x3FFFFF) shl 4) or (y.toLong() and 15)

    @JvmStatic
    fun reload(renderer: WorldRenderer, world: ClientWorld, viewDistance: Int) {
        TerrainWorkers.reset()
        important.clear()
        this.renderer = renderer
        this.world = world
        val started = System.nanoTime()
        if (!tablesReady) {
            sendTables()
            tablesReady = true
        }
        Native.terrainConfigure(viewDistance)
        blockEntities.clear()
        renderer.blockEntitiesToBeRendered.clear()
        logger.info("Terrain reloaded with view distance {} in {} ms", viewDistance, (System.nanoTime() - started) / 1_000_000)
    }

    /** The mesher tables only hold block models; every 1.7.10 mesh arrives as captured geometry, so a table of air is enough. */
    private fun sendTables() {
        val blocks = Memory.allocate(65536 * 16L)
        val models = Memory.allocate(4L)
        val sprites = Memory.allocate(16 * 4L)
        try {
            Memory.fill(blocks, 65536 * 16L, 0)
            Memory.putInt(models, 0)
            Memory.fill(sprites, 16 * 4L, 0)
            val minecraft = Minecraft.getInstance()
            Native.terrainTables(blocks, models, 1, sprites, true, false, minecraft.world?.dimension?.isNatural() != false,
                0L, 0.2f, true, false, 0L, 0)
        } finally {
            Memory.free(blocks)
            Memory.free(models)
            Memory.free(sprites)
        }
    }

    private const val DIRTY_KEPT = 8
    private val dirtyFrom = LongArray(DIRTY_KEPT) { Long.MIN_VALUE }
    private val dirtyTo = LongArray(DIRTY_KEPT) { Long.MIN_VALUE }
    private var dirtyNext = 0

    private fun dirtyKey(x: Int, y: Int, z: Int, important: Boolean): Long =
        (((x shr 4).toLong() and 0x3FFFFF) shl 30) or (((z shr 4).toLong() and 0x3FFFFF) shl 8) or
            (((y shr 4).toLong() and 0x7F) shl 1) or (if (important) 1L else 0L)

    /**
     * Marks the sections a box of blocks touches as needing a rebuild. A burst of light or block changes names the same
     * few sections over and over, so a box covering the same sections as one already sent since the last frame's
     * scheduling is not sent again. Sections are only handed out for building in [setup], which forgets what was sent.
     */
    @JvmStatic
    fun markDirty(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int, important: Boolean) {
        val from = dirtyKey(minX, minY, minZ, important)
        val to = dirtyKey(maxX, maxY, maxZ, important)
        for (i in 0 until DIRTY_KEPT) {
            if (dirtyFrom[i] == from && dirtyTo[i] == to) return
        }
        dirtyFrom[dirtyNext] = from
        dirtyTo[dirtyNext] = to
        dirtyNext = (dirtyNext + 1) % DIRTY_KEPT
        Native.terrainMarkDirty(minX, minY, minZ, maxX, maxY, maxZ, important)
    }

    private fun forgetDirty() {
        dirtyFrom.fill(Long.MIN_VALUE)
        dirtyTo.fill(Long.MIN_VALUE)
    }

    @JvmStatic
    fun clear() {
        forgetDirty()
        important.clear()
        TerrainWorkers.reset()
        Native.terrainClear()
        blockEntities.clear()
        world = null
    }

    @JvmStatic
    fun setup(camera: LivingEntity, tickDelta: Double) {
        val world = world ?: return
        forgetDirty()
        val now = System.nanoTime()
        if (lastFrameNanos != 0L) frameNanos += ((now - lastFrameNanos).coerceAtMost(100_000_000L) - frameNanos) * 0.05
        lastFrameNanos = now
        val x = camera.prevX + (camera.x - camera.prevX) * tickDelta
        val y = camera.prevY + (camera.y - camera.prevY) * tickDelta
        val z = camera.prevZ + (camera.z - camera.prevZ) * tickDelta
        val eyeHeight = camera.eyeHeight.toDouble()
        Native.terrainSetup(x, y, z, eyeHeight, frame++, lookFace(camera, tickDelta), true, false, false, true)
        buildImportant(world, x, y + eyeHeight, z)
        schedule(world)
    }

    private var playerChanges = 0

    @JvmStatic
    fun beginPlayerChange() {
        playerChanges++
    }

    @JvmStatic
    fun endPlayerChange() {
        if (playerChanges > 0) playerChanges--
    }

    @JvmStatic
    fun markImportant(x: Int, y: Int, z: Int) {
        if (playerChanges == 0) return
        for (sx in ((x - 1) shr 4)..((x + 1) shr 4)) {
            for (sz in ((z - 1) shr 4)..((z + 1) shr 4)) {
                for (sy in ((y - 1) shr 4)..((y + 1) shr 4)) {
                    if (sy in 0..15) important += key(sx, sy, sz)
                }
            }
        }
    }

    private fun buildImportant(world: ClientWorld, x: Double, y: Double, z: Double) {
        if (important.isEmpty()) return
        val cameraX = MathHelper.floor(x) shr 4
        val cameraY = MathHelper.floor(y) shr 4
        val cameraZ = MathHelper.floor(z) shr 4
        var built = false
        for (key in important) {
            val sectionX = (key shr 26).toInt() shl 10 shr 10
            val sectionZ = ((key shr 4) and 0x3FFFFF).toInt() shl 10 shr 10
            val sectionY = (key and 15).toInt()
            if (abs(sectionX - cameraX) > 1 || abs(sectionY - cameraY) > 1 || abs(sectionZ - cameraZ) > 1) continue
            buildSection(world, sectionX, sectionY, sectionZ, 0, true)
            built = true
        }
        important.clear()
        if (built) upload()
    }

    private fun lookFace(camera: LivingEntity, tickDelta: Double): Int {
        var pitch = (camera.lastPitch + (camera.pitch - camera.lastPitch) * tickDelta).toFloat()
        val yaw = (camera.lastYaw + (camera.yaw - camera.lastYaw) * tickDelta).toFloat()
        if (Minecraft.getInstance().options.perspective == 2) pitch += 180.0f
        val h = MathHelper.cos(-yaw * (Math.PI.toFloat() / 180.0f) - Math.PI.toFloat())
        val i = MathHelper.sin(-yaw * (Math.PI.toFloat() / 180.0f) - Math.PI.toFloat())
        val j = -MathHelper.cos(-pitch * (Math.PI.toFloat() / 180.0f))
        val k = MathHelper.sin(-pitch * (Math.PI.toFloat() / 180.0f))
        val fx = i * j
        val fy = k
        val fz = h * j
        // nearest face of the forward vector: 0 down, 1 up, 2 north, 3 south, 4 west, 5 east; the mesher wants the one looking back
        val face = when {
            abs(fy) >= abs(fx) && abs(fy) >= abs(fz) -> if (fy < 0) 0 else 1
            abs(fz) >= abs(fx) -> if (fz < 0) 2 else 3
            else -> if (fx < 0) 4 else 5
        }
        return face xor 1
    }

    private fun schedule(world: ClientWorld) {
        if (TerrainWorkers.active()) {
            scheduleAsync(world)
            return
        }
        val affordable = (budgetNanos() / buildNanos).toInt().coerceAtLeast(1)
        val wanted = affordable.coerceAtMost(SCHEDULE_ENTRIES)
        val count = Native.terrainSchedule(scheduled, wanted)
        var urgent = false
        val deadline = System.nanoTime() + budgetNanos()
        for (i in 0 until count) {
            val at = scheduled + i * 20L
            val x = Memory.getInt(at)
            val y = Memory.getInt(at + 4)
            val z = Memory.getInt(at + 8)
            val version = Memory.getInt(at + 12)
            val now = Memory.getInt(at + 16) != 0
            val started = System.nanoTime()
            if (!now && i > 0 && started >= deadline) {
                Native.terrainAbort(x, y, z, version)
                continue
            }
            buildSection(world, x, y, z, version, false)
            buildNanos += ((System.nanoTime() - started) - buildNanos) * 0.1
            urgent = urgent || now
        }
        if (urgent) upload()
    }

    private var snapshotNanos = 100_000.0

    /**
     * luka, async terrain: sections the native side marks urgent are built here as before; the others are copied and
     * queued for the mesh workers (TerrainWorkers) as long as they have room and the copies fit the frame's budget.
     * Sections that cannot go to a worker are built here; what is left is handed back and scheduled again.
     */
    private fun scheduleAsync(world: ClientWorld) {
        TerrainWorkers.retryFailed { x, y, z, version -> buildSection(world, x, y, z, version, false) }
        val room = TerrainWorkers.room()
        val affordable = (budgetNanos() / snapshotNanos).toInt().coerceAtLeast(1)
        val wanted = (room.coerceAtMost(affordable) + 1).coerceAtMost(SCHEDULE_ENTRIES)
        val count = Native.terrainSchedule(scheduled, wanted)
        var urgent = false
        var queued = 0
        val deadline = System.nanoTime() + budgetNanos()
        for (i in 0 until count) {
            val at = scheduled + i * 20L
            val x = Memory.getInt(at)
            val y = Memory.getInt(at + 4)
            val z = Memory.getInt(at + 8)
            val version = Memory.getInt(at + 12)
            val now = Memory.getInt(at + 16) != 0
            val started = System.nanoTime()
            if (now) {
                buildSection(world, x, y, z, version, false)
                buildNanos += ((System.nanoTime() - started) - buildNanos) * 0.1
                urgent = true
                continue
            }
            if (queued >= room || (i > 0 && started >= deadline)) {
                Native.terrainAbort(x, y, z, version)
                continue
            }
            val entities = ArrayList<BlockEntity>()
            val sent = try {
                TerrainWorkers.submit(world, x, y, z, version, entities)
            } catch (throwable: Throwable) {
                logger.error("Unable to copy section {} {} {}", x, y, z, throwable)
                false
            }
            if (sent) {
                queued++
                trackBlockEntities(x, y, z, entities)
                RenderChunk.updateCounter++
                snapshotNanos += ((System.nanoTime() - started) - snapshotNanos) * 0.1
            } else {
                buildSection(world, x, y, z, version, false)
            }
        }
        if (urgent) upload()
    }

    private fun trackBlockEntities(x: Int, y: Int, z: Int, entities: List<BlockEntity>) {
        val renderer = renderer
        val key = key(x, y, z)
        val previous = blockEntities[key] ?: emptyList()
        if (renderer != null && (previous.isNotEmpty() || entities.isNotEmpty())) {
            val added = entities.filter { it !in previous }
            val removed = previous.filter { it !in entities }
            renderer.blockEntitiesToBeRendered.removeAll(removed)
            renderer.blockEntitiesToBeRendered.addAll(added)
        }
        if (entities.isEmpty()) blockEntities.remove(key) else blockEntities[key] = entities
    }

    private fun buildSection(world: ClientWorld, x: Int, y: Int, z: Int, version: Int, urgent: Boolean) {
        try {
            val entities = SectionCapture.capture(world, x, y, z, version, urgent)
            trackBlockEntities(x, y, z, entities)
            RenderChunk.updateCounter++
        } catch (throwable: Throwable) {
            logger.error("Unable to build section {} {} {}", x, y, z, throwable)
            Native.terrainAbort(x, y, z, version)
        }
    }

    private fun upload() = Native.terrainUpload(UPLOAD_BUDGET, budgetNanos())

    /** Called where vanilla compiles dirty chunks; the actual meshing already happened in [setup]. */
    @JvmStatic
    fun accept() {
        upload()
    }

    @JvmStatic
    fun sortTranslucent() {
        Native.terrainSort(TRANSLUCENT_SORTS)
    }

    @JvmStatic
    fun drawOpaqueLayers(): Int = Native.terrainDraw(LAYER_SOLID) + Native.terrainDraw(LAYER_CUTOUT)

    @JvmStatic
    fun drawTranslucent(): Int = Native.terrainDraw(LAYER_TRANSLUCENT)

    @JvmStatic
    fun debug(): String = Native.terrainDebug()

    const val LAYER_SOLID = 0
    const val LAYER_CUTOUT = 1
    const val LAYER_TRANSLUCENT = 3
}

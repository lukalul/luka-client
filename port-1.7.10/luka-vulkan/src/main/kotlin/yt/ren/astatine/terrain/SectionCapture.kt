package yt.ren.astatine.terrain

import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.block.entity.BlockEntity
import net.minecraft.block.material.Material
import net.minecraft.client.Minecraft
import net.minecraft.client.render.block.BlockRenderer
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher
import net.minecraft.client.render.vertex.Tesselator
import net.minecraft.client.world.ClientWorld
import net.minecraft.util.math.MathHelper
import net.minecraft.world.WorldRegion
import yt.ren.astatine.Native
import yt.ren.astatine.memory.Memory

/**
 * Meshes one chunk section by running the game's block renderer into the Tesselator, the way 1.7.10's RenderChunk
 * does, except that while [active] is set the mixin on Tesselator.end() hands the finished vertices to [append]
 * instead of drawing them. The records go to the native mesher unchanged (see Terrain::buildCaptured), which packs
 * them into its vertex format.
 *
 * Runs on the game thread only: the block renderer calls Block.updateShape and other methods that write shared block
 * fields, so there is no safe way to do this on a worker without replacing all of that code.
 */
object SectionCapture {
    private const val LAYERS = 4
    private const val VERTEX_BYTES = 32L

    private val capacity = LongArray(LAYERS) { 1L shl 20 }
    private val sinks = LongArray(LAYERS) { Memory.allocate(capacity[it]) }
    private val written = LongArray(LAYERS)

    private val addresses = Memory.allocate(LAYERS * 8L)
    private val counts = Memory.allocate(LAYERS * 4L)
    private val opaque = Memory.allocate(512L)

    @JvmField
    var active = false

    private var currentLayer = TerrainRenderer.LAYER_SOLID

    /** Called from the Tesselator.end() mixin, with the vanilla int[] vertex buffer. */
    @JvmStatic
    fun append(vertices: IntArray, ints: Int) {
        if (ints == 0) return
        val layer = currentLayer
        val bytes = ints * 4L
        val needed = written[layer] + bytes
        if (needed > capacity[layer]) {
            var size = capacity[layer]
            while (needed > size) size *= 2
            val larger = Memory.allocate(size)
            Memory.copy(sinks[layer], larger, written[layer])
            Memory.free(sinks[layer])
            sinks[layer] = larger
            capacity[layer] = size
        }
        Memory.copy(vertices, 0, sinks[layer] + written[layer], ints)
        written[layer] = needed
    }

    fun capture(world: ClientWorld, x: Int, y: Int, z: Int, version: Int, urgent: Boolean): List<BlockEntity> {
        written.fill(0L)
        Memory.fill(opaque, 512L, 0)
        val entities = ArrayList<BlockEntity>()
        val minX = x shl 4
        val minY = y shl 4
        val minZ = z shl 4
        val region = WorldRegion(world, minX - 1, minY - 1, minZ - 1, minX + 17, minY + 17, minZ + 17, 1)
        var opaqueCount = 0
        if (!region.isEmpty) {
            val renderer = BlockRenderer(region)
            val camera = Minecraft.getInstance().camera
            val cameraX = MathHelper.floor(camera.x)
            val cameraY = MathHelper.floor(camera.y)
            val cameraZ = MathHelper.floor(camera.z)
            val tesselator = Tesselator.INSTANCE
            tesselator.begin()
            tesselator.offset(-minX.toDouble(), -minY.toDouble(), -minZ.toDouble())
            currentLayer = TerrainRenderer.LAYER_SOLID
            active = true
            try {
                for (pass in 0 until 2) {
                    var more = false
                    for (by in minY until minY + 16) {
                        for (bz in minZ until minZ + 16) {
                            for (bx in minX until minX + 16) {
                                val block = region.getBlock(bx, by, bz)
                                if (block.material == Material.AIR) continue
                                if (pass == 0) {
                                    val index = (by - minY shl 8) or (bz - minZ shl 4) or (bx - minX)
                                    if (block.isOpaque) {
                                        val word = index shr 6
                                        val at = opaque + word * 8L
                                        Memory.putLong(at, Memory.getLong(at) or (1L shl (index and 63)))
                                        opaqueCount++
                                    }
                                    if (block.hasBlockEntity()) {
                                        val entity = region.getBlockEntity(bx, by, bz)
                                        if (entity != null && BlockEntityRenderDispatcher.INSTANCE.hasRenderer(entity)) entities += entity
                                    }
                                }
                                val layer = block.renderLayer
                                if (layer > pass) {
                                    more = true
                                    continue
                                }
                                if (layer != pass) continue
                                select(tesselator, target(block, pass), minX, minY, minZ)
                                renderer.tesselateInWorld(block, bx, by, bz)
                                if (block.renderType == 0 && bx == cameraX && by == cameraY && bz == cameraZ) {
                                    renderer.setFlipDepth(true)
                                    renderer.setRenderFaceAlways(true)
                                    renderer.tesselateInWorld(block, bx, by, bz)
                                    renderer.setFlipDepth(false)
                                    renderer.setRenderFaceAlways(false)
                                }
                            }
                        }
                    }
                    if (!more) break
                }
            } finally {
                tesselator.end()
                tesselator.offset(0.0, 0.0, 0.0)
                active = false
            }
        }
        val sequence = if (urgent) Native.terrainDirtySequence() else 0L
        for (layer in 0 until LAYERS) {
            Memory.putLong(addresses + layer * 8L, sinks[layer])
            Memory.putInt(counts + layer * 4L, (written[layer] / VERTEX_BYTES).toInt())
        }
        Native.terrainBuildCaptured(x, y, z, version, urgent, sequence, addresses, counts, opaque, opaqueCount)
        return entities
    }

    /** Pass 0 blocks are opaque cubes or alpha tested; 1.7.10 has no per-block layer beyond the two render passes. */
    private fun target(block: Block, pass: Int): Int = when {
        pass == 1 -> TerrainRenderer.LAYER_TRANSLUCENT
        block.isOpaque && block.renderType == 0 && block !== Blocks.GRASS -> TerrainRenderer.LAYER_SOLID
        else -> TerrainRenderer.LAYER_CUTOUT
    }

    private fun select(tesselator: Tesselator, layer: Int, minX: Int, minY: Int, minZ: Int) {
        if (layer == currentLayer) return
        tesselator.end()
        currentLayer = layer
        tesselator.begin()
        tesselator.offset(-minX.toDouble(), -minY.toDouble(), -minZ.toDouble())
    }
}

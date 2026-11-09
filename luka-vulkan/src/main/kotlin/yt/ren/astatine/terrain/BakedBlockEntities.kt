package yt.ren.astatine.terrain

import net.minecraft.block.Block
import net.minecraft.block.ChestBlock
import net.minecraft.block.EnderChestBlock
import net.minecraft.block.StandingSignBlock
import net.minecraft.block.WallSignBlock
import net.minecraft.block.entity.BlockEntity
import net.minecraft.block.entity.ChestBlockEntity
import net.minecraft.block.entity.EnderChestBlockEntity
import net.minecraft.block.entity.SignBlockEntity
import net.minecraft.block.state.BlockState
import net.minecraft.client.render.model.ModelPart
import net.minecraft.client.render.model.block.BakedModel
import net.minecraft.client.render.model.block.BakedQuad
import net.minecraft.client.render.model.block.entity.ChestModel
import net.minecraft.client.render.model.block.entity.LargeChestModel
import net.minecraft.client.render.model.block.entity.SignModel
import net.minecraft.client.render.texture.TextureAtlas
import net.minecraft.client.render.texture.TextureAtlasSprite
import net.minecraft.client.resource.metadata.AnimationMetadata
import net.minecraft.client.resource.model.ModelTransformations
import net.minecraft.resource.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.world.WorldView
import yt.ren.astatine.extension.LidBaked
import yt.ren.astatine.mixin.model.BoxAccessor
import yt.ren.astatine.mixin.terrain.TextureAtlasSpritesAccessor
import yt.ren.astatine.shaderpack.Shaderpacks
import java.awt.image.BufferedImage
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * luka: chests and signs drawn as part of the chunk instead of one by one every frame, built from the game's own models so they look the same.
 * A chest goes back to the normal renderer only while its lid is moving. ASTATINE_BAKE_BLOCK_ENTITIES=off turns this off for comparisons.
 */
object BakedBlockEntities {
    private const val CHEST = 0
    private const val TRAPPED = 1
    private const val ENDER = 2
    private const val SIGN = 3
    private val TEXTURES = arrayOf("entity/chest/normal", "entity/chest/trapped", "entity/chest/ender", "entity/sign")
    private val DOUBLE_TEXTURES = arrayOf("entity/chest/normal_double", "entity/chest/trapped_double")
    private val FACINGS = intArrayOf(2, 3, 4, 5)

    private const val NOT_BAKED = -1
    private const val DRAW_NOTHING = -2

    val enabled: Boolean = !"off".equals(System.getenv("ASTATINE_BAKE_BLOCK_ENTITIES"), ignoreCase = true)

    private val christmas: Boolean = Calendar.getInstance().let {
        it.get(Calendar.MONTH) + 1 == 12 && it.get(Calendar.DATE) in 24..26
    }

    /** single[type][facing-2], paired[type][facing-2][0 east, 1 south], signs[0..15 standing, 16..19 wall facing 2..5] */
    private var single = Array(3) { IntArray(4) { NOT_BAKED } }
    private var paired = Array(2) { Array(4) { IntArray(2) { NOT_BAKED } } }
    private var signs = IntArray(20) { NOT_BAKED }

    @JvmStatic
    fun active(): Boolean = enabled && !Shaderpacks.isActive() && !christmas

    /** Puts the entity textures in the block atlas, padded square so the stitcher accepts the wide double chest and sign images. */
    @JvmStatic
    fun registerSprites(atlas: TextureAtlas) {
        if (!enabled) return
        val sprites = (atlas as TextureAtlasSpritesAccessor).astatineSourcedSprites
        for (name in TEXTURES + DOUBLE_TEXTURES) sprites.putIfAbsent("minecraft:$name", PaddedSprite("minecraft:$name"))
    }

    private class PaddedSprite(name: String) : TextureAtlasSprite(name) {
        @JvmField var uScale = 1.0f
        @JvmField var vScale = 1.0f

        override fun load(images: Array<BufferedImage?>, metadata: AnimationMetadata?) {
            val source = images[0]!!
            val side = max(source.width, source.height)
            uScale = source.width.toFloat() / side
            vScale = source.height.toFloat() / side
            if (source.width != source.height) {
                val square = BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB)
                val graphics = square.createGraphics()
                graphics.drawImage(source, 0, 0, null)
                graphics.dispose()
                images[0] = square
            }
            super.load(images, null) // an animation block on these would be meaningless
        }
    }

    /** Called while the model table is built; [id] gives each new model its number. */
    fun bake(atlas: TextureAtlas, id: (BakedModel) -> Int) {
        val chest = ChestModel()
        val large = LargeChestModel()
        val sign = SignModel()
        val chestParts = listOf(chest.lid, chest.lock, chest.base)
        val largeParts = listOf(large.lid, large.lock, large.base)
        val newSingle = Array(3) { IntArray(4) { NOT_BAKED } }
        val newPaired = Array(2) { Array(4) { IntArray(2) { NOT_BAKED } } }
        val newSigns = IntArray(20) { NOT_BAKED }
        for (type in CHEST..ENDER) {
            val sprite = sprite(atlas, TEXTURES[type]) ?: continue
            for (f in 0 until 4) newSingle[type][f] = id(model(chestParts, sprite, chestTransform(FACINGS[f], east = false, south = false)))
        }
        for (type in CHEST..TRAPPED) {
            val sprite = sprite(atlas, DOUBLE_TEXTURES[type]) ?: continue
            for (f in 0 until 4) {
                newPaired[type][f][0] = id(model(largeParts, sprite, chestTransform(FACINGS[f], east = true, south = false)))
                newPaired[type][f][1] = id(model(largeParts, sprite, chestTransform(FACINGS[f], east = false, south = true)))
            }
        }
        sprite(atlas, TEXTURES[SIGN])?.let { sprite ->
            val standing = listOf(sign.board, sign.pole)
            val wall = listOf(sign.board)
            for (rotation in 0 until 16) newSigns[rotation] = id(model(standing, sprite, signTransform(-(rotation * 360f / 16f), wall = false)))
            for (f in 0 until 4) {
                val angle = when (FACINGS[f]) { 2 -> 180f; 4 -> 90f; 5 -> -90f; else -> 0f }
                newSigns[16 + f] = id(model(wall, sprite, signTransform(-angle, wall = true)))
            }
        }
        single = newSingle
        paired = newPaired
        signs = newSigns
    }

    private fun sprite(atlas: TextureAtlas, name: String): PaddedSprite? = atlas.getSprite("minecraft:$name") as? PaddedSprite

    /** A chest or sign block always draws through a cutout pass so packs with see-through chests keep working. */
    fun bakeable(block: Block): Boolean = enabled && (block is ChestBlock || block is EnderChestBlock || block is StandingSignBlock || block is WallSignBlock)

    /** Returns the model to put in the chunk, or a negative code. [dynamic] tells the caller whether the normal renderer still runs. */
    class Result {
        @JvmField var model = NOT_BAKED
        @JvmField var dynamic = true
    }

    fun resolve(world: WorldView, pos: BlockPos, state: BlockState, entity: BlockEntity, result: Result) {
        result.model = NOT_BAKED
        result.dynamic = true
        if (!active()) return
        val type = state.block.renderType
        if (type == -2 || (type == -1 && (state.block is ChestBlock || state.block is EnderChestBlock))) {
            // something hid this block on purpose (render type -1 is not what these blocks normally answer)
            (entity as? LidBaked)?.`astatine$built`(true)
            result.model = DRAW_NOTHING
            result.dynamic = false
            return
        }
        when (entity) {
            is ChestBlockEntity -> chest(world, pos, state, entity, result)
            is EnderChestBlockEntity -> {
                val closed = entity.viewerCount == 0 && entity.animationProgress == 0f
                (entity as LidBaked).`astatine$built`(closed)
                if (!closed) return
                result.model = single[ENDER][index(state.get(EnderChestBlock.FACING))]
                result.dynamic = result.model < 0
            }
            is SignBlockEntity -> {
                val block = state.block
                result.model = if (block is StandingSignBlock) signs[state.get(StandingSignBlock.ROTATION)]
                else signs[16 + index(state.get(WallSignBlock.FACING))]
                (entity as LidBaked).`astatine$built`(result.model >= 0)
                // text can arrive later without a chunk rebuild, so a sign stays on the renderer's list and only draws its words
            }
        }
    }

    private fun chest(world: WorldView, pos: BlockPos, state: BlockState, entity: ChestBlockEntity, result: Result) {
        val closed = entity.viewerCount == 0 && entity.animationProgress == 0f
        (entity as LidBaked).`astatine$built`(closed)
        val block = state.block
        val type = if (block === net.minecraft.block.Blocks.TRAPPED_CHEST) TRAPPED else CHEST
        if (same(world, pos.north(), block) || same(world, pos.west(), block)) {
            // the other half draws this one
            result.dynamic = false
            result.model = DRAW_NOTHING
            return
        }
        if (!closed) return
        val facing = index(state.get(ChestBlock.FACING))
        val east = same(world, pos.east(), block)
        val south = same(world, pos.south(), block)
        if (!east && !south) {
            result.model = single[type][facing]
        } else {
            // the model reaches into the next block: across a section edge the section's own bounds would cut it off at the screen edge
            if ((east && pos.x and 15 == 15) || (south && pos.z and 15 == 15)) return
            result.model = paired[type][facing][if (east) 0 else 1]
        }
        result.dynamic = result.model < 0
    }

    private fun same(world: WorldView, pos: BlockPos, block: Block): Boolean = world.getBlockState(pos).block === block

    private fun index(direction: Direction): Int = (direction.id - 2).coerceIn(0, 3)

    // ---- geometry ----

    /** Column-major 3x4 transform, composed the way the game's renderers compose theirs. */
    private class Transform {
        val m = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0)

        fun translate(x: Double, y: Double, z: Double): Transform {
            m[3] += m[0] * x + m[1] * y + m[2] * z
            m[7] += m[4] * x + m[5] * y + m[6] * z
            m[11] += m[8] * x + m[9] * y + m[10] * z
            return this
        }

        fun scale(x: Double, y: Double, z: Double): Transform {
            for (row in 0..2) {
                m[row * 4] *= x
                m[row * 4 + 1] *= y
                m[row * 4 + 2] *= z
            }
            return this
        }

        fun rotateY(degrees: Float): Transform {
            val radians = Math.toRadians(degrees.toDouble())
            val c = cos(radians)
            val s = sin(radians)
            for (row in 0..2) {
                val a = m[row * 4]
                val b = m[row * 4 + 2]
                m[row * 4] = a * c - b * s
                m[row * 4 + 2] = a * s + b * c
            }
            return this
        }

        fun copy(): Transform = Transform().also { System.arraycopy(m, 0, it.m, 0, 12) }

        fun x(x: Double, y: Double, z: Double) = m[0] * x + m[1] * y + m[2] * z + m[3]
        fun y(x: Double, y: Double, z: Double) = m[4] * x + m[5] * y + m[6] * z + m[7]
        fun z(x: Double, y: Double, z: Double) = m[8] * x + m[9] * y + m[10] * z + m[11]
    }

    private fun chestTransform(facing: Int, east: Boolean, south: Boolean): Transform {
        val angle = when (facing) { 2 -> 180f; 4 -> 90f; 5 -> -90f; else -> 0f }
        val t = Transform().translate(0.0, 1.0, 1.0).scale(1.0, -1.0, -1.0).translate(0.5, 0.5, 0.5)
        if (facing == 2 && east) t.translate(1.0, 0.0, 0.0)
        if (facing == 5 && south) t.translate(0.0, 0.0, -1.0)
        return t.rotateY(angle).translate(-0.5, -0.5, -0.5)
    }

    private fun signTransform(angle: Float, wall: Boolean): Transform {
        val size = 2.0 / 3.0
        val t = Transform().translate(0.5, 0.75 * size, 0.5).rotateY(angle)
        if (wall) t.translate(0.0, -0.3125, -0.4375)
        return t.scale(size, -size, -size)
    }

    private const val SCALE = 0.0625

    private fun model(parts: List<ModelPart>, sprite: PaddedSprite, transform: Transform): BakedModel {
        val quads = ArrayList<BakedQuad>()
        for (part in parts) {
            val placed = transform.copy().translate(part.x * SCALE, part.y * SCALE, part.z * SCALE)
            for (box in part.boxes) {
                for (polygon in (box as BoxAccessor).astatineFaces) {
                    quads += quad(polygon.vertices, sprite, placed)
                }
            }
        }
        return Model(quads, sprite)
    }

    private fun quad(vertices: Array<net.minecraft.client.render.model.Vertex>, sprite: PaddedSprite, t: Transform): BakedQuad {
        val data = IntArray(28)
        val px = FloatArray(4)
        val py = FloatArray(4)
        val pz = FloatArray(4)
        for (i in 0 until 4) {
            val v = vertices[i]
            val mx = v.pos.x * SCALE
            val my = v.pos.y * SCALE
            val mz = v.pos.z * SCALE
            px[i] = t.x(mx, my, mz).toFloat()
            py[i] = t.y(mx, my, mz).toFloat()
            pz[i] = t.z(mx, my, mz).toFloat()
            val u = sprite.uMin + (sprite.uMax - sprite.uMin) * v.u * sprite.uScale
            val w = sprite.vMin + (sprite.vMax - sprite.vMin) * v.v * sprite.vScale
            val at = i * 7
            data[at] = java.lang.Float.floatToRawIntBits(px[i])
            data[at + 1] = java.lang.Float.floatToRawIntBits(py[i])
            data[at + 2] = java.lang.Float.floatToRawIntBits(pz[i])
            data[at + 4] = java.lang.Float.floatToRawIntBits(u)
            data[at + 5] = java.lang.Float.floatToRawIntBits(w)
        }
        // cross of the two edges from the second corner, the same normal the game gives these faces
        val ax = (px[1] - px[0]).toDouble()
        val ay = (py[1] - py[0]).toDouble()
        val az = (pz[1] - pz[0]).toDouble()
        val bx = (px[1] - px[2]).toDouble()
        val by = (py[1] - py[2]).toDouble()
        val bz = (pz[1] - pz[2]).toDouble()
        var nx = by * az - bz * ay
        var ny = bz * ax - bx * az
        var nz = bx * ay - by * ax
        val length = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-6)
        nx /= length
        ny /= length
        nz /= length
        val shade = (min(1.0, AMBIENT + DIFFUSE * max(0.0, nx * L0X + ny * L0Y + nz * L0Z) + DIFFUSE * max(0.0, nx * L1X + ny * L1Y + nz * L1Z)) * 255.0).toInt()
        val color = (0xFF shl 24) or (shade shl 16) or (shade shl 8) or shade
        for (i in 0 until 4) data[i * 7 + 3] = color
        return BakedQuad(data, -1, nearest(nx, ny, nz))
    }

    // the two fixed lights the game uses for entities and block entities
    private const val AMBIENT = 0.4
    private const val DIFFUSE = 0.6
    private val L0 = normalized(0.2, 1.0, -0.7)
    private val L1 = normalized(-0.2, 1.0, 0.7)
    private val L0X = L0[0]
    private val L0Y = L0[1]
    private val L0Z = L0[2]
    private val L1X = L1[0]
    private val L1Y = L1[1]
    private val L1Z = L1[2]

    private fun normalized(x: Double, y: Double, z: Double): DoubleArray {
        val length = sqrt(x * x + y * y + z * z)
        return doubleArrayOf(x / length, y / length, z / length)
    }

    private fun nearest(x: Double, y: Double, z: Double): Direction {
        val ax = abs(x)
        val ay = abs(y)
        val az = abs(z)
        return if (ay >= ax && ay >= az) { if (y > 0) Direction.UP else Direction.DOWN }
        else if (az >= ax) { if (z > 0) Direction.SOUTH else Direction.NORTH }
        else { if (x > 0) Direction.EAST else Direction.WEST }
    }

    private class Model(private val quads: List<BakedQuad>, private val particle: TextureAtlasSprite) : BakedModel {
        override fun getQuads(direction: Direction): List<BakedQuad> = emptyList()
        override fun getQuads(): List<BakedQuad> = quads
        override fun useAmbientOcclusion(): Boolean = false
        override fun isGui3d(): Boolean = false
        override fun isCustomRenderer(): Boolean = false
        override fun getParticleIcon(): TextureAtlasSprite = particle
        override fun getTransformations(): ModelTransformations = ModelTransformations.NONE
    }
}

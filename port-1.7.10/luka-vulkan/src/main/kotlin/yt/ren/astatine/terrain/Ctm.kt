package yt.ren.astatine.terrain

import net.minecraft.block.Block
import net.minecraft.block.PaneBlock
import net.minecraft.block.state.BlockState
import net.minecraft.block.material.Material
import net.minecraft.client.Minecraft
import net.minecraft.client.render.model.block.BakedModel
import net.minecraft.client.render.model.block.BakedQuad
import net.minecraft.client.render.texture.TextureAtlas
import net.minecraft.client.resource.manager.ResourceManager
import net.minecraft.client.resource.pack.CustomResourcePack
import net.minecraft.client.resource.pack.ResourcePack
import net.minecraft.resource.Identifier
import net.minecraft.util.math.Direction
import org.apache.logging.log4j.LogManager
import yt.ren.astatine.memory.Memory
import yt.ren.astatine.mixin.terrain.CustomResourcePackAccessor
import java.io.File
import java.util.IdentityHashMap
import java.util.Properties
import java.util.zip.ZipFile
import kotlin.math.abs

/**
 * MCPatcher/OptiFine connected textures for full-cube blocks and panes. Tile choice is made here per face and the native
 * mesher swaps the UVs. The tile selection for each method is adapted from Right Proper MCPatcher (LGPL-3.0, see NOTICE.md).
 */
object Ctm {
    private const val METHOD_CTM = 0
    private const val METHOD_RANDOM = 1
    private const val METHOD_FIXED = 2
    private const val METHOD_HORIZONTAL = 3
    private const val METHOD_VERTICAL = 4
    private const val METHOD_TOP = 5
    private const val METHOD_REPEAT = 6
    private const val METHOD_HORIZONTAL_VERTICAL = 7
    private const val METHOD_VERTICAL_HORIZONTAL = 8
    private const val CONNECT_BLOCK = 0
    private const val CONNECT_TILE = 1
    private const val CONNECT_MATERIAL = 2
    private val LOG = LogManager.getLogger("Astatine CTM")
    private val PROPERTIES = Regex("assets/minecraft/((?:mcpatcher|optifine)/ctm/.+\\.properties)")
    private val UNSUPPORTED = listOf("biomes", "heights", "minHeight", "maxHeight")

    class Rule(
        val tiles: Array<String?>,
        val matchTiles: Set<String>,
        val matchBlocks: Map<Block, Set<Int>?>,
        val connect: Int,
        val faces: Int,
        val method: Int = METHOD_CTM,
        /** Running totals of the tile weights for `random`, or null when every tile is equally likely. */
        val weights: IntArray? = null,
        /** How many faces share one random pick: 0 per face, 1 opposite faces together, 2 the whole block. */
        val symmetry: Int = 0,
        val linked: Boolean = false,
        val width: Int = 1,
        val height: Int = 1,
    ) {
        var firstTile = 0

        /** The one texture this rule replaces, when it names exactly one. Panes need it. */
        var base: FloatArray? = null
    }

    /** For a block state: its rule, the faces it applies to, and six axis numbers per face (+u then +v in world steps). */
    class StateEntry(@JvmField val rule: Rule, @JvmField val faceMask: Int, @JvmField val axes: IntArray)

    /** [rects] holds eight floats per tile: the tile's u/v bounds, then the replaced sprite's (all zero if unknown). */
    class Baked(val states: Array<StateEntry?>, val actual: IdentityHashMap<BlockState, StateEntry>, val rects: FloatArray)

    private var rules: List<Rule> = emptyList()

    // ---- loading ----

    @JvmStatic
    fun registerSprites(atlas: TextureAtlas, manager: ResourceManager) {
        if (System.getProperty("luka.connectedTextures") == "false") {
            rules = ArrayList()
            return
        }
        val found = LinkedHashMap<String, Rule>()
        var skipped = 0
        val minecraft = Minecraft.getInstance()
        val packs = ArrayList<ResourcePack>()
        for (pack in minecraft.resourcePacks.selectedPacks) packs += pack.open()
        minecraft.resourcePacks.serverResourcePack?.let { packs += it }
        for (pack in packs.asReversed()) {
            val file = (pack as? CustomResourcePack)?.let { (it as CustomResourcePackAccessor).astatineFile } ?: continue
            for (path in list(file)) {
                if (path in found) continue
                val rule = try {
                    parse(manager, path)
                } catch (error: Exception) {
                    LOG.warn("Could not read {}: {}", path, error.toString())
                    null
                }
                if (rule != null) found[path] = rule else skipped++
            }
        }
        rules = ArrayList(found.values)
        for (rule in rules) for (tile in rule.tiles) if (tile != null) atlas.registerSprite(Identifier("minecraft", tile))
        LOG.info("Loaded {} connected texture rules, skipped {}", rules.size, skipped)
    }

    private fun list(file: File): List<String> {
        val out = ArrayList<String>()
        if (file.isDirectory) {
            file.walkTopDown().filter { it.isFile }.forEach { entry ->
                val relative = entry.relativeTo(file).invariantSeparatorsPath
                PROPERTIES.matchEntire(relative)?.let { out += it.groupValues[1] }
            }
        } else if (file.isFile) {
            ZipFile(file).use { zip ->
                for (entry in zip.entries()) {
                    if (entry.isDirectory || entry.name.contains("/._") || entry.name.startsWith("__MACOSX")) continue
                    PROPERTIES.matchEntire(entry.name)?.let { out += it.groupValues[1] }
                }
            }
        }
        return out
    }

    private fun exists(manager: ResourceManager, path: String): Boolean = try {
        manager.getResource(Identifier("minecraft", path)).asStream().close()
        true
    } catch (_: Exception) {
        false
    }

    private fun parse(manager: ResourceManager, path: String): Rule? {
        val properties = Properties()
        manager.getResource(Identifier("minecraft", path)).asStream().use { properties.load(it) }
        val method = when (properties.getProperty("method", "").trim().lowercase()) {
            "ctm", "glass" -> METHOD_CTM
            "random" -> METHOD_RANDOM
            "fixed", "static" -> METHOD_FIXED
            "horizontal", "bookshelf", "h" -> METHOD_HORIZONTAL
            "vertical", "v" -> METHOD_VERTICAL
            "top", "sandstone" -> METHOD_TOP
            "repeat", "pattern" -> METHOD_REPEAT
            "horizontal+vertical", "h+v" -> METHOD_HORIZONTAL_VERTICAL
            "vertical+horizontal", "v+h" -> METHOD_VERTICAL_HORIZONTAL
            else -> return null
        }
        if (UNSUPPORTED.any { properties.getProperty(it) != null }) return null
        val directory = path.substringBeforeLast('/')
        val root = path.substringBefore('/')
        val tiles = expand(properties.getProperty("tiles", "")).map { tile(it, directory, root) }.toTypedArray()
        val width = properties.getProperty("width")?.trim()?.toIntOrNull() ?: 1
        val height = properties.getProperty("height")?.trim()?.toIntOrNull() ?: 1
        val expected = when (method) {
            METHOD_CTM -> 47
            METHOD_FIXED, METHOD_TOP -> 1
            METHOD_HORIZONTAL, METHOD_VERTICAL -> 4
            METHOD_HORIZONTAL_VERTICAL, METHOD_VERTICAL_HORIZONTAL -> 7
            METHOD_REPEAT -> width * height
            else -> -1
        }
        if (if (expected < 0) tiles.isEmpty() else tiles.size != expected) return null
        val first = tiles.firstOrNull { it != null } ?: return null
        if (!exists(manager, "$first.png")) return null

        val name = path.substringAfterLast('/').removeSuffix(".properties")
        var blockNames = properties.getProperty("matchBlocks")
        var tileNames = properties.getProperty("matchTiles")
        if (blockNames == null && tileNames == null) {
            if (name.matches(Regex("block\\d+"))) blockNames = name.removePrefix("block") else tileNames = name
        }
        val metadata = properties.getProperty("metadata")?.let { expand(it).mapNotNull(String::toIntOrNull).toSet() }
        val blocks = blockNames?.let { parseBlocks(it, metadata) } ?: emptyMap()
        val sprites = tileNames?.let { words(it).map(::spriteName).toSet() } ?: emptySet()
        if (blocks.isEmpty() && sprites.isEmpty()) return null
        val connect = when (properties.getProperty("connect", "").trim().lowercase()) {
            "block" -> CONNECT_BLOCK
            "tile" -> CONNECT_TILE
            "material" -> CONNECT_MATERIAL
            else -> if (blocks.isNotEmpty()) CONNECT_BLOCK else CONNECT_TILE
        }
        val symmetry = when (properties.getProperty("symmetry", "").trim().lowercase()) {
            "opposite" -> 1
            "all" -> 2
            else -> 0
        }
        val linked = properties.getProperty("linked", "").trim().equals("true", true)
        return Rule(tiles, sprites, blocks, connect, parseFaces(properties.getProperty("faces")), method,
            parseWeights(properties.getProperty("weights"), tiles.size), symmetry, linked, width, height)
    }

    private fun parseWeights(text: String?, tiles: Int): IntArray? {
        val values = text?.let { expand(it).mapNotNull(String::toIntOrNull) } ?: return null
        if (values.isEmpty()) return null
        val out = IntArray(tiles)
        var total = 0
        for (i in 0 until tiles) {
            total += values.getOrElse(i) { 1 }.coerceAtLeast(0)
            out[i] = total
        }
        return if (total > 0) out else null
    }

    private fun words(text: String): List<String> = text.trim().split(Regex("[\\s,]+")).filter { it.isNotEmpty() }

    private fun expand(text: String): List<String> {
        val out = ArrayList<String>()
        for (word in words(text)) {
            val range = Regex("(\\d+)-(\\d+)").matchEntire(word)
            if (range != null) {
                for (i in range.groupValues[1].toInt()..range.groupValues[2].toInt()) out += i.toString()
            } else {
                out += word
            }
        }
        return out
    }

    private fun tile(word: String, directory: String, root: String): String? {
        if (word.equals("<skip>", true) || word.equals("<default>", true)) return null
        val name = word.removeSuffix(".png").removePrefix("minecraft:")
        return when {
            name.startsWith("~/") -> "$root/${name.substring(2)}"
            name.startsWith("./") -> "$directory/${name.substring(2)}"
            '/' in name -> name
            else -> "$directory/$name"
        }
    }

    private fun spriteName(word: String): String {
        val name = word.removeSuffix(".png")
        return when {
            ':' in name -> name
            '/' in name -> "minecraft:$name"
            else -> "minecraft:blocks/$name"
        }
    }

    private fun parseBlocks(text: String, metadata: Set<Int>?): Map<Block, Set<Int>?> {
        val out = HashMap<Block, Set<Int>?>()
        for (word in words(text)) {
            val parts = word.split(':').toMutableList()
            var metas = metadata
            val last = parts.last()
            if (parts.size > 1 && last.all { it.isDigit() || it == '-' }) {
                metas = expand(last).mapNotNull(String::toIntOrNull).toSet()
                parts.removeAt(parts.size - 1)
            }
            val joined = parts.joinToString(":")
            val block = joined.toIntOrNull()?.let { Block.byId(it) }
                ?: Block.REGISTRY.get(Identifier(if (':' in joined) joined else "minecraft:$joined"))
            if (block != null) out[block] = metas
        }
        return out
    }

    /** Bits follow Direction ids: down, up, north, south, west, east. */
    private fun parseFaces(text: String?): Int {
        if (text == null) return 63
        var mask = 0
        for (word in words(text)) {
            mask = mask or when (word.lowercase()) {
                "bottom" -> 1
                "top" -> 2
                "north" -> 4
                "south" -> 8
                "west" -> 16
                "east" -> 32
                "sides" -> 60
                "all" -> 63
                else -> 0
            }
        }
        return mask
    }

    // ---- baking against the stitched atlas ----

    fun bake(atlas: TextureAtlas, modelOf: (Int) -> BakedModel?, modelOfState: (BlockState) -> BakedModel?): Baked {
        val rectangles = ArrayList<Float>()
        val active = rules
        val matchRects = HashMap<Rule, List<FloatArray>>()
        for (rule in active) {
            val matched = rule.matchTiles.mapNotNull { name ->
                val sprite = atlas.getSprite(name)
                if (sprite === atlas.missingSprite) null else floatArrayOf(sprite.uMin, sprite.uMax, sprite.vMin, sprite.vMax)
            }
            matchRects[rule] = matched
            rule.base = matched.singleOrNull()
            rule.firstTile = rectangles.size / 8
            for (tile in rule.tiles) {
                val sprite = if (tile == null) atlas.missingSprite else atlas.getSprite("minecraft:$tile")
                rectangles += sprite.uMin
                rectangles += sprite.uMax
                rectangles += sprite.vMin
                rectangles += sprite.vMax
                val base = rule.base
                for (i in 0 until 4) rectangles += base?.get(i) ?: 0f
            }
        }
        val actual = IdentityHashMap<BlockState, StateEntry>()
        val panes = IdentityHashMap<Block, StateEntry?>()
        val states = arrayOfNulls<StateEntry>(TerrainTables.STATES)
        if (active.isNotEmpty()) {
            val directions = Direction.entries.toTypedArray()
            for (raw in 0 until TerrainTables.STATES) {
                val state = Block.STATE_REGISTRY.get(raw) ?: continue
                val block = state.block
                if (block is PaneBlock) {
                    if (!panes.containsKey(block)) panes[block] = bakePane(block, active, modelOfState, actual)
                    states[raw] = panes[block]
                    continue
                }
                val model = modelOf(raw) ?: continue
                if (model.quads.isNotEmpty()) continue
                val quads = arrayOfNulls<BakedQuad>(6)
                val full = directions.indices.all { face ->
                    val list = model.getQuads(directions[face])
                    if (list.size == 1 && coversFace(list[0])) {
                        quads[face] = list[0]
                        true
                    } else {
                        false
                    }
                }
                if (!full) continue
                for (rule in active) {
                    val blockMatch = state.block in rule.matchBlocks &&
                        rule.matchBlocks[state.block].let { it == null || state.block.getMetadataFromState(state) in it }
                    var mask = 0
                    val axes = IntArray(36)
                    for (face in 0 until 6) {
                        if ((rule.faces shr face) and 1 == 0) continue
                        val quad = quads[face]!!
                        if (!blockMatch && matchRects.getValue(rule).none { contains(it, quad) }) continue
                        if (axes(quad, axes, face * 6)) mask = mask or (1 shl face)
                    }
                    if (mask != 0) {
                        states[raw] = StateEntry(rule, mask, axes)
                        break
                    }
                }
            }
        }
        return Baked(states, actual, rectangles.toFloatArray())
    }

    /** Pane shapes come from properties resolved per block, so each shape gets its own entry. Returns one of them. */
    private fun bakePane(
        block: Block,
        active: List<Rule>,
        modelOfState: (BlockState) -> BakedModel?,
        actual: IdentityHashMap<BlockState, StateEntry>,
    ): StateEntry? {
        var representative: StateEntry? = null
        for (shape in block.stateDefinition().all()) {
            val model = modelOfState(shape) ?: continue
            val quads = Direction.entries.flatMap { model.getQuads(it) } + model.quads
            val rule = active.firstOrNull { rule ->
                val base = rule.base
                base != null && quads.any { contains(base, it) && (rule.faces shr it.face.id) and 1 != 0 }
            } ?: continue
            val axes = IntArray(36)
            var mask = 0
            for (quad in quads) {
                val face = quad.face.id
                if ((mask shr face) and 1 != 0 || (rule.faces shr face) and 1 == 0 || !contains(rule.base!!, quad)) continue
                if (axes(quad, axes, face * 6)) mask = mask or (1 shl face)
            }
            if (mask != 0) {
                val entry = StateEntry(rule, mask, axes)
                actual[shape] = entry
                if (representative == null) representative = entry
            }
        }
        return representative
    }

    private fun float(quad: BakedQuad, vertex: Int, element: Int): Float =
        java.lang.Float.intBitsToFloat(quad.vertices[vertex * 7 + element])

    private fun coversFace(quad: BakedQuad): Boolean {
        val axis = quad.face.axis.ordinal
        for (element in 0..2) {
            if (element == axis) continue
            val values = (0..3).map { float(quad, it, element) }
            if (abs(values.min()) > 1.0E-3f || abs(values.max() - 1.0f) > 1.0E-3f) return false
        }
        return true
    }

    private fun contains(rect: FloatArray, quad: BakedQuad): Boolean {
        val u = (0..3).map { float(quad, it, 4) }.average().toFloat()
        val v = (0..3).map { float(quad, it, 5) }.average().toFloat()
        return u >= rect[0] && u <= rect[1] && v >= rect[2] && v <= rect[3]
    }

    /** Reads which world direction +u and +v run in from the quad itself, so packs line up on every face. */
    private fun axes(quad: BakedQuad, out: IntArray, at: Int): Boolean {
        val epsilon = 1.0E-6f
        var foundU = false
        var foundV = false
        for (vertex in 1..3) {
            val du = float(quad, vertex, 4) - float(quad, 0, 4)
            val dv = float(quad, vertex, 5) - float(quad, 0, 5)
            val offset = when {
                abs(du) > epsilon && abs(dv) <= epsilon -> {
                    foundU = true
                    if (du > 0) 1 else -1
                }
                abs(dv) > epsilon && abs(du) <= epsilon -> {
                    foundV = true
                    if (dv > 0) 1 else -1
                }
                else -> 0
            }
            if (offset == 0) continue
            val slot = if (abs(du) > epsilon) at else at + 3
            for (element in 0..2) {
                val delta = float(quad, vertex, element) - float(quad, 0, element)
                out[slot + element] = if (abs(delta) < 1.0E-3f) 0 else if (delta > 0) offset else -offset
            }
        }
        return foundU && foundV
    }

    // ---- per block, during chunk building ----

    private fun connects(slice: WorldSlice, entry: StateEntry, raw: Int, x: Int, y: Int, z: Int): Boolean {
        val neighbor = slice.stateId(x, y, z)
        return when (entry.rule.connect) {
            CONNECT_TILE -> TerrainTables.ctmStates[neighbor]?.rule === entry.rule
            CONNECT_MATERIAL -> {
                val a: Material? = Block.STATE_REGISTRY.get(raw)?.block?.material
                a != null && Block.STATE_REGISTRY.get(neighbor)?.block?.material === a
            }
            else -> neighbor == raw
        }
    }

    /** Writes one tile number per face (0 keeps the normal texture). */
    fun fill(slice: WorldSlice, entry: StateEntry, raw: Int, x: Int, y: Int, z: Int, at: Long) {
        val rule = entry.rule
        val a = entry.axes
        for (face in 0 until 6) {
            var tile = 0
            if ((entry.faceMask shr face) and 1 != 0) {
                val n = face * 6
                val ux = a[n]; val uy = a[n + 1]; val uz = a[n + 2]
                val vx = a[n + 3]; val vy = a[n + 4]; val vz = a[n + 5]
                fun at(u: Int, v: Int) = connects(slice, entry, raw, x + u * ux + v * vx, y + u * uy + v * vy, z + u * uz + v * vz)
                val left = at(-1, 0)
                val right = at(1, 0)
                val down = at(0, 1)
                val up = at(0, -1)
                val index = when (rule.method) {
                    METHOD_RANDOM -> randomTile(slice, rule, raw, x, y, z, face)
                    METHOD_FIXED -> 0
                    METHOD_REPEAT -> repeatTile(rule, x, y, z, face)
                    METHOD_TOP -> if (face >= 2 && connects(slice, entry, raw, x, y + 1, z)) 0 else -1
                    METHOD_HORIZONTAL -> if (face >= 2) line(left, right) else -1
                    METHOD_VERTICAL -> if (face >= 2) verticalTile(line(connects(slice, entry, raw, x, y - 1, z), connects(slice, entry, raw, x, y + 1, z))) else -1
                    METHOD_HORIZONTAL_VERTICAL, METHOD_VERTICAL_HORIZONTAL -> {
                        if (face < 2) {
                            -1
                        } else {
                            val h = line(left, right)
                            val v = verticalTile(line(connects(slice, entry, raw, x, y - 1, z), connects(slice, entry, raw, x, y + 1, z)))
                            val first = if (rule.method == METHOD_HORIZONTAL_VERTICAL) h else v
                            val second = if (rule.method == METHOD_HORIZONTAL_VERTICAL) v else h
                            if (first != 3) first else when (second) {
                                0 -> 4
                                1 -> 5
                                2 -> 6
                                else -> 3
                            }
                        }
                    }
                    else -> tileIndex(left, right, down, up, { !at(1, 1) }, { !at(-1, 1) }, { !at(1, -1) }, { !at(-1, -1) })
                }
                if (index >= 0 && rule.tiles[index] != null) tile = rule.firstTile + index + 1
            }
            Memory.putChar(at + face * 2L, tile.toChar())
        }
    }

    /** The four-tile strip: 0 joins on the second side only, 1 on both, 2 on the first only, 3 stands alone. */
    private fun line(first: Boolean, second: Boolean): Int = when {
        first && second -> 1
        first -> 2
        second -> 0
        else -> 3
    }

    private fun verticalTile(index: Int): Int = index

    private fun hash(value: Int): Int {
        var x = value xor 61 xor (value shr 16)
        x += x shl 3
        x = x xor (x shr 4)
        x *= 668265261
        return x xor (x shr 15)
    }

    private fun randomTile(slice: WorldSlice, rule: Rule, raw: Int, x: Int, startY: Int, z: Int, face: Int): Int {
        if (rule.tiles.size == 1) return 0
        var y = startY
        if (rule.linked) {
            while (y > 0 && slice.stateId(x, y - 1, z) == raw) y--
        }
        val side = when (rule.symmetry) {
            1 -> face and 1.inv()
            2 -> 0
            else -> face
        }
        val value = hash(hash(hash(hash(side + 37) + x) + z) + y) and Int.MAX_VALUE
        val weights = rule.weights ?: return value % rule.tiles.size
        val pick = value % weights.last()
        return weights.indexOfFirst { pick < it }.coerceAtLeast(0)
    }

    private fun repeatTile(rule: Rule, x: Int, y: Int, z: Int, face: Int): Int {
        if (rule.tiles.size == 1) return 0
        val nx: Int
        val ny: Int
        when (face) {
            2 -> { nx = -x - 1; ny = -y }
            3 -> { nx = x; ny = -y }
            4 -> { nx = z; ny = -y }
            5 -> { nx = -z - 1; ny = -y }
            else -> { nx = x; ny = z }
        }
        return Math.floorMod(ny, rule.height) * rule.width + Math.floorMod(nx, rule.width)
    }

    /** Edge arguments are true when that diagonal block does NOT connect. */
    private inline fun tileIndex(
        left: Boolean, right: Boolean, down: Boolean, up: Boolean,
        rightDown: () -> Boolean, leftDown: () -> Boolean, rightUp: () -> Boolean, leftUp: () -> Boolean,
    ): Int {
        val index = when {
            left && !right && !down && !up -> 3
            !left && right && !down && !up -> 1
            !left && !right && down && !up -> 12
            !left && !right && !down && up -> 36
            left && right && !down && !up -> 2
            !left && !right && down && up -> 24
            left && !right && down && !up -> 15
            left && !right && !down && up -> 39
            !left && right && down && !up -> 13
            !left && right && !down && up -> 37
            !left && right && down && up -> 25
            left && !right && down && up -> 27
            left && right && !down && up -> 38
            left && right && down && !up -> 14
            left && right && down && up -> 26
            else -> 0
        }
        if (index == 0) return 0
        val e0 = rightDown()
        val e1 = leftDown()
        val e2 = rightUp()
        val e3 = leftUp()
        return when {
            index == 13 && e0 -> 4
            index == 15 && e1 -> 5
            index == 37 && e2 -> 16
            index == 39 && e3 -> 17
            index == 14 && e0 && e1 -> 7
            index == 25 && e0 && e2 -> 6
            index == 27 && e3 && e1 -> 19
            index == 38 && e3 && e2 -> 18
            index == 14 && !e0 && e1 -> 31
            index == 25 && e0 && !e2 -> 30
            index == 27 && !e3 && e1 -> 41
            index == 38 && e3 && !e2 -> 40
            index == 14 && e0 && !e1 -> 29
            index == 25 && !e0 && e2 -> 28
            index == 27 && e3 && !e1 -> 43
            index == 38 && !e3 && e2 -> 42
            index == 26 && e0 && e1 && e2 && e3 -> 46
            index == 26 && !e0 && e1 && e2 && e3 -> 9
            index == 26 && e0 && !e1 && e2 && e3 -> 21
            index == 26 && e0 && e1 && !e2 && e3 -> 8
            index == 26 && e0 && e1 && e2 && !e3 -> 20
            index == 26 && e0 && e1 && !e2 && !e3 -> 11
            index == 26 && !e0 && !e1 && e2 && e3 -> 22
            index == 26 && !e0 && e1 && !e2 && e3 -> 23
            index == 26 && e0 && !e1 && e2 && !e3 -> 10
            index == 26 && e0 && !e1 && !e2 && e3 -> 34
            index == 26 && !e0 && e1 && e2 && !e3 -> 35
            index == 26 && e0 && !e1 && !e2 && !e3 -> 32
            index == 26 && !e0 && e1 && !e2 && !e3 -> 33
            index == 26 && !e0 && !e1 && e2 && !e3 -> 44
            index == 26 && !e0 && !e1 && !e2 && e3 -> 45
            else -> index
        }
    }
}

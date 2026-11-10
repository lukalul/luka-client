package yt.ren.astatine.terrain

import net.minecraft.block.Block
import net.minecraft.block.material.Material
import net.minecraft.client.Minecraft
import net.minecraft.client.render.texture.Sprite
import net.minecraft.client.render.texture.TextureAtlas
import net.minecraft.client.resource.Identifier
import net.minecraft.client.resource.pack.CustomResourcePack
import net.minecraft.world.WorldView
import net.minecraft.world.biome.Biome
import org.apache.logging.log4j.LogManager
import yt.ren.astatine.mixin.texture.CustomResourcePackAccessor
import yt.ren.astatine.mixin.texture.UnopenedPackAccessor
import java.io.File
import java.io.InputStream
import java.util.IdentityHashMap
import java.util.Properties
import java.util.TreeMap
import java.util.zip.ZipFile

/**
 * MCPatcher/OptiFine connected textures for Minecraft 1.7.10. Rules are read from the enabled resource packs when the
 * block atlas is stitched, their tiles join that atlas, and the block renderer asks [sprite] for every face it draws
 * from a world position. The tile selection for each method is adapted from Right Proper MCPatcher (LGPL-3.0, see the
 * connected textures entry in NOTICE.md).
 */
object ConnectedTextures {
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
    private const val ALL_METADATA = 0xFFFF

    /** How often one face may be handed from rule to rule (a rule may match the tile another rule chose). */
    private const val PASSES = 4

    /** Sprite names of tiles are this prefix plus the path below assets/minecraft with '|' for '/'. */
    private const val NAMESPACE = "astatine_ctm"
    private const val PREFIX = "$NAMESPACE:"
    private val LOG = LogManager.getLogger("Astatine CTM")
    private val ENTRY = Regex("(?:[^/]+/)?assets/minecraft/((?:mcpatcher|optifine)/ctm/.+\\.(?:properties|png))")
    private val RANGE = Regex("(\\d+)-(\\d+)")
    private val SEPARATORS = Regex("[\\s,]+")
    private val BLOCK_FILE = Regex("block\\d+")

    // Texture +u and the face normal in world steps, by face: bottom, top, north, south, west, east. Texture +v is
    // +z on the two horizontal faces and down on the four sides.
    private val U_X = intArrayOf(1, 1, -1, 1, 0, 0)
    private val U_Z = intArrayOf(0, 0, 0, 0, 1, -1)
    private val NORMAL_X = intArrayOf(0, 0, 0, 0, -1, 1)
    private val NORMAL_Y = intArrayOf(-1, 1, 0, 0, 0, 0)
    private val NORMAL_Z = intArrayOf(0, 0, -1, 1, 0, 0)

    /** The settings switch. Call [refresh] after changing it so loaded chunks follow. */
    @JvmField
    @Volatile
    var enabled = true

    /** True only while at least one rule is attached to a block or a texture, so the common case costs one read. */
    @JvmField
    @Volatile
    var loaded = false

    class Rule(
        @JvmField val path: String,
        @JvmField val method: Int,
        val tileNames: Array<String?>,
        @JvmField val connect: Int,
        @JvmField val faces: Int,
        @JvmField val metadata: Int,
        /** Running totals of the tile weights for `random`, or null when every tile is equally likely. */
        @JvmField val weights: IntArray?,
        /** How many faces share one random pick: 0 per face, 1 opposite faces together, 2 the whole block. */
        @JvmField val symmetry: Int,
        @JvmField val width: Int,
        @JvmField val height: Int,
        @JvmField val innerSeams: Boolean,
        val weight: Int,
        @JvmField val minY: Int,
        @JvmField val maxY: Int,
        /** By biome id, or null for everywhere. */
        @JvmField val biomes: BooleanArray?,
        /** Block to the metadata values it matches, one bit each. */
        val blocks: Map<Block, Int>,
        val matchSprites: Set<String>,
    ) {
        @JvmField var tiles: Array<Sprite?> = arrayOfNulls(tileNames.size)

        /** For a rule that names both blocks and textures: the textures it is limited to. */
        @JvmField var only: Array<Any>? = null
    }

    class Entry(@JvmField val rule: Rule, @JvmField val metadata: Int)

    private class Scan {
        val properties = TreeMap<String, Properties>()
        val images = HashSet<String>()
    }

    private var pending: List<Rule> = emptyList()
    private var resolved = false

    @JvmStatic
    fun refresh() {
        Minecraft.getInstance().worldRenderer?.reload()
    }

    // ---- loading ----

    /** Start of a block atlas stitch: drops the tiles of the last stitch and adds those of the packs enabled now. */
    @JvmStatic
    fun register(atlas: TextureAtlas, sourced: MutableMap<Any?, Any?>) {
        loaded = false
        resolved = false
        pending = emptyList()
        try {
            sourced.keys.removeAll { it is String && it.startsWith(PREFIX) }
            if (System.getProperty("luka.connectedTextures") == "false") return
            val scan = Scan()
            for (file in packFiles()) {
                try {
                    scan(file, scan)
                } catch (error: Exception) {
                    LOG.warn("Could not list {}: {}", file.name, error.toString())
                }
            }
            if (scan.properties.isEmpty()) return
            val rules = ArrayList<Rule>()
            var skipped = 0
            for ((path, properties) in scan.properties) {
                val rule = try {
                    parse(path, properties, scan.images, sourced)
                } catch (error: Exception) {
                    LOG.warn("Could not read {}: {}", path, error.toString())
                    null
                }
                if (rule != null) rules += rule else skipped++
            }
            var tiles = 0
            for (rule in rules) {
                for (name in rule.tileNames) {
                    if (name != null && name.startsWith(PREFIX) && !sourced.containsKey(name)) {
                        atlas.registerSprite(name)
                        tiles++
                    }
                }
            }
            // Heaviest first, then by file name, as MCPatcher orders them; the scan already sorted by path.
            pending = rules.sortedWith(compareBy<Rule> { -it.weight }.thenBy { it.path.substringAfterLast('/') })
            LOG.info("Connected textures: {} rules, {} tiles, {} files skipped", rules.size, tiles, skipped)
        } catch (error: Throwable) {
            LOG.warn("Connected textures are off, loading failed", error)
            pending = emptyList()
        }
    }

    /** Where the atlas reads a tile from. Null for every sprite that is not one of ours. */
    @JvmStatic
    fun resource(id: Identifier, mipmap: Int): Identifier? {
        if (id.namespace != NAMESPACE) return null
        resolved = true
        val path = id.path.replace('|', '/')
        return Identifier("minecraft", if (mipmap == 0) "$path.png" else "$path.$mipmap.png")
    }

    /** End of the stitch: hangs the rules on the blocks and sprites they match. */
    @JvmStatic
    fun bake(atlas: TextureAtlas, sourced: Map<Any?, Any?>) {
        loaded = false
        try {
            for (block in Block.REGISTRY) (block as? CtmSlot)?.`astatine$setCtm`(null)
            for (sprite in sourced.values) (sprite as? CtmSlot)?.`astatine$setCtm`(null)
            val rules = pending
            if (rules.isEmpty()) return
            if (!resolved && rules.any { rule -> rule.tileNames.any { it != null && it.startsWith(PREFIX) } }) {
                LOG.warn("Connected textures are off, the atlas did not ask for tile paths")
                return
            }
            val byBlock = IdentityHashMap<Block, ArrayList<Entry>>()
            val bySprite = IdentityHashMap<Any, ArrayList<Entry>>()
            for (rule in rules) {
                var usable = 0
                for (i in rule.tileNames.indices) {
                    val name = rule.tileNames[i] ?: continue
                    val sprite = sourced[name] as? Sprite
                    // A tile whose image could not be read was replaced by the missing texture.
                    if (sprite != null && atlas.getSprite(name) === sprite) {
                        rule.tiles[i] = sprite
                        usable++
                    }
                }
                if (usable == 0) continue
                val sprites = rule.matchSprites.mapNotNull { sourced[it] }
                if (rule.blocks.isNotEmpty()) {
                    if (rule.matchSprites.isNotEmpty()) rule.only = sprites.toTypedArray()
                    for ((block, metadata) in rule.blocks) byBlock.getOrPut(block) { ArrayList() } += Entry(rule, metadata)
                } else {
                    for (sprite in sprites) bySprite.getOrPut(sprite) { ArrayList() } += Entry(rule, rule.metadata)
                }
            }
            var slots = 0
            for ((block, entries) in byBlock) {
                val slot = block as? CtmSlot ?: continue
                slot.`astatine$setCtm`(entries.toTypedArray())
                slots++
            }
            for ((sprite, entries) in bySprite) {
                val slot = sprite as? CtmSlot ?: continue
                slot.`astatine$setCtm`(entries.toTypedArray())
                slots++
            }
            loaded = slots > 0
            LOG.info("Connected textures: active on {} blocks and {} textures", byBlock.size, bySprite.size)
        } catch (error: Throwable) {
            LOG.warn("Connected textures are off, baking failed", error)
            loaded = false
        }
    }

    /** Files of the enabled packs, topmost pack first. */
    private fun packFiles(): List<File> {
        val out = ArrayList<File>()
        val packs = Minecraft.getInstance().resourcePacks ?: return out
        fun add(pack: Any?, unopened: Any?) {
            val file = (pack as? CustomResourcePack)?.let { (it as? CustomResourcePackAccessor)?.astatineFile }
                ?: (unopened as? UnopenedPackAccessor)?.astatineFile
            if (file != null && file !in out) out += file
        }
        add(packs.serverResourcePack, null)
        for (unopened in packs.selectedPacks.reversed()) {
            val pack = try {
                (unopened as? net.minecraft.client.resource.pack.ResourcePacks__UnopenedPack)?.open()
            } catch (_: Exception) {
                null
            }
            add(pack, unopened)
        }
        return out
    }

    /** Collects rule files and tile images. A pack listed earlier wins, as it does for every other resource. */
    private fun scan(file: File, into: Scan) {
        fun add(path: String, open: () -> InputStream) {
            if (path.endsWith(".png")) {
                into.images += path.removeSuffix(".png")
            } else if (path !in into.properties) {
                val properties = Properties()
                open().use { properties.load(it) }
                into.properties[path] = properties
            }
        }
        if (file.isDirectory) {
            val base = File(file, "assets/minecraft")
            for (root in arrayOf("mcpatcher/ctm", "optifine/ctm")) {
                val directory = File(base, root)
                if (!directory.isDirectory) continue
                directory.walkTopDown().filter { it.isFile }.forEach { entry ->
                    val path = entry.relativeTo(base).invariantSeparatorsPath
                    if (path.endsWith(".png") || path.endsWith(".properties")) add(path) { entry.inputStream() }
                }
            }
        } else if (file.isFile) {
            ZipFile(file).use { zip ->
                for (entry in zip.entries()) {
                    val name = entry.name
                    if (entry.isDirectory || !name.contains("/ctm/") || name.contains("/._") || name.startsWith("__MACOSX")) continue
                    val match = ENTRY.matchEntire(name) ?: continue
                    add(match.groupValues[1]) { zip.getInputStream(entry) }
                }
            }
        }
    }

    private fun parse(path: String, properties: Properties, images: Set<String>, sourced: Map<Any?, Any?>): Rule? {
        fun text(key: String): String? = properties.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }
        val method = when ((text("method") ?: "ctm").lowercase()) {
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
        val directory = path.substringBeforeLast('/')
        val root = path.substringBefore('/')
        val width = text("width")?.toIntOrNull() ?: 1
        val height = text("height")?.toIntOrNull() ?: 1
        val needed = when (method) {
            METHOD_CTM -> 47
            METHOD_FIXED, METHOD_TOP -> 1
            METHOD_HORIZONTAL, METHOD_VERTICAL -> 4
            METHOD_HORIZONTAL_VERTICAL, METHOD_VERTICAL_HORIZONTAL -> 7
            METHOD_REPEAT -> if (width < 1 || height < 1) return null else width * height
            else -> 0
        }
        var words = text("tiles")?.let(::expand) ?: emptyList()
        if (words.isEmpty()) {
            // No list means the numbered images next to the file.
            val numbered = ArrayList<String>()
            while ("$directory/${numbered.size}" in images) numbered += numbered.size.toString()
            words = numbered
        }
        if (words.size < needed || words.isEmpty()) return null
        if (needed > 0) words = words.subList(0, needed)
        val tiles = arrayOfNulls<String>(words.size)
        for (i in words.indices) {
            val tile = tilePath(words[i], directory, root) ?: continue
            // A tile that is named but missing makes the whole rule unusable.
            tiles[i] = spriteName(tile, images, sourced) ?: return null
        }
        if (tiles.all { it == null }) return null

        val name = path.substringAfterLast('/').removeSuffix(".properties")
        var blockNames = text("matchBlocks")
        var tileNames = text("matchTiles")
        if (blockNames == null && tileNames == null) {
            if (name.matches(BLOCK_FILE)) blockNames = name.removePrefix("block") else tileNames = name
        }
        val metadata = text("metadata")?.let(::metadataMask) ?: ALL_METADATA
        val blocks = blockNames?.let { parseBlocks(it, metadata) } ?: emptyMap()
        val sprites = HashSet<String>()
        for (word in tileNames?.let(::words) ?: emptyList()) {
            val plain = word.removeSuffix(".png").removePrefix("minecraft:")
            if ('/' !in plain) {
                sprites += plain
            } else {
                val tile = tilePath(plain, directory, root) ?: continue
                sprites += vanillaName(tile) ?: (PREFIX + tile.replace('/', '|'))
            }
        }
        if (blocks.isEmpty() && sprites.isEmpty()) return null
        val connect = when (text("connect")?.lowercase()) {
            "block" -> CONNECT_BLOCK
            "tile" -> CONNECT_TILE
            "material" -> CONNECT_MATERIAL
            else -> if (blocks.isNotEmpty()) CONNECT_BLOCK else CONNECT_TILE
        }
        val symmetry = when (text("symmetry")?.lowercase()) {
            "opposite" -> 1
            "all" -> 2
            else -> 0
        }
        var minY = text("minHeight")?.toIntOrNull() ?: Int.MIN_VALUE
        var maxY = text("maxHeight")?.toIntOrNull() ?: Int.MAX_VALUE
        text("heights")?.let { list ->
            // Only one span fits the two bounds; a list of several spans keeps its outer limits.
            val values = expand(list).mapNotNull(String::toIntOrNull)
            if (values.isNotEmpty()) {
                minY = maxOf(minY, values.min())
                maxY = minOf(maxY, values.max())
            }
        }
        return Rule(
            path, method, tiles, connect, parseFaces(text("faces")), metadata,
            if (method == METHOD_RANDOM) parseWeights(text("weights"), tiles.size) else null, symmetry, width, height,
            text("innerSeams").equals("true", true), text("weight")?.toIntOrNull() ?: 0, minY, maxY,
            text("biomes")?.let(::parseBiomes), blocks, sprites,
        )
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

    private fun words(text: String): List<String> = text.trim().split(SEPARATORS).filter { it.isNotEmpty() }

    private fun expand(text: String): List<String> {
        val out = ArrayList<String>()
        for (word in words(text)) {
            val range = RANGE.matchEntire(word)
            if (range != null) {
                val from = range.groupValues[1].toIntOrNull() ?: continue
                val to = range.groupValues[2].toIntOrNull() ?: continue
                if (to - from > 4096) continue
                for (i in from..to) out += i.toString()
            } else {
                out += word
            }
        }
        return out
    }

    private fun metadataMask(text: String): Int {
        var mask = 0
        for (value in expand(text)) {
            val bit = value.toIntOrNull() ?: continue
            if (bit in 0..15) mask = mask or (1 shl bit)
        }
        return mask
    }

    /** Path below assets/minecraft without the extension, or null for a tile the rule leaves alone. */
    private fun tilePath(word: String, directory: String, root: String): String? {
        if (word.equals("<skip>", true) || word.equals("<default>", true) || word.equals("null", true)) return null
        val name = word.removeSuffix(".png").removePrefix("minecraft:")
        return when {
            name.startsWith("~/") -> "$root/${name.substring(2)}"
            name.startsWith("./") -> "$directory/${name.substring(2)}"
            '/' in name -> name
            else -> "$directory/$name"
        }
    }

    private fun vanillaName(tile: String): String? =
        tile.removePrefix("textures/blocks/").takeIf { tile.startsWith("textures/blocks/") && '/' !in it }

    private fun spriteName(tile: String, images: Set<String>, sourced: Map<Any?, Any?>): String? {
        val vanilla = vanillaName(tile)
        return when {
            vanilla != null -> vanilla.takeIf { sourced.containsKey(it) }
            tile in images -> PREFIX + tile.replace('/', '|')
            else -> null
        }
    }

    private fun parseBlocks(text: String, metadata: Int): Map<Block, Int> {
        val out = IdentityHashMap<Block, Int>()
        for (word in words(text)) {
            val parts = word.split(':').toMutableList()
            var mask = metadata
            val last = parts.last()
            if (parts.size > 1 && last.isNotEmpty() && last.all { it.isDigit() || it == '-' }) {
                mask = metadataMask(last)
                parts.removeAt(parts.size - 1)
            }
            val joined = parts.joinToString(":")
            val id = joined.toIntOrNull()
            val block = when {
                id != null -> if (Block.REGISTRY.containsId(id)) Block.byId(id) else null
                Block.REGISTRY.containsKey(joined) -> Block.REGISTRY.get(joined) as? Block
                else -> null
            }
            if (block != null && block.material !== Material.AIR) out[block] = (out[block] ?: 0) or mask
        }
        return out
    }

    /** Bits follow the face numbers: bottom, top, north, south, west, east. */
    private fun parseFaces(text: String?): Int {
        if (text == null) return 63
        var mask = 0
        for (word in words(text)) {
            mask = mask or when (word.lowercase()) {
                "bottom", "down" -> 1
                "top", "up" -> 2
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

    private fun parseBiomes(text: String): BooleanArray {
        fun plain(name: String) = name.lowercase().filter { it.isLetterOrDigit() }
        val wanted = words(text).map(::plain).toSet()
        val all = Biome.getAll()
        val out = BooleanArray(all.size)
        for (biome in all) {
            if (biome != null && biome.id in out.indices && biome.name != null && plain(biome.name) in wanted) out[biome.id] = true
        }
        return out
    }

    // ---- per face ----

    /**
     * The sprite to draw for one face, given the one vanilla chose. [rotated] is set when the block renderer turns
     * this face's texture (sideways logs, quartz pillars); the directional methods then leave the face alone, because
     * their tiles would point the wrong way.
     */
    @JvmStatic
    fun sprite(block: Block, world: WorldView, x: Int, y: Int, z: Int, face: Int, original: Sprite, rotated: Boolean): Sprite {
        val blockRules = (block as? CtmSlot)?.`astatine$ctm`()
        val tileRules = (original as? CtmSlot)?.`astatine$ctm`()
        if (blockRules == null && tileRules == null) return original
        if (face < 0 || face > 5) return original
        val metadata = world.getBlockMetadata(x, y, z) and 15
        var current = original
        var first: Rule? = null
        if (blockRules != null) {
            @Suppress("UNCHECKED_CAST")
            for (entry in blockRules as Array<Entry>) {
                val tile = pick(entry, block, metadata, world, x, y, z, face, original, original, rotated) ?: continue
                first = entry.rule
                current = tile
                break
            }
        }
        if (first == null && tileRules != null) {
            @Suppress("UNCHECKED_CAST")
            for (entry in tileRules as Array<Entry>) {
                val tile = pick(entry, block, metadata, world, x, y, z, face, original, original, rotated) ?: continue
                first = entry.rule
                current = tile
                break
            }
        }
        if (first == null) return original
        // A rule may match the tile another rule put there, each rule at most once.
        var second: Rule? = null
        var third: Rule? = null
        for (pass in 1 until PASSES) {
            @Suppress("UNCHECKED_CAST")
            val next = (current as? CtmSlot)?.`astatine$ctm`() as Array<Entry>? ?: break
            var hit: Rule? = null
            for (entry in next) {
                val rule = entry.rule
                if (rule === first || rule === second || rule === third) continue
                val tile = pick(entry, block, metadata, world, x, y, z, face, current, original, rotated) ?: continue
                hit = rule
                current = tile
                break
            }
            if (hit == null) break
            if (second == null) second = hit else third = hit
        }
        return current
    }

    private fun pick(
        entry: Entry, block: Block, metadata: Int, world: WorldView, x: Int, y: Int, z: Int, face: Int,
        current: Sprite, original: Sprite, rotated: Boolean,
    ): Sprite? {
        val rule = entry.rule
        if ((rule.faces shr face) and 1 == 0) return null
        if ((entry.metadata shr metadata) and 1 == 0) return null
        if (y < rule.minY || y > rule.maxY) return null
        val only = rule.only
        if (only != null) {
            var found = false
            for (sprite in only) if (sprite === current) found = true
            if (!found) return null
        }
        val biomes = rule.biomes
        if (biomes != null) {
            val id = world.getBiome(x, z)?.id ?: return null
            if (id < 0 || id >= biomes.size || !biomes[id]) return null
        }
        val index = when (rule.method) {
            METHOD_FIXED -> 0
            METHOD_RANDOM -> randomTile(rule, x, y, z, face)
            METHOD_REPEAT -> repeatTile(rule, x, y, z, face)
            else -> if (rotated) -1 else connectedTile(rule, block, metadata, world, x, y, z, face, original)
        }
        return if (index < 0) null else rule.tiles[index]
    }

    private fun connectedTile(rule: Rule, block: Block, metadata: Int, world: WorldView, x: Int, y: Int, z: Int, face: Int, original: Sprite): Int {
        if (rule.method != METHOD_CTM) {
            if (face < 2) return -1
            val above = connects(rule, block, metadata, world, x, y + 1, z, face, original)
            if (rule.method == METHOD_TOP) return if (above) 0 else -1
            val ux = U_X[face]
            val uz = U_Z[face]
            if (rule.method == METHOD_VERTICAL) return line(connects(rule, block, metadata, world, x, y - 1, z, face, original), above)
            val h = line(
                connects(rule, block, metadata, world, x - ux, y, z - uz, face, original),
                connects(rule, block, metadata, world, x + ux, y, z + uz, face, original),
            )
            if (rule.method == METHOD_HORIZONTAL) return h
            val v = line(connects(rule, block, metadata, world, x, y - 1, z, face, original), above)
            val one = if (rule.method == METHOD_HORIZONTAL_VERTICAL) h else v
            val other = if (rule.method == METHOD_HORIZONTAL_VERTICAL) v else h
            return if (one != 3) one else when (other) {
                0 -> 4
                1 -> 5
                2 -> 6
                else -> 3
            }
        }
        val ux = U_X[face]
        val uz = U_Z[face]
        val vy = if (face < 2) 0 else -1
        val vz = if (face < 2) 1 else 0
        val left = connects(rule, block, metadata, world, x - ux, y, z - uz, face, original)
        val right = connects(rule, block, metadata, world, x + ux, y, z + uz, face, original)
        val down = connects(rule, block, metadata, world, x, y + vy, z + vz, face, original)
        val up = connects(rule, block, metadata, world, x, y - vy, z - vz, face, original)
        return tileIndex(
            left, right, down, up,
            { !connects(rule, block, metadata, world, x + ux, y + vy, z + uz + vz, face, original) },
            { !connects(rule, block, metadata, world, x - ux, y + vy, z - uz + vz, face, original) },
            { !connects(rule, block, metadata, world, x + ux, y - vy, z + uz - vz, face, original) },
            { !connects(rule, block, metadata, world, x - ux, y - vy, z - uz - vz, face, original) },
        )
    }

    /** Whether the block at the given place continues the texture of the face being drawn. */
    private fun connects(rule: Rule, block: Block, metadata: Int, world: WorldView, x: Int, y: Int, z: Int, face: Int, original: Sprite): Boolean {
        val other = world.getBlock(x, y, z) ?: return false
        when (rule.connect) {
            CONNECT_BLOCK -> if (other !== block || (world.getBlockMetadata(x, y, z) and 15) != metadata) return false
            CONNECT_TILE -> if (other.material === Material.AIR || other.getSprite(world, x, y, z, face) !== original) return false
            else -> if (other.material !== block.material) return false
        }
        // A neighbour whose own face is covered does not join, which leaves a border in inner corners.
        return rule.innerSeams || other.shouldRenderFace(world, x + NORMAL_X[face], y + NORMAL_Y[face], z + NORMAL_Z[face], face)
    }

    /** The four-tile strip: 0 joins on the second side only, 1 on both, 2 on the first only, 3 stands alone. */
    private fun line(first: Boolean, second: Boolean): Int = when {
        first && second -> 1
        first -> 2
        second -> 0
        else -> 3
    }

    private fun hash(value: Int): Int {
        var x = value xor 61 xor (value shr 16)
        x += x shl 3
        x = x xor (x shr 4)
        x *= 668265261
        return x xor (x shr 15)
    }

    private fun randomTile(rule: Rule, x: Int, y: Int, z: Int, face: Int): Int {
        val count = rule.tiles.size
        if (count == 1) return 0
        val side = when (rule.symmetry) {
            1 -> face and 1.inv()
            2 -> 0
            else -> face
        }
        val value = hash(hash(hash(hash(side + 37) + x) + z) + y) and Int.MAX_VALUE
        val weights = rule.weights ?: return value % count
        val pick = value % weights[count - 1]
        for (i in 0 until count) if (pick < weights[i]) return i
        return 0
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

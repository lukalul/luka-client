package yt.ren.astatine.render.geometry

import net.minecraft.client.render.model.block.BakedModel
import net.minecraft.client.render.vertex.BufferBuilder
import net.minecraft.item.ItemStack
import net.minecraft.util.math.Direction
import yt.ren.astatine.render.GL
import java.util.IdentityHashMap

/**
 * Item models as permanent GPU meshes, so a held item is drawn by reference instead of copying its vertices every frame.
 * One mesh per (model, colour). Game thread only.
 */
object ItemMeshes {
    /** ASTATINE_ITEM_MESHES=off goes back to drawing items as immediate geometry, for comparisons. */
    @JvmField
    val ENABLED: Boolean = System.getenv("ASTATINE_ITEM_MESHES") != "off"

    const val NONE = 0L

    private const val PLAIN = 0x7FFFFFFE00000000L

    private const val MAX_MESHES = 2048

    private class Entry(val tinted: Boolean) {
        val meshes = HashMap<Long, Long>(4)
    }

    private val entries = IdentityHashMap<BakedModel, Entry>()
    private var count = 0

    @JvmField
    val builder = BufferBuilder(1 shl 18)

    private fun entry(model: BakedModel): Entry = entries.getOrPut(model) {
        val tinted = Direction.entries.any { face -> model.getQuads(face).any { it.hasTint() } } || model.quads.any { it.hasTint() }
        Entry(tinted)
    }

    /**
     * What makes this draw's vertices different from another draw of the same model. Generated item models mark every
     * layer as tinted even when the item has no colour, so the colours are asked for instead of assumed.
     */
    @JvmStatic
    fun key(model: BakedModel, color: Int, stack: ItemStack?): Long {
        if (color != -1 || stack == null || !entry(model).tinted) return PLAIN or (color.toLong() and 0xFFFFFFFFL)
        val item = stack.item
        return (item.getColorTint(stack, 0).toLong() shl 32) or (item.getColorTint(stack, 1).toLong() and 0xFFFFFFFFL)
    }

    /** The mesh for this model and key, or [NONE] if it has not been built. */
    @JvmStatic
    fun find(model: BakedModel, key: Long): Long = entry(model).meshes[key] ?: NONE

    @JvmStatic
    fun store(model: BakedModel, key: Long, mesh: Long) {
        if (count >= MAX_MESHES) clear()
        entry(model).meshes[key] = mesh
        count++
    }

    /** Models are rebuilt on a resource reload, so the old meshes can never be reached again. */
    @JvmStatic
    fun clear() {
        for (entry in entries.values) for (mesh in entry.meshes.values) GL.releaseMesh(mesh)
        entries.clear()
        count = 0
    }
}

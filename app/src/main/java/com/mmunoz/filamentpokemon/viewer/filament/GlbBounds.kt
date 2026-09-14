package com.mmunoz.filamentpokemon.viewer.filament

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import kotlin.math.max
import kotlin.math.min

/**
 * World-space bounds of a glTF-Binary model, computed the way gltfio computes
 * `FilamentAsset.boundingBox` (every scene root and parentless node, each mesh primitive's
 * POSITION min/max transformed by the node's world transform) with one difference: normalized
 * integer positions (`KHR_mesh_quantization`) are dequantized first. gltfio 1.75 takes the
 * accessor's raw min/max, so such a model reports a box up to 32767x too large and
 * `ModelViewer.transformToUnitCube` shrinks it to a speck.
 */
object GlbBounds {

    class Aabb(val min: FloatArray, val max: FloatArray) {
        val center: FloatArray get() = FloatArray(3) { (min[it] + max[it]) / 2f }
        val halfExtent: FloatArray get() = FloatArray(3) { (max[it] - min[it]) / 2f }
    }

    /**
     * The corrected bounds, or null when the model has no normalized integer positions (gltfio's
     * own box is right then) or the JSON chunk cannot be read.
     */
    fun correctedBox(glb: ByteBuffer): Aabb? = runCatching { compute(jsonChunk(glb) ?: return null) }.getOrNull()

    private fun jsonChunk(glb: ByteBuffer): JsonObject? {
        val buffer = glb.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        if (buffer.remaining() < 20 || buffer.getInt(0) != GLB_MAGIC) return null
        val chunkLength = buffer.getInt(12)
        if (buffer.getInt(16) != JSON_CHUNK || chunkLength <= 0 || 20 + chunkLength > buffer.remaining()) return null
        val bytes = ByteArray(chunkLength)
        buffer.position(20)
        buffer.get(bytes)
        return Json.parseToJsonElement(String(bytes, StandardCharsets.UTF_8)).jsonObject
    }

    private fun compute(gltf: JsonObject): Aabb? {
        val accessors = gltf["accessors"]?.jsonArray ?: return null
        val meshes = gltf["meshes"]?.jsonArray ?: return null
        val nodes = gltf["nodes"]?.jsonArray ?: return null

        val meshBoxes = meshes.map { mesh -> meshBox(mesh.jsonObject, accessors) }
        if (meshBoxes.none { it?.quantized == true }) return null

        val childIndices = nodes.flatMap { node -> node.jsonObject["children"]?.jsonArray?.map { it.jsonPrimitive.int } ?: emptyList() }.toSet()
        val roots = LinkedHashSet<Int>()
        gltf["scenes"]?.jsonArray?.forEach { scene -> scene.jsonObject["nodes"]?.jsonArray?.forEach { roots += it.jsonPrimitive.int } }
        nodes.indices.filter { it !in childIndices }.forEach { roots += it }

        val total = Aabb(FloatArray(3) { Float.POSITIVE_INFINITY }, FloatArray(3) { Float.NEGATIVE_INFINITY })
        // glTF nodes form a tree; a hostile file that repeats children would otherwise fan out exponentially.
        val visited = HashSet<Int>()
        fun visit(index: Int, parentWorld: FloatArray, depth: Int) {
            if (depth > MAX_DEPTH || index !in nodes.indices || !visited.add(index)) return
            val node = nodes[index].jsonObject
            val world = multiply(parentWorld, localTransform(node))
            node["mesh"]?.jsonPrimitive?.intOrNull?.let { meshIndex ->
                meshBoxes.getOrNull(meshIndex)?.let { box -> total.expand(box.aabb.transformed(world)) }
            }
            node["children"]?.jsonArray?.forEach { visit(it.jsonPrimitive.int, world, depth + 1) }
        }
        roots.forEach { visit(it, IDENTITY, 0) }
        return total.takeIf { it.min.all { v -> v.isFinite() } && it.max.all { v -> v.isFinite() } }
    }

    private class MeshBox(val aabb: Aabb, val quantized: Boolean)

    /** Object-space box of a mesh: union of its primitives' POSITION min/max, plus morph target deltas. */
    private fun meshBox(mesh: JsonObject, accessors: JsonArray): MeshBox? {
        var quantized = false
        val box = Aabb(FloatArray(3) { Float.POSITIVE_INFINITY }, FloatArray(3) { Float.NEGATIVE_INFINITY })
        for (primitive in mesh["primitives"]?.jsonArray ?: return null) {
            val attributes = primitive.jsonObject["attributes"]?.jsonObject ?: continue
            val positionIndex = attributes["POSITION"]?.jsonPrimitive?.intOrNull ?: continue
            val base = accessorBounds(accessors, positionIndex) ?: continue
            quantized = quantized || base.quantized
            box.expand(base.aabb)
            primitive.jsonObject["targets"]?.jsonArray?.forEach { target ->
                val targetIndex = target.jsonObject["POSITION"]?.jsonPrimitive?.intOrNull ?: return@forEach
                val delta = accessorBounds(accessors, targetIndex) ?: return@forEach
                quantized = quantized || delta.quantized
                box.expand(Aabb(FloatArray(3) { base.aabb.min[it] + delta.aabb.min[it] }, FloatArray(3) { base.aabb.max[it] + delta.aabb.max[it] }))
            }
        }
        if (!box.min.all { it.isFinite() }) return null
        return MeshBox(box, quantized)
    }

    private class AccessorBounds(val aabb: Aabb, val quantized: Boolean)

    private fun accessorBounds(accessors: JsonArray, index: Int): AccessorBounds? {
        val accessor = accessors.getOrNull(index)?.jsonObject ?: return null
        val min = accessor["min"]?.jsonArray?.takeIf { it.size >= 3 } ?: return null
        val max = accessor["max"]?.jsonArray?.takeIf { it.size >= 3 } ?: return null
        val normalized = accessor["normalized"]?.jsonPrimitive?.booleanOrNull == true
        val divisor = if (normalized) normalizationDivisor(accessor["componentType"]?.jsonPrimitive?.intOrNull) else null
        fun read(array: JsonArray, i: Int): Float {
            val raw = array[i].jsonPrimitive.float
            return if (divisor == null) raw else max(raw / divisor, -1f)
        }
        return AccessorBounds(
            aabb = Aabb(FloatArray(3) { read(min, it) }, FloatArray(3) { read(max, it) }),
            quantized = divisor != null
        )
    }

    /** glTF normalization: signed types map c / (2^(bits-1) - 1) clamped at -1, unsigned c / (2^bits - 1). */
    private fun normalizationDivisor(componentType: Int?): Float? = when (componentType) {
        COMPONENT_BYTE -> 127f
        COMPONENT_UNSIGNED_BYTE -> 255f
        COMPONENT_SHORT -> 32767f
        COMPONENT_UNSIGNED_SHORT -> 65535f
        else -> null
    }

    private fun localTransform(node: JsonObject): FloatArray {
        node["matrix"]?.jsonArray?.takeIf { it.size == 16 }?.let { m -> return FloatArray(16) { m[it].jsonPrimitive.float } }
        val t = node["translation"]?.jsonArray?.let { a -> FloatArray(3) { a[it].jsonPrimitive.float } } ?: floatArrayOf(0f, 0f, 0f)
        val r = node["rotation"]?.jsonArray?.let { a -> FloatArray(4) { a[it].jsonPrimitive.float } } ?: floatArrayOf(0f, 0f, 0f, 1f)
        val s = node["scale"]?.jsonArray?.let { a -> FloatArray(3) { a[it].jsonPrimitive.float } } ?: floatArrayOf(1f, 1f, 1f)
        val (x, y, z, w) = r
        // Column-major T * R * S, as glTF specifies.
        return floatArrayOf(
            (1 - 2 * (y * y + z * z)) * s[0], (2 * (x * y + z * w)) * s[0], (2 * (x * z - y * w)) * s[0], 0f,
            (2 * (x * y - z * w)) * s[1], (1 - 2 * (x * x + z * z)) * s[1], (2 * (y * z + x * w)) * s[1], 0f,
            (2 * (x * z + y * w)) * s[2], (2 * (y * z - x * w)) * s[2], (1 - 2 * (x * x + y * y)) * s[2], 0f,
            t[0], t[1], t[2], 1f
        )
    }

    private fun multiply(a: FloatArray, b: FloatArray): FloatArray = FloatArray(16) { i ->
        val row = i % 4
        val col = i / 4
        var sum = 0f
        for (k in 0 until 4) sum += a[k * 4 + row] * b[col * 4 + k]
        sum
    }

    private fun Aabb.transformed(m: FloatArray): Aabb {
        val out = Aabb(FloatArray(3) { Float.POSITIVE_INFINITY }, FloatArray(3) { Float.NEGATIVE_INFINITY })
        for (corner in 0 until 8) {
            val p = FloatArray(3) { axis -> if (corner shr axis and 1 == 0) min[axis] else max[axis] }
            for (axis in 0 until 3) {
                val v = m[axis] * p[0] + m[4 + axis] * p[1] + m[8 + axis] * p[2] + m[12 + axis]
                out.min[axis] = min(out.min[axis], v)
                out.max[axis] = max(out.max[axis], v)
            }
        }
        return out
    }

    private fun Aabb.expand(other: Aabb) {
        for (axis in 0 until 3) {
            min[axis] = min(min[axis], other.min[axis])
            max[axis] = max(max[axis], other.max[axis])
        }
    }

    private const val GLB_MAGIC = 0x46546C67
    private const val JSON_CHUNK = 0x4E4F534A
    private const val COMPONENT_BYTE = 5120
    private const val COMPONENT_UNSIGNED_BYTE = 5121
    private const val COMPONENT_SHORT = 5122
    private const val COMPONENT_UNSIGNED_SHORT = 5123
    private const val MAX_DEPTH = 64
    private val IDENTITY = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
}

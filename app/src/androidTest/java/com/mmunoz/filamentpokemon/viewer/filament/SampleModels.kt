package com.mmunoz.filamentpokemon.viewer.filament

import android.content.Context
import java.io.File
import java.nio.ByteBuffer

/**
 * The debug-only smoke-test models under `assets/models/` (see that folder's README for
 * provenance). Every duck variant is the plain Duck re-encoded, so they must all decode to the
 * same geometry.
 */
object SampleModels {
    const val BOX_TEXTURED = "sample.glb"
    const val DUCK = "duck.glb"
    const val DUCK_DRACO = "duck_draco.glb"
    const val DUCK_MESHOPT = "duck_meshopt_unquantized.glb"
    const val DUCK_MESHOPT_QUANTIZED = "duck_meshopt.glb"
    const val CORRUPT = "corrupt.glb"

    fun readDirectBuffer(context: Context, name: String): ByteBuffer =
        context.assets.open("models/$name").use { input ->
            val bytes = input.readBytes()
            ByteBuffer.allocateDirect(bytes.size).put(bytes).apply { flip() }
        }

    /** Mirrors the app: models are only ever read from the cache directory. */
    fun copyToCache(context: Context, name: String): File =
        File(context.cacheDir, "decoder-test-$name").also { file ->
            context.assets.open("models/$name").use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
        }
}

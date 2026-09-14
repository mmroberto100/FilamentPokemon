package com.mmunoz.filamentpokemon.viewer.filament

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.PixelCopy
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * End-to-end through the app's own renderer: a real SurfaceView in a real Activity, the
 * Choreographer frame loop, and [FilamentModelRenderer.loadGlb] from the cache directory,
 * exactly as the viewer screen does it. [FilamentModelRenderer.onModelLoaded] fires once every
 * resource of the model is resident; the surface is then captured and compared with the
 * environment-only frame taken before the load, so the model must actually show up on screen.
 */
@RunWith(AndroidJUnit4::class)
class FilamentModelRendererTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun rendersBoxTextured() = assertRenders(SampleModels.BOX_TEXTURED)

    @Test
    fun rendersDuck() = assertRenders(SampleModels.DUCK)

    @Test
    fun rendersDracoDuck() = assertRenders(SampleModels.DUCK_DRACO)

    @Test
    fun rendersMeshoptDuck() = assertRenders(SampleModels.DUCK_MESHOPT)

    @Test
    fun loadsQuantizedMeshoptDuck() = load(SampleModels.DUCK_MESHOPT_QUANTIZED) { _, _ -> }

    /** gltfio's own box is 32767x too large for this model; the renderer frames it from [GlbBounds]. */
    @Test
    fun rendersQuantizedMeshoptDuck() = assertRenders(SampleModels.DUCK_MESHOPT_QUANTIZED)

    private fun assertRenders(name: String) = load(name) { surfaceView, environment ->
        val changed = awaitVisibleChange(surfaceView, environment)
        Log.i(TAG, "$name: ${"%.2f".format(changed * 100)}% of the surface changed after the load")
        assertTrue(
            "$name: only ${"%.2f".format(changed * 100)}% of the surface changed after the load, " +
                "expected at least ${MIN_VISIBLE_FRACTION * 100}% (model not visible)",
            changed >= MIN_VISIBLE_FRACTION
        )
        assertTrue(
            "$name: ${"%.1f".format(changed * 100)}% of the surface changed after the load; the " +
                "environment should still be visible around the model",
            changed <= MAX_VISIBLE_FRACTION
        )
    }

    /**
     * Loads [name] through the renderer and waits for [FilamentModelRenderer.onModelLoaded], then
     * hands the live surface and the environment-only frame captured before the load to [verify].
     */
    private fun load(name: String, verify: (SurfaceView, Bitmap) -> Unit) {
        val file = SampleModels.copyToCache(context, name)
        val loaded = CountDownLatch(1)
        try {
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                lateinit var surfaceView: SurfaceView
                lateinit var renderer: FilamentModelRenderer
                scenario.onActivity { activity ->
                    surfaceView = SurfaceView(activity)
                    activity.setContentView(surfaceView)
                    renderer = FilamentModelRenderer(surfaceView).apply {
                        onModelLoaded = { loaded.countDown() }
                        start()
                    }
                }
                val environment = awaitFirstFrame(surfaceView, name)

                runBlocking { withContext(Dispatchers.Main) { renderer.loadGlb(file) } }
                assertTrue(
                    "$name: onModelLoaded not reached within $LOAD_TIMEOUT_SECONDS s",
                    loaded.await(LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                )
                verify(surfaceView, environment)
            }
        } finally {
            file.delete()
        }
    }

    /** The environment (IBL skybox) is the first thing on screen; it is not a flat colour. */
    private fun awaitFirstFrame(surfaceView: SurfaceView, name: String): Bitmap {
        val deadline = SystemClock.uptimeMillis() + FIRST_FRAME_TIMEOUT_SECONDS * 1000
        while (true) {
            val bitmap = capture(surfaceView)
            if (bitmap != null && distinctColours(bitmap) >= MIN_ENVIRONMENT_COLOURS) return bitmap
            assertTrue("$name: no rendered frame within $FIRST_FRAME_TIMEOUT_SECONDS s", SystemClock.uptimeMillis() < deadline)
            SystemClock.sleep(CAPTURE_INTERVAL_MS)
        }
    }

    /** Renderables reach the scene over the frames after onModelLoaded; polls until they show. */
    private fun awaitVisibleChange(surfaceView: SurfaceView, environment: Bitmap): Float {
        val deadline = SystemClock.uptimeMillis() + VISIBLE_TIMEOUT_SECONDS * 1000
        var changed = 0f
        while (SystemClock.uptimeMillis() < deadline) {
            capture(surfaceView)?.let { changed = changedFraction(environment, it) }
            if (changed >= MIN_VISIBLE_FRACTION) break
            SystemClock.sleep(CAPTURE_INTERVAL_MS)
        }
        return changed
    }

    private fun capture(surfaceView: SurfaceView): Bitmap? {
        if (!surfaceView.holder.surface.isValid) return null
        val bitmap = Bitmap.createBitmap(CAPTURE_SIZE, CAPTURE_SIZE, Bitmap.Config.ARGB_8888)
        val done = CountDownLatch(1)
        var result = PixelCopy.ERROR_UNKNOWN
        PixelCopy.request(surfaceView, bitmap, { result = it; done.countDown() }, Handler(Looper.getMainLooper()))
        if (!done.await(CAPTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS) || result != PixelCopy.SUCCESS) return null
        return bitmap
    }

    private fun distinctColours(bitmap: Bitmap): Int {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.toHashSet().size
    }

    private fun changedFraction(before: Bitmap, after: Bitmap): Float {
        val a = IntArray(before.width * before.height)
        val b = IntArray(after.width * after.height)
        before.getPixels(a, 0, before.width, 0, 0, before.width, before.height)
        after.getPixels(b, 0, after.width, 0, 0, after.width, after.height)
        val changed = a.indices.count { i -> channelDelta(a[i], b[i]) > PIXEL_DELTA_THRESHOLD }
        return changed.toFloat() / a.size
    }

    private fun channelDelta(x: Int, y: Int): Int = maxOf(
        abs(((x shr 16) and 0xFF) - ((y shr 16) and 0xFF)),
        abs(((x shr 8) and 0xFF) - ((y shr 8) and 0xFF)),
        abs((x and 0xFF) - (y and 0xFF))
    )

    private companion object {
        const val TAG = "FilamentModelRendererTest"
        const val LOAD_TIMEOUT_SECONDS = 20L
        const val FIRST_FRAME_TIMEOUT_SECONDS = 20L
        const val VISIBLE_TIMEOUT_SECONDS = 20L
        const val CAPTURE_TIMEOUT_SECONDS = 5L
        const val CAPTURE_INTERVAL_MS = 250L
        const val CAPTURE_SIZE = 256
        const val MIN_ENVIRONMENT_COLOURS = 64
        const val PIXEL_DELTA_THRESHOLD = 48
        const val MIN_VISIBLE_FRACTION = 0.02f
        const val MAX_VISIBLE_FRACTION = 0.90f
    }
}

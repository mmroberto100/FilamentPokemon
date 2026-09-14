package com.mmunoz.filamentpokemon.viewer.filament

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Renderer
import com.google.android.filament.SwapChainFlags
import com.google.android.filament.Texture
import com.google.android.filament.Viewport
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import com.mmunoz.filamentpokemon.FilamentPokemonApp
import com.mmunoz.filamentpokemon.viewer.domain.GlbHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Drives gltfio directly (no SurfaceView) on the instrumentation thread: parse, decode, upload
 * and rasterise each bundled sample into a headless swap chain. A compressed duck must produce
 * the same renderables, bounds and silhouette as the plain Duck; that, not merely a non-null
 * asset, is what shows the Draco / meshopt decoders built into gltfio actually ran.
 */
@RunWith(AndroidJUnit4::class)
class GltfDecoderTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun boxTexturedDecodes() {
        decode(SampleModels.BOX_TEXTURED).assertHasGeometry()
    }

    @Test
    fun duckDecodes() {
        decode(SampleModels.DUCK).assertHasGeometry()
    }

    @Test
    fun dracoDuckDecodesToTheSameGeometryAsThePlainDuck() {
        val plain = decode(SampleModels.DUCK)
        val draco = decode(SampleModels.DUCK_DRACO)
        draco.assertHasGeometry()
        draco.assertBoundsMatch(plain)
        draco.assertSilhouetteMatches(plain)
    }

    @Test
    fun meshoptDuckDecodesToTheSameGeometryAsThePlainDuck() {
        val plain = decode(SampleModels.DUCK)
        val meshopt = decode(SampleModels.DUCK_MESHOPT)
        meshopt.assertHasGeometry()
        meshopt.assertBoundsMatch(plain)
        meshopt.assertSilhouetteMatches(plain)
    }

    @Test
    fun quantizedMeshoptDuckDecodesToTheSameGeometryAsThePlainDuck() {
        val plain = decode(SampleModels.DUCK)
        // Framed by the plain duck's bounds: the quantized asset's own are wrong (see the ignored test).
        val quantized = decode(SampleModels.DUCK_MESHOPT_QUANTIZED, frameLike = plain)
        quantized.assertHasGeometry()
        quantized.assertSilhouetteMatches(plain)
    }

    @Ignore(
        "gltfio 1.75.1 builds FilamentAsset.boundingBox from the POSITION accessor's raw min/max " +
            "(AssetLoader.cpp, createPrimitive) and ignores `normalized`, so KHR_mesh_quantization " +
            "models with SHORT positions report bounds 32767x too large; still present on upstream main"
    )
    @Test
    fun quantizedMeshoptDuckReportsTheSameBoundsAsThePlainDuck() {
        val plain = decode(SampleModels.DUCK)
        val quantized = decode(SampleModels.DUCK_MESHOPT_QUANTIZED, frameLike = plain)
        quantized.assertBoundsMatch(plain)
    }

    @Test
    fun corruptGlbPassesTheHeaderCheckButFailsInsideGltfio() {
        val file = SampleModels.copyToCache(context, SampleModels.CORRUPT)
        try {
            assertTrue("corrupt.glb must carry a valid glTF-Binary header", GlbHeader.isValid(file))
            withGltfio { _, assetLoader, _ ->
                assertNull(assetLoader.createAsset(SampleModels.readDirectBuffer(context, SampleModels.CORRUPT)))
            }
        } finally {
            file.delete()
        }
    }

    private class Decoded(
        val name: String,
        val renderables: Int,
        val primitives: Int,
        val center: FloatArray,
        val halfExtent: FloatArray,
        val coveredPixels: Int
    ) {
        val coverage get() = coveredPixels.toFloat() / (RENDER_SIZE * RENDER_SIZE)

        fun assertHasGeometry() {
            assertTrue("$name: no renderable entities", renderables > 0)
            assertTrue("$name: no primitives", primitives > 0)
            assertTrue(
                "$name: silhouette covers ${"%.1f".format(coverage * 100)}% of the frame, expected " +
                    "${MIN_COVERAGE * 100}%..${MAX_COVERAGE * 100}%",
                coverage in MIN_COVERAGE..MAX_COVERAGE
            )
        }

        fun assertBoundsMatch(reference: Decoded) {
            val tolerance = reference.halfExtent.max() * BOUNDS_TOLERANCE
            for (axis in 0..2) {
                assertEquals(
                    "$name: bounding box centre[$axis] ${center.toList()} differs from ${reference.name} ${reference.center.toList()}",
                    reference.center[axis], center[axis], tolerance
                )
                assertEquals(
                    "$name: bounding box half extent[$axis] ${halfExtent.toList()} differs from ${reference.name} ${reference.halfExtent.toList()}",
                    reference.halfExtent[axis], halfExtent[axis], tolerance
                )
            }
        }

        fun assertSilhouetteMatches(reference: Decoded) {
            assertEquals("$name: renderable count differs from ${reference.name}", reference.renderables, renderables)
            assertEquals("$name: primitive count differs from ${reference.name}", reference.primitives, primitives)
            val drift = abs(coveredPixels - reference.coveredPixels).toFloat() / reference.coveredPixels
            assertTrue(
                "$name: silhouette differs from ${reference.name} by ${"%.1f".format(drift * 100)}% " +
                    "($coveredPixels vs ${reference.coveredPixels} pixels)",
                drift <= SILHOUETTE_TOLERANCE
            )
        }
    }

    /**
     * Loads [name] and rasterises it. The camera frames the asset's own bounding box unless
     * [frameLike] supplies a reference to frame instead.
     */
    private fun decode(name: String, frameLike: Decoded? = null): Decoded =
        withGltfio { engine, assetLoader, resourceLoader ->
            val asset = assetLoader.createAsset(SampleModels.readDirectBuffer(context, name))
                ?: throw AssertionError("$name: gltfio could not parse the asset")
            try {
                // The synchronous Java loadResources() drops the native result; asyncBeginLoad returns it.
                assertTrue("$name: ResourceLoader rejected the asset", resourceLoader.asyncBeginLoad(asset))
                awaitResources(resourceLoader, name)

                val renderableManager = engine.renderableManager
                val renderables = asset.renderableEntities
                val primitives = renderables.sumOf { renderableManager.getPrimitiveCount(renderableManager.getInstance(it)) }
                val center = asset.boundingBox.center
                val halfExtent = asset.boundingBox.halfExtent
                val covered = renderSilhouette(
                    engine, asset,
                    frameLike?.center ?: center,
                    frameLike?.halfExtent ?: halfExtent
                )
                Decoded(name, renderables.size, primitives, center, halfExtent, covered).also {
                    Log.i(
                        TAG,
                        "$name: ${it.renderables} renderables, ${it.primitives} primitives, centre ${center.toList()}, " +
                            "half extent ${halfExtent.toList()}, silhouette ${"%.1f".format(it.coverage * 100)}%"
                    )
                }
            } finally {
                assetLoader.destroyAsset(asset)
            }
        }

    private inline fun <T> withGltfio(block: (Engine, AssetLoader, ResourceLoader) -> T): T {
        val engine = Engine.create()
        val materials = UbershaderProvider(engine)
        val assetLoader = AssetLoader(engine, materials, EntityManager.get())
        val resourceLoader = ResourceLoader(engine)
        try {
            return block(engine, assetLoader, resourceLoader)
        } finally {
            assetLoader.destroy()
            materials.destroyMaterials()
            materials.destroy()
            resourceLoader.destroy()
            engine.destroy()
        }
    }

    /** Textures decode on gltfio's worker threads and are only committed by asyncUpdateLoad(). */
    private fun awaitResources(resourceLoader: ResourceLoader, name: String) {
        val deadline = SystemClock.uptimeMillis() + RESOURCE_TIMEOUT_MS
        while (true) {
            resourceLoader.asyncUpdateLoad()
            if (resourceLoader.asyncGetLoadProgress() >= 1f) return
            assertTrue("$name: resources not resident after $RESOURCE_TIMEOUT_MS ms", SystemClock.uptimeMillis() < deadline)
            Thread.sleep(FRAME_INTERVAL_MS)
        }
    }

    /**
     * Renders the asset against a solid blue clear colour into a readable headless swap chain,
     * with the camera framing the given bounds, and returns how many pixels the model covers.
     */
    private fun renderSilhouette(engine: Engine, asset: FilamentAsset, center: FloatArray, halfExtent: FloatArray): Int {
        val entityManager = EntityManager.get()
        val swapChain = engine.createSwapChain(RENDER_SIZE, RENDER_SIZE, SwapChainFlags.CONFIG_READABLE)
        val renderer = engine.createRenderer()
        val scene = engine.createScene()
        val view = engine.createView()
        val cameraEntity = entityManager.create()
        val camera = engine.createCamera(cameraEntity)
        try {
            renderer.clearOptions = Renderer.ClearOptions().apply {
                clear = true
                clearColor = doubleArrayOf(0.0, 0.0, 1.0, 1.0)
            }
            frame(camera, center, halfExtent)
            view.viewport = Viewport(0, 0, RENDER_SIZE, RENDER_SIZE)
            view.scene = scene
            view.camera = camera
            view.isPostProcessingEnabled = false
            scene.addEntities(asset.entities)

            val pixels = ByteBuffer.allocateDirect(RENDER_SIZE * RENDER_SIZE * 4)
            val readBack = CountDownLatch(1)
            val descriptor = Texture.PixelBufferDescriptor(pixels, Texture.Format.RGBA, Texture.Type.UBYTE)
            descriptor.setCallback(Executor { it.run() }) { readBack.countDown() }

            // The read-back completes on a later frame than the one that issued it.
            var issued = false
            var frames = 0
            while (readBack.count > 0L && frames++ < MAX_READBACK_FRAMES) {
                if (renderer.beginFrame(swapChain, 0L)) {
                    renderer.render(view)
                    if (!issued) {
                        renderer.readPixels(0, 0, RENDER_SIZE, RENDER_SIZE, descriptor)
                        issued = true
                    }
                    renderer.endFrame()
                }
                engine.flushAndWait()
            }
            assertTrue("pixel read-back did not complete within $MAX_READBACK_FRAMES frames", readBack.await(0, TimeUnit.MILLISECONDS))
            return countCoveredPixels(pixels)
        } finally {
            scene.removeEntities(asset.entities)
            engine.destroyView(view)
            engine.destroyScene(scene)
            engine.destroyRenderer(renderer)
            engine.destroyCameraComponent(cameraEntity)
            entityManager.destroy(cameraEntity)
            engine.destroySwapChain(swapChain)
        }
    }

    /** Looks down -Z at the given bounds from far enough that they fit with margin. */
    private fun frame(camera: Camera, center: FloatArray, halfExtent: FloatArray) {
        val (cx, cy, cz) = center.map { it.toDouble() }
        val radius = halfExtent.max().toDouble()
        val distance = radius * CAMERA_DISTANCE_RADII
        camera.setProjection(45.0, 1.0, radius * NEAR_PLANE_RADII, distance + radius * 4, Camera.Fov.VERTICAL)
        camera.lookAt(cx, cy, cz + distance, cx, cy, cz, 0.0, 1.0, 0.0)
    }

    private fun countCoveredPixels(pixels: ByteBuffer): Int {
        var covered = 0
        pixels.rewind()
        while (pixels.hasRemaining()) {
            val r = pixels.get().toInt() and 0xFF
            val g = pixels.get().toInt() and 0xFF
            val b = pixels.get().toInt() and 0xFF
            pixels.get()
            val isClearColour = r < 16 && g < 16 && b > 239
            if (!isClearColour) covered++
        }
        return covered
    }

    private companion object {
        const val TAG = "GltfDecoderTest"
        const val RENDER_SIZE = 256
        const val CAMERA_DISTANCE_RADII = 5.0
        const val NEAR_PLANE_RADII = 0.1
        const val MIN_COVERAGE = 0.02f
        const val MAX_COVERAGE = 0.98f
        const val SILHOUETTE_TOLERANCE = 0.05f
        const val BOUNDS_TOLERANCE = 0.02f
        const val RESOURCE_TIMEOUT_MS = 30_000L
        const val FRAME_INTERVAL_MS = 16L
        const val MAX_READBACK_FRAMES = 120

        /** Filament.init()/Gltfio.init()/Utils.init() run in the app's Application.onCreate(). */
        @BeforeClass
        @JvmStatic
        fun applicationLoadedFilament() {
            val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
            assertTrue("instrumentation did not start FilamentPokemonApp", app is FilamentPokemonApp)
        }
    }
}

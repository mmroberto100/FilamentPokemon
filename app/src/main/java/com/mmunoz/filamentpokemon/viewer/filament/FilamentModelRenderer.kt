package com.mmunoz.filamentpokemon.viewer.filament

import android.content.res.AssetManager
import android.view.Choreographer
import android.view.SurfaceView
import android.view.View as AndroidView
import co.touchlab.kermit.Logger
import com.google.android.filament.Engine
import com.google.android.filament.IndirectLight
import com.google.android.filament.Skybox
import com.google.android.filament.View
import com.google.android.filament.utils.KTX1Loader
import com.google.android.filament.utils.ModelViewer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.TimeUnit

/**
 * Owns one Filament [Engine] + [ModelViewer] bound to a [SurfaceView], the Choreographer frame
 * loop, and the image-based lighting environment. Create it once per SurfaceView.
 *
 * Lifecycle: [start]/[stop] drive the frame loop (call from ON_RESUME/ON_PAUSE). Teardown is
 * deterministic and tied to the view: [ModelViewer] destroys the Engine and every GPU resource
 * when the SurfaceView is detached from its window, which Compose's AndroidView guarantees on
 * disposal. [release] runs just before that (registered first) to stop the loop and free the
 * environment while the engine is still alive. Never call `modelViewer.destroy()` twice.
 *
 * Every [loadGlb] ends in exactly one of [onModelLoaded] or [onModelLoadFailed], both invoked on
 * the main thread; neither fires after [release], and a later outcome for a superseded load is
 * dropped. The load timeout only counts time the frame loop is running, so a paused view never
 * times out.
 */
class FilamentModelRenderer(
    private val surfaceView: SurfaceView,
    private val assets: AssetManager = surfaceView.context.assets
) {
    private val log = Logger.withTag("FilamentRenderer")
    private val engine: Engine = Engine.create()
    private val modelViewer: ModelViewer
    private val choreographer = Choreographer.getInstance()

    private var indirectLight: IndirectLight? = null
    private var skybox: Skybox? = null

    private var running = false
    private var released = false

    /** Non-null from [loadGlb] until the model's outcome has been reported. */
    private var pendingLoad: PendingLoad? = null

    /** Set when gltfio's own bounding box is wrong for the current model (see [GlbBounds]). */
    private var correctedBounds: GlbBounds.Aabb? = null

    /** Invoked once on the main thread when all of the current model's resources are resident. */
    var onModelLoaded: (() -> Unit)? = null

    /** Invoked once on the main thread when the current model cannot be shown. */
    var onModelLoadFailed: ((ModelLoadError) -> Unit)? = null

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running || released) return
            choreographer.postFrameCallback(this)
            modelViewer.render(frameTimeNanos)
            pendingLoad?.let { checkPendingLoad(it, frameTimeNanos) }
        }
    }

    init {
        // Registered before ModelViewer's own detach listener so we run first, engine still alive.
        surfaceView.addOnAttachStateChangeListener(object : AndroidView.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: AndroidView) = Unit
            override fun onViewDetachedFromWindow(v: AndroidView) = release()
        })
        modelViewer = ModelViewer(surfaceView, engine)
        surfaceView.setOnTouchListener(modelViewer)
        configureForMobile(modelViewer.view)
        loadEnvironment()
    }

    fun start() {
        if (released || running) return
        running = true
        // The gap while paused must not count against the load timeout.
        pendingLoad?.lastFrameNanos = NO_FRAME
        choreographer.postFrameCallback(frameCallback)
    }

    fun stop() {
        running = false
        choreographer.removeFrameCallback(frameCallback)
    }

    /**
     * Reads [file] off the main thread, then hands the buffer to gltfio (parse is synchronous,
     * resource upload is asynchronous and completes over subsequent frames). Never throws: a file
     * that cannot be read reports [ModelLoadError.FileUnreadable] instead.
     */
    suspend fun loadGlb(file: File) {
        if (released) return
        val (buffer, bounds) = try {
            withContext(Dispatchers.IO) { file.readDirectBuffer().let { it to GlbBounds.correctedBox(it) } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.w(e) { "Cannot read ${file.name}" }
            reportFailure(ModelLoadError.FileUnreadable)
            return
        }
        loadGlb(buffer, bounds)
    }

    /**
     * Never throws: bytes gltfio cannot parse report [ModelLoadError.ParseFailed]. [bounds] is the
     * [GlbBounds] correction for the buffer, computed here when the caller has not done so off-thread.
     */
    fun loadGlb(buffer: ByteBuffer, bounds: GlbBounds.Aabb? = GlbBounds.correctedBox(buffer)) {
        if (released) return
        pendingLoad = PendingLoad()
        val parsed = try {
            modelViewer.loadModelGlb(buffer)
            modelViewer.asset != null
        } catch (e: Exception) {
            log.e(e) { "gltfio threw while parsing" }
            false
        }
        if (!parsed) {
            log.w { "gltfio rejected the buffer (${buffer.remaining()} bytes)" }
            reportFailure(ModelLoadError.ParseFailed)
            return
        }
        correctedBounds = bounds
        frameModel()
        log.d { "glb parsed: ${modelViewer.asset?.entities?.size ?: 0} entities, animations=${modelViewer.animator?.animationCount ?: 0}, correctedBounds=${correctedBounds != null}" }
    }

    fun resetCamera() {
        if (released) return
        frameModel()
    }

    /**
     * Scales and centres the model's root so it fills a unit cube at the camera target, exactly
     * like [ModelViewer.transformToUnitCube] but from [correctedBounds] when gltfio's box is unusable.
     */
    private fun frameModel() {
        val bounds = correctedBounds ?: return modelViewer.transformToUnitCube()
        val asset = modelViewer.asset ?: return
        val maxExtent = 2f * bounds.halfExtent.max()
        if (maxExtent <= 0f || !maxExtent.isFinite()) return modelViewer.transformToUnitCube()
        val scaleFactor = 2f / maxExtent
        // Column-major scale(scaleFactor) * translation(-centre): the centre lands on the camera target.
        val center = bounds.center
        val transform = FloatArray(16).also {
            it[0] = scaleFactor
            it[5] = scaleFactor
            it[10] = scaleFactor
            it[12] = -center[0] * scaleFactor + OBJECT_POSITION[0]
            it[13] = -center[1] * scaleFactor + OBJECT_POSITION[1]
            it[14] = -center[2] * scaleFactor + OBJECT_POSITION[2]
            it[15] = 1f
        }
        val tm = engine.transformManager
        tm.setTransform(tm.getInstance(asset.root), transform)
    }

    private fun checkPendingLoad(pending: PendingLoad, frameTimeNanos: Long) {
        if (modelViewer.asset != null && modelViewer.progress >= 1f) {
            pendingLoad = null
            onModelLoaded?.invoke()
            return
        }
        if (pending.lastFrameNanos != NO_FRAME) pending.awaitedNanos += frameTimeNanos - pending.lastFrameNanos
        pending.lastFrameNanos = frameTimeNanos
        if (pending.awaitedNanos >= LOAD_TIMEOUT_NANOS) {
            log.w { "model resources not resident after ${LOAD_TIMEOUT_MS} ms of rendering (progress=${modelViewer.progress})" }
            reportFailure(ModelLoadError.Timeout)
        }
    }

    private fun reportFailure(error: ModelLoadError) {
        pendingLoad = null
        if (released) return
        onModelLoadFailed?.invoke(error)
    }

    private fun configureForMobile(view: View) {
        // Mobile budget: dynamic resolution + FXAA instead of MSAA, no post-processing extras.
        view.dynamicResolutionOptions = View.DynamicResolutionOptions().apply {
            enabled = true
            quality = View.QualityLevel.MEDIUM
        }
        view.antiAliasing = View.AntiAliasing.FXAA
        view.multiSampleAntiAliasingOptions = View.MultiSampleAntiAliasingOptions().apply { enabled = false }
        view.ambientOcclusionOptions = View.AmbientOcclusionOptions().apply { enabled = false }
        view.bloomOptions = View.BloomOptions().apply { enabled = false }
    }

    private fun loadEnvironment() {
        runCatching {
            val ibl = KTX1Loader.createIndirectLight(engine, assets.readDirectBuffer("$ENV_DIR/${ENV_NAME}_ibl.ktx"))
            ibl.indirectLight?.intensity = IBL_INTENSITY
            indirectLight = ibl.indirectLight
            modelViewer.indirectLightCubemap = ibl.cubemap
            modelViewer.scene.indirectLight = ibl.indirectLight

            val sky = KTX1Loader.createSkybox(engine, assets.readDirectBuffer("$ENV_DIR/${ENV_NAME}_skybox.ktx"))
            skybox = sky.skybox
            modelViewer.skyboxCubemap = sky.cubemap
            modelViewer.scene.skybox = sky.skybox
        }.onFailure { log.e(it) { "Failed to load IBL environment; rendering with the directional light only" } }
    }

    /** Frees what ModelViewer doesn't own; ModelViewer's detach listener then destroys the engine. */
    private fun release() {
        if (released) return
        released = true
        stop()
        pendingLoad = null
        onModelLoaded = null
        onModelLoadFailed = null
        modelViewer.scene.indirectLight = null
        modelViewer.scene.skybox = null
        indirectLight?.let { engine.destroyIndirectLight(it) }
        skybox?.let { engine.destroySkybox(it) }
        indirectLight = null
        skybox = null
    }

    private fun File.readDirectBuffer(): ByteBuffer =
        FileChannel.open(toPath()).use { channel ->
            val buffer = ByteBuffer.allocateDirect(channel.size().toInt())
            while (buffer.hasRemaining() && channel.read(buffer) >= 0) Unit
            buffer.flip()
            buffer
        }

    private fun AssetManager.readDirectBuffer(path: String): ByteBuffer =
        open(path).use { input ->
            val bytes = input.readBytes()
            ByteBuffer.allocateDirect(bytes.size).put(bytes).apply { flip() }
        }

    /** Frame-loop time spent waiting for the current model's resources. */
    private class PendingLoad {
        var awaitedNanos = 0L
        var lastFrameNanos = NO_FRAME
    }

    companion object {
        /** Frame-loop time after which a model whose resources never become resident is given up on. */
        const val LOAD_TIMEOUT_MS = 20_000L

        private const val NO_FRAME = -1L

        /** ModelViewer's camera target (its private kDefaultObjectPosition). */
        private val OBJECT_POSITION = floatArrayOf(0f, 0f, -4f)
        private val LOAD_TIMEOUT_NANOS = TimeUnit.MILLISECONDS.toNanos(LOAD_TIMEOUT_MS)
        private const val ENV_DIR = "envs/default_env"
        private const val ENV_NAME = "default_env"
        private const val IBL_INTENSITY = 30_000f
    }
}

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * Owns one Filament [Engine] + [ModelViewer] bound to a [SurfaceView], the Choreographer frame
 * loop, and the image-based lighting environment. Create it once per SurfaceView.
 *
 * Lifecycle: [start]/[stop] drive the frame loop (call from ON_RESUME/ON_PAUSE). Teardown is
 * deterministic and tied to the view: [ModelViewer] destroys the Engine and every GPU resource
 * when the SurfaceView is detached from its window, which Compose's AndroidView guarantees on
 * disposal. [release] runs just before that (registered first) to stop the loop and free the
 * environment while the engine is still alive. Never call `modelViewer.destroy()` twice.
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
    private var modelLoadedReported = false

    /** Invoked once on the main thread when all of the current model's resources are resident. */
    var onModelLoaded: (() -> Unit)? = null

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running || released) return
            choreographer.postFrameCallback(this)
            modelViewer.render(frameTimeNanos)
            if (!modelLoadedReported && modelViewer.asset != null && modelViewer.progress >= 1f) {
                modelLoadedReported = true
                onModelLoaded?.invoke()
            }
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
        choreographer.postFrameCallback(frameCallback)
    }

    fun stop() {
        running = false
        choreographer.removeFrameCallback(frameCallback)
    }

    /**
     * Reads [file] off the main thread, then hands the buffer to gltfio (parse is synchronous,
     * resource upload is asynchronous and completes over subsequent frames).
     */
    suspend fun loadGlb(file: File) {
        val buffer = withContext(Dispatchers.IO) { file.readDirectBuffer() }
        loadGlb(buffer)
    }

    fun loadGlb(buffer: ByteBuffer) {
        if (released) return
        modelLoadedReported = false
        modelViewer.loadModelGlb(buffer)
        modelViewer.transformToUnitCube()
        log.d { "glb loaded: ${modelViewer.asset?.entities?.size ?: 0} entities, animations=${modelViewer.animator?.animationCount ?: 0}" }
    }

    fun resetCamera() {
        if (released) return
        modelViewer.transformToUnitCube()
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
        onModelLoaded = null
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

    private companion object {
        const val ENV_DIR = "envs/default_env"
        const val ENV_NAME = "default_env"
        const val IBL_INTENSITY = 30_000f
    }
}

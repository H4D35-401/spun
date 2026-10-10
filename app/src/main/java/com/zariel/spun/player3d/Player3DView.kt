package com.zariel.spun.player3d

import android.content.Context
import android.view.Choreographer
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.SwapChain
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import java.nio.ByteBuffer
import java.nio.ByteOrder
import com.google.android.filament.LightManager
import android.view.MotionEvent
import android.util.Log
import kotlin.math.cos
import kotlin.math.sin
import com.zariel.spun.playback.NowPlayingState
import com.zariel.spun.playback.ExternalMediaSessions
import com.google.android.filament.utils.KTX1Loader

private const val TAG = "Player3DView"

/**
 * Filament-powered SurfaceView that renders the TP-7-inspired 3D reel player.
 *
 * Accepts [NowPlayingState] instead of a raw [PlaybackController] so it works
 * with both internal and external playback sources without knowing about either.
 *
 * Call [updateNowPlaying] from the Compose host whenever state changes.
 * Call [destroy] from the AndroidView onRelease lambda.
 */
class Player3DView(
    private val appContext: Context,
    /** Callback fired when the user drags the reel — (deltaMs: Long) -> Unit */
    private val onReelSeek: (Long) -> Unit,
    private val onReelScrubStart: () -> Unit,
    private val onReelScrubEnd: () -> Unit,
) : SurfaceView(appContext) {

    companion object {
        init {
            System.loadLibrary("filament-jni")
            System.loadLibrary("gltfio-jni")
            System.loadLibrary("filament-utils-jni")
        }
    }

    // --- Filament objects ------------------------------------------------------------------

    private val engine = Engine.create()
    private val renderer = engine.createRenderer()
    private val view = engine.createView()
    private val scene = engine.createScene()
    private val camera = engine.createCamera(EntityManager.get().create())

    private var indirectLight: com.google.android.filament.IndirectLight? = null
    private var swapChain: SwapChain? = null

    private val materialProvider = UbershaderProvider(engine)
    private val assetLoader = AssetLoader(engine, materialProvider, EntityManager.get())
    private val resourceLoader = ResourceLoader(engine)

    private var asset: FilamentAsset? = null

    // --- Camera orbit state ----------------------------------------------------------------

    private var orbitYaw    = 0.0
    private var orbitPitch  = 0.35
    private var orbitDistance = 0.25

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var touching = false

    // --- Reel state ------------------------------------------------------------------------

    private var reelEntity = 0
    private var reelOriginalTransform = FloatArray(16)
    private var reelRotation = 0.0

    private var reelDragging = false
    private var lastReelTouchAngle = 0.0

    private var isPlaying = false
    private var trackChangeImpulse = 0.0
    private var lastTrackKey = ""
    private var hasSeenTrack = false
    private var resumeImpulse = 0.0

    private var reelVelocity = 0.0
    private val targetReelVelocity = 0.03
    private val reelDragFriction = 0.92
    private var mechanicalPhase = 0.0

    private val scratchAudio = ReelScratchAudio()

    // --- Frame callback --------------------------------------------------------------------

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            renderFrame()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    // --- Initialisation --------------------------------------------------------------------

    init {
        view.scene = scene
        view.camera = camera
        view.blendMode = View.BlendMode.TRANSLUCENT

        // Load IBL
        val iblBuffer = appContext.assets.open("envs/studio/studio_ibl.ktx").use { input ->
            ByteBuffer.wrap(input.readBytes())
        }
        val ibl = KTX1Loader.createIndirectLight(engine, iblBuffer)
        indirectLight = ibl.indirectLight
        indirectLight!!.intensity = 30_000f
        scene.indirectLight = indirectLight

        camera.setProjection(45.0, 1.0, 0.1, 100.0, Camera.Fov.VERTICAL)
        updateCamera()
        loadModel()

        setZOrderOnTop(true)
        holder.setFormat(android.graphics.PixelFormat.TRANSLUCENT)

        holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                swapChain = engine.createSwapChain(holder.surface)
                Choreographer.getInstance().postFrameCallback(frameCallback)
            }

            override fun surfaceChanged(
                holder: SurfaceHolder, format: Int, width: Int, height: Int,
            ) {
                if (height > 0) {
                    camera.setProjection(
                        45.0,
                        width.toDouble() / height.toDouble(),
                        0.1,
                        100.0,
                        Camera.Fov.VERTICAL,
                    )
                }
                view.viewport = Viewport(0, 0, width, height)
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                Choreographer.getInstance().removeFrameCallback(frameCallback)
                swapChain?.let { engine.destroySwapChain(it) }
                swapChain = null
            }
        })

        setOnTouchListener { _, event ->
            when (event.actionMasked) {

                MotionEvent.ACTION_DOWN -> {
                    val centerX = width / 2f
                    val centerY = height / 2f
                    val dx = event.x - centerX
                    val dy = event.y - centerY
                    val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                    val reelRadius = width.coerceAtMost(height) * 0.35f

                    if (distance < reelRadius) {
                        reelDragging = true
                        onReelScrubStart()
                        lastReelTouchAngle = kotlin.math.atan2(dy.toDouble(), dx.toDouble())
                    } else {
                        lastTouchX = event.x
                        lastTouchY = event.y
                        touching = true
                    }
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (reelDragging) {
                        val centerX = width / 2f
                        val centerY = height / 2f
                        val dx = event.x - centerX
                        val dy = event.y - centerY
                        val angle = kotlin.math.atan2(dy.toDouble(), dx.toDouble())

                        var delta = angle - lastReelTouchAngle
                        if (delta > Math.PI)  delta -= Math.PI * 2
                        if (delta < -Math.PI) delta += Math.PI * 2

                        reelRotation += delta
                        scratchAudio.update(delta)

                        // Convert angular delta to milliseconds and report to host.
                        onReelSeek((delta * 2500.0).toLong())

                        // Hand momentum to physics so the reel coasts after release.
                        reelVelocity = delta * 0.35 * reelDragFriction

                        lastReelTouchAngle = angle
                        applyReelRotation()
                        true
                    } else if (touching) {
                        val dx = event.x - lastTouchX
                        val dy = event.y - lastTouchY
                        orbitYaw   -= dx * 0.01
                        orbitPitch  = (orbitPitch + dy * 0.01).coerceIn(-1.4, 1.4)
                        lastTouchX = event.x
                        lastTouchY = event.y
                        updateCamera()
                        true
                    } else true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    reelDragging = false
                    scratchAudio.stop()
                    onReelScrubEnd()
                    touching = false
                    true
                }

                else -> false
            }
        }
    }

    // --- Public API ------------------------------------------------------------------------

    /**
     * Called by the Compose host whenever [NowPlayingState] changes.
     * Updates the reel physics variables so the animation reflects the current state.
     */
    fun updateNowPlaying(state: NowPlayingState) {
        val wasPlaying = isPlaying
        isPlaying = state.isPlaying

        if (!wasPlaying && isPlaying && hasSeenTrack) {
            resumeImpulse = 0.006
        }

        val trackKey = "${state.title}|${state.artist}"
        if (hasSeenTrack && trackKey != lastTrackKey) {
            trackChangeImpulse = 0.01
        }

        lastTrackKey = trackKey
        hasSeenTrack = true
    }

    // --- Filament internals ----------------------------------------------------------------

    private fun applyReelRotation() {
        if (reelEntity == 0) return
        val tm = engine.transformManager
        val instance = tm.getInstance(reelEntity)
        if (instance == 0) return

        val c = cos(reelRotation).toFloat()
        val s = sin(reelRotation).toFloat()
        val m = reelOriginalTransform
        val sx = m[0]; val sy = m[5]; val sz = m[10]

        tm.setTransform(instance, floatArrayOf(
            sx * c,  0f,     sx * s,  0f,
            0f,      sy,     0f,      0f,
            -sz * s, 0f,     sz * c,  0f,
            m[12],   m[13],  m[14],   1f,
        ))
    }

    private fun updateCamera() {
        val x = orbitDistance * cos(orbitPitch) * sin(orbitYaw)
        val y = orbitDistance * sin(orbitPitch)
        val z = orbitDistance * cos(orbitPitch) * cos(orbitYaw)
        camera.lookAt(x, y, z, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0)
    }

    private fun loadModel() {
        val gltfData = appContext.assets.open("models/tp7/scene.gltf").readBytes()
        val gltfBuffer = ByteBuffer.allocateDirect(gltfData.size)
            .order(ByteOrder.nativeOrder())
            .apply { put(gltfData); flip() }

        asset = assetLoader.createAsset(gltfBuffer)
            ?: throw IllegalStateException("Failed to load TP-7 glTF")

        for (uri in asset!!.resourceUris) {
            val data = appContext.assets.open("models/tp7/$uri").readBytes()
            val buffer = ByteBuffer.allocateDirect(data.size)
                .order(ByteOrder.nativeOrder())
                .apply { put(data); flip() }
            resourceLoader.addResourceData(uri, buffer)
        }

        resourceLoader.loadResources(asset!!)
        scene.addEntities(asset!!.entities)

        reelEntity = asset!!.getFirstEntityByName("Cylinder.009_35")
        Log.d(TAG, "Reel entity: $reelEntity (${if (reelEntity != 0) "found" else "NOT FOUND"})")

        if (reelEntity != 0) {
            val tm = engine.transformManager
            val instance = tm.getInstance(reelEntity)
            if (instance != 0) {
                tm.getTransform(instance, reelOriginalTransform)
                Log.d(TAG, "Reel original transform captured")
            }
        }

        val bounds = asset!!.boundingBox
        Log.d(TAG, "Model bounds: center=${bounds.center.contentToString()} " +
                "halfExtent=${bounds.halfExtent.contentToString()}")
    }

    private fun renderFrame() {
        val chain = swapChain ?: return

        if (reelEntity != 0 && !reelDragging) {
            mechanicalPhase += 0.035
            trackChangeImpulse *= 0.96
            resumeImpulse      *= 0.94

            val trackResponse = trackChangeImpulse + resumeImpulse
            val mechanicalVariation =
                if (isPlaying) sin(mechanicalPhase) * 0.0004 else 0.0

            val targetVelocity = if (isPlaying) {
                targetReelVelocity + mechanicalVariation + trackResponse
            } else 0.0

            reelVelocity += (targetVelocity - reelVelocity) * 0.08
            reelRotation += reelVelocity
            applyReelRotation()
        }

        if (renderer.beginFrame(chain, System.nanoTime())) {
            renderer.render(view)
            renderer.endFrame()
        }
    }

    // --- Resource cleanup ------------------------------------------------------------------

    /**
     * Must be called from AndroidView's onRelease lambda to prevent Filament leaks.
     */
    fun destroy() {
        scratchAudio.release()
        Choreographer.getInstance().removeFrameCallback(frameCallback)

        swapChain?.let { engine.destroySwapChain(it) }
        swapChain = null

        asset?.let { assetLoader.destroyAsset(it) }

        resourceLoader.destroy()
        assetLoader.destroy()
        materialProvider.destroy()

        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroyCameraComponent(camera.entity)
        engine.destroyRenderer(renderer)
        indirectLight?.let { engine.destroyIndirectLight(it) }

        engine.destroy()
        Log.d(TAG, "Filament resources destroyed")
    }
}

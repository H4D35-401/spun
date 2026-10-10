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
import kotlin.math.cos
import kotlin.math.sin
import com.zariel.spun.playback.PlaybackController
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.google.android.filament.utils.KTX1Loader
import com.zariel.spun.playback.ExternalMediaSessions

class Player3DView(
    private val appContext: Context,
        private val playbackController: PlaybackController
) : SurfaceView(appContext) {

    companion object {
        init {
            System.loadLibrary("filament-jni")
            System.loadLibrary("gltfio-jni")
            System.loadLibrary("filament-utils-jni")
        }
    }

    private val engine = Engine.create()
    private val renderer = engine.createRenderer()

    private val view = engine.createView()
    private val scene = engine.createScene()

    private val camera = engine.createCamera(
        EntityManager.get().create()
    )


    private var indirectLight: com.google.android.filament.IndirectLight? = null

    private var swapChain: SwapChain? = null

        private var orbitYaw = 0.0
        private var orbitPitch = 0.35
        private var orbitDistance = 0.25

        private var lastTouchX = 0f
        private var lastTouchY = 0f
        private var touching = false

        private val materialProvider =
        UbershaderProvider(engine)

        private val assetLoader = AssetLoader(
            engine,
            materialProvider,
            EntityManager.get()
        )

        private val resourceLoader =
        ResourceLoader(engine)

        private var asset: FilamentAsset? = null

        //private var debugEntity = 0
        //private var debugRotation = 0.0
        //private var debugOriginalTransform = FloatArray(16)

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


        //private var debugRotation = 0.0

        //private var debugEntity = 0

            private val frameCallback =
            object : Choreographer.FrameCallback {

                override fun doFrame(frameTimeNanos: Long) {
                    renderFrame()

                    Choreographer.getInstance()
                    .postFrameCallback(this)
                }
            }

            init {

                if (appContext is LifecycleOwner) {
                    appContext.lifecycleScope.launch {
                        playbackController.playbackState.collect { state ->
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
                    }
                }

                view.scene = scene
                view.camera = camera
                view.blendMode = View.BlendMode.TRANSLUCENT

                val iblBuffer = appContext.assets
                .open("envs/studio/studio_ibl.ktx")
                .use { input ->
                    val bytes = ByteArray(input.available())
                    input.read(bytes)
                    ByteBuffer.wrap(bytes)
                }

                val ibl = KTX1Loader.createIndirectLight(
                    engine,
                    iblBuffer
                )

                indirectLight = ibl.indirectLight
                indirectLight!!.intensity = 30_000.0f
                scene.indirectLight = indirectLight

                camera.setProjection(
                    45.0,
                    1.0,
                    0.1,
                    100.0,
                    Camera.Fov.VERTICAL
                )



                updateCamera()

                loadModel()

                setZOrderOnTop(true)
                holder.setFormat(android.graphics.PixelFormat.TRANSLUCENT)

                holder.addCallback(
                    object : SurfaceHolder.Callback {

                        override fun surfaceCreated(
                            holder: SurfaceHolder
                        ) {
                            swapChain =
                            engine.createSwapChain(holder.surface)

                            Choreographer.getInstance()
                            .postFrameCallback(frameCallback)
                        }

                        override fun surfaceChanged(
                            holder: SurfaceHolder,
                            format: Int,
                                width: Int,
                                height: Int
                        ) {
                            if (height > 0) {
                                camera.setProjection(
                                    45.0,
                                    width.toDouble() / height.toDouble(),
                                                     0.1,
                                                     100.0,
                                                     Camera.Fov.VERTICAL
                                )
                            }

                            view.viewport = Viewport(
                                0,
                                0,
                                width,
                                height
                            )
                        }

                        override fun surfaceDestroyed(
                            holder: SurfaceHolder
                        ) {
                            Choreographer.getInstance()
                            .removeFrameCallback(frameCallback)

                            swapChain?.let {
                                engine.destroySwapChain(it)
                            }

                            swapChain = null
                        }
                    }
                )

                setOnTouchListener { _, event ->

                    when (event.actionMasked) {

                        MotionEvent.ACTION_DOWN -> {
                            val centerX = width / 2f
                            val centerY = height / 2f

                            val dx = event.x - centerX
                            val dy = event.y - centerY

                            val distance = kotlin.math.sqrt(
                                dx * dx + dy * dy
                            )

                            // Reel hit area for now.
                            val reelRadius = width.coerceAtMost(height) * 0.35f

                            if (distance < reelRadius) {
                                reelDragging = true
                                ExternalMediaSessions.beginScrub()

                                lastReelTouchAngle = kotlin.math.atan2(
                                    dy.toDouble(),
                                                                       dx.toDouble()
                                )

                                true
                            } else {
                                lastTouchX = event.x
                                lastTouchY = event.y
                                touching = true
                                true
                            }
                        }

                        MotionEvent.ACTION_MOVE -> {

                            if (reelDragging) {
                                val centerX = width / 2f
                                val centerY = height / 2f

                                val dx = event.x - centerX
                                val dy = event.y - centerY

                                val angle = kotlin.math.atan2(
                                    dy.toDouble(),
                                                              dx.toDouble()
                                )

                                var delta = angle - lastReelTouchAngle

                                if (delta > Math.PI) {
                                    delta -= Math.PI * 2
                                }

                                if (delta < -Math.PI) {
                                    delta += Math.PI * 2
                                }

                                //reelRotation += delta
                                //lastReelTouchAngle = angle

                                reelRotation += delta
                                scratchAudio.update(delta)
                                ExternalMediaSessions.seekBy(
                                    (delta * 2500.0).toLong()
                                )

                                // Transfer some of the finger movement into physical momentum.
                                reelVelocity = delta * 0.35 * reelDragFriction

                                lastReelTouchAngle = angle

                                applyReelRotation()

                                true
                            } else if (touching) {

                                val dx = event.x - lastTouchX
                                val dy = event.y - lastTouchY

                                orbitYaw -= dx * 0.01
                                orbitPitch += dy * 0.01

                                orbitPitch = orbitPitch.coerceIn(
                                    -1.4,
                                    1.4
                                )

                                lastTouchX = event.x
                                lastTouchY = event.y

                                updateCamera()

                                true
                            } else {
                                true
                            }
                        }

                        MotionEvent.ACTION_UP,
                        MotionEvent.ACTION_CANCEL -> {
                            reelDragging = false
                            scratchAudio.stop()
                            ExternalMediaSessions.endScrub()
                            touching = false
                            true
                        }

                        else -> false
                    }
                }
            }

            private fun printParentChain(entity: Int) {
                val tm = engine.transformManager
                var current = entity

                while (current != 0) {
                    val name = asset?.getName(current) ?: "unknown"
                    val instance = tm.getInstance(current)

                    println("REEL NODE: entity=$current name=$name instance=$instance")

                    if (instance == 0) break

                        current = tm.getParent(instance)
                }
            }

            private fun applyReelRotation() {
                if (reelEntity == 0) return

                    val tm = engine.transformManager
                    val instance = tm.getInstance(reelEntity)

                    if (instance == 0) return

                        val c = cos(reelRotation).toFloat()
                        val s = sin(reelRotation).toFloat()

                        val m = reelOriginalTransform

                        val sx = m[0]
                        val sy = m[5]
                        val sz = m[10]

                        val result = floatArrayOf(
                            sx * c,  0f,     sx * s,  0f,
                            0f,      sy,     0f,      0f,
                            -sz * s,  0f,     sz * c,  0f,
                            m[12],   m[13],  m[14],   1f
                        )

                        tm.setTransform(instance, result)
            }

            private fun updateCamera() {

                val targetX = 0.0
                val targetY = 0.0
                val targetZ = 0.0

                val x =
                targetX +
                orbitDistance *
                cos(orbitPitch) *
                sin(orbitYaw)

                val y =
                targetY +
                orbitDistance *
                sin(orbitPitch)

                val z =
                targetZ +
                orbitDistance *
                cos(orbitPitch) *
                cos(orbitYaw)

                camera.lookAt(
                    x,
                    y,
                    z,
                    targetX,
                    targetY,
                    targetZ,
                    0.0,
                    1.0,
                    0.0
                )
            }

            private fun loadModel() {
                val gltfData = appContext.assets
                .open("models/tp7/scene.gltf")
                .use {
                    it.readBytes()
                }

                val gltfBuffer = ByteBuffer
                .allocateDirect(gltfData.size)
                .order(ByteOrder.nativeOrder())

                gltfBuffer.put(gltfData)
                gltfBuffer.flip()

                asset = assetLoader.createAsset(gltfBuffer)

                if (asset == null) {
                    throw IllegalStateException(
                        "Failed to load TP-7 glTF"
                    )
                }

                val resourceUris = asset!!.resourceUris

                for (uri in resourceUris) {
                    val data = appContext.assets
                    .open("models/tp7/$uri")
                    .use {
                        it.readBytes()
                    }

                    val buffer = ByteBuffer
                    .allocateDirect(data.size)
                    .order(ByteOrder.nativeOrder())

                    buffer.put(data)
                    buffer.flip()

                    resourceLoader.addResourceData(
                        uri,
                        buffer
                    )
                }

                resourceLoader.loadResources(asset!!)

                scene.addEntities(
                    asset!!.entities
                )


                reelEntity = asset!!.getFirstEntityByName("Cylinder.009_35")

                for (entity in asset!!.entities) {
                    val name = asset!!.getName(entity)

                    if (name.contains("Cylinder.009")) {
                        println("REEL ENTITY: $entity -> $name")
                    }
                }
                if (reelEntity != 0) {
                    printParentChain(reelEntity)
                }

                if (reelEntity != 0) {
                    val tm = engine.transformManager
                    val instance = tm.getInstance(reelEntity)

                    if (instance != 0) {
                        val transform = FloatArray(16)
                        tm.getTransform(instance, transform)

                        println(
                            "REEL TRANSFORM: " +
                            transform.contentToString()
                        )
                    }
                }
                //debugEntity = asset!!.getFirstEntityByName("Cylinder.009_35")

                if (reelEntity != 0) {
                    val tm = engine.transformManager
                    val instance = tm.getInstance(reelEntity)

                    if (instance != 0) {
                        tm.getTransform(instance, reelOriginalTransform)
                    }
                }

                //debugEntity = asset!!.getFirstEntityByName("Cylinder.001_3")

                println("TP7 ENTITIES: ${asset!!.entities.size}")
                println("TP7 RENDERABLES: ${asset!!.renderableEntities.size}")
                for (entity in asset!!.renderableEntities) {
                    val name = asset!!.getName(entity)

                    val transformManager = engine.transformManager
                    val instance = transformManager.getInstance(entity)

                    val box = asset!!.boundingBox

                    println(
                        "TP7 PART: entity=$entity name=$name"
                    )
                }
                println("TP7 RESOURCES: ${asset!!.resourceUris.contentToString()}")

                val bounds = asset!!.boundingBox
                val center = bounds.center
                val halfExtent = bounds.halfExtent

                println(
                    "TP7 BOUNDS: center=${center[0]}, ${center[1]}, ${center[2]} " +
                    "halfExtent=${halfExtent[0]}, ${halfExtent[1]}, ${halfExtent[2]}"
                )
            }

            private fun renderFrame() {
                val chain = swapChain ?: return

                /*if (debugEntity != 0) {
                    debugRotation += 0.03

                    val tm = engine.transformManager
                    val instance = tm.getInstance(debugEntity)

                    if (instance != 0) {
                        val matrix = FloatArray(16)
                        tm.getTransform(instance, matrix)

                        // Rotate around local Y axis
                        val c = kotlin.math.cos(debugRotation).toFloat()
                        val s = kotlin.math.sin(debugRotation).toFloat()

                        matrix[0] = c
                        matrix[2] = -s
                        matrix[8] = s
                        matrix[10] = c

                        tm.setTransform(instance, matrix)
                    }
                }*/

              /*  if (reelEntity != 0 && isPlaying && !reelDragging) {
                    reelRotation += 0.03

                    val tm = engine.transformManager
                    val instance = tm.getInstance(reelEntity)

                    if (instance != 0) {
                        val c = kotlin.math.cos(reelRotation).toFloat()
                        val s = kotlin.math.sin(reelRotation).toFloat()

                        val r = floatArrayOf(
                            c,  0f, s,  0f,
                            0f, 1f, 0f, 0f,
                            -s,  0f, c,  0f,
                            0f, 0f, 0f, 1f
                        )

                        val m = reelOriginalTransform
                        val result = FloatArray(16)

                        for (row in 0..3) {
                            for (col in 0..3) {
                                result[col * 4 + row] =
                                m[0 * 4 + row] * r[col * 4 + 0] +
                                m[1 * 4 + row] * r[col * 4 + 1] +
                                m[2 * 4 + row] * r[col * 4 + 2] +
                                m[3 * 4 + row] * r[col * 4 + 3]
                            }
                        }

                        tm.setTransform(instance, result)
                    }
                }*/

              if (reelEntity != 0 && !reelDragging) {
                  mechanicalPhase += 0.035
                  trackChangeImpulse *= 0.96
                  resumeImpulse *= 0.94

                  val trackResponse = trackChangeImpulse + resumeImpulse

                  val mechanicalVariation =
                  if (isPlaying) kotlin.math.sin(mechanicalPhase) * 0.0004
                      else 0.0

                          val targetVelocity =
                          if (isPlaying) {
                              targetReelVelocity + mechanicalVariation + trackResponse
                          } else {
                              0.0
                          }

                          reelVelocity +=
                          (targetVelocity - reelVelocity) * 0.08

                      reelRotation += reelVelocity

                      applyReelRotation()
              }


    /*
              view.renderTarget = null
              view.blendMode = View.BlendMode.TRANSLUCENT
    */
                if (renderer.beginFrame(
                    chain,
                    System.nanoTime()
                )
                ) {
                    renderer.render(view)
                    renderer.endFrame()
                }
            }

            fun destroy() {
                scratchAudio.release()
                Choreographer.getInstance()
                .removeFrameCallback(frameCallback)

                swapChain?.let {
                    engine.destroySwapChain(it)
                }

                asset?.let {
                    assetLoader.destroyAsset(it)
                }

                resourceLoader.destroy()
                assetLoader.destroy()
                materialProvider.destroy()

                engine.destroyView(view)
                engine.destroyScene(scene)
                engine.destroyCameraComponent(camera.getEntity())
                engine.destroyRenderer(renderer)


                indirectLight?.let {
                    engine.destroyIndirectLight(it)
                }

                engine.destroy()
            }
}

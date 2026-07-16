package com.example.myapplication

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Log
import android.view.Surface
import com.example.myapplication.gles.*
import java.io.File
import java.io.FileInputStream

@Suppress("DEPRECATION")
class DualCameraRecorder(private val context: Context) : SurfaceTexture.OnFrameAvailableListener {
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var cameraHandler: Handler
    private var cameraThread: HandlerThread = HandlerThread("CameraThread").apply { start() }

    private var eglCore: EglCore? = null
    private var displaySurface: EGLSurface? = null
    private var textureId: Int = -1
    private var surfaceTexture: SurfaceTexture? = null
    private var fullFrameRect: FullFrameRect? = null
    private val stMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    private var previewWidth = 0
    private var previewHeight = 0
    private var sensorWidth = 1920
    private var sensorHeight = 1080
    private var sensorOrientation = 0

    private var horizontalEncoder: VideoEncoder? = null
    private var verticalEncoder: VideoEncoder? = null
    private var horizontalEglSurface: EGLSurface? = null
    private var verticalEglSurface: EGLSurface? = null

    private var isRecording = false
    private val recordingLock = Any()
    private var currentDeviceOrientation = 0

    private var horizontalFile: File? = null
    private var verticalFile: File? = null

    private var displaySurfaceTexture: SurfaceTexture? = null

    init {
        cameraHandler = Handler(cameraThread.looper)
    }

    fun startPreview(texture: SurfaceTexture, verticalTexture: SurfaceTexture?, width: Int, height: Int) {
        this.previewWidth = width
        this.previewHeight = height
        this.displaySurfaceTexture = texture
        cameraHandler.post {
            Log.d("DualCameraRecorder", "startPreview: $width x $height")
            eglCore = EglCore(null, EglCore.FLAG_RECORDABLE)
            displaySurface = eglCore?.createWindowSurface(texture)
            
            eglCore?.makeCurrent(displaySurface!!)

            fullFrameRect = FullFrameRect(Texture2dProgram())
            textureId = fullFrameRect!!.createTextureObject()
            surfaceTexture = SurfaceTexture(textureId)
            surfaceTexture?.setDefaultBufferSize(width, height)
            surfaceTexture?.setOnFrameAvailableListener(this)

            openCamera()
        }
    }

    @SuppressLint("MissingPermission")
    private fun openCamera() {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            var cameraId = manager.cameraIdList.firstOrNull { id ->
                val chars = manager.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                facing == CameraCharacteristics.LENS_FACING_BACK && (focalLengths?.all { it >= 3.0f } == true)
            }
            
            if (cameraId == null) {
                cameraId = manager.cameraIdList.firstOrNull { id ->
                    val chars = manager.getCameraCharacteristics(id)
                    chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
                }
            }
            
            cameraId = cameraId ?: manager.cameraIdList[0]

            val chars = manager.getCameraCharacteristics(cameraId)
            sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90

            val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val sizes = map?.getOutputSizes(SurfaceTexture::class.java)
            val largest = sizes?.maxByOrNull { it.width * it.height }
            if (largest != null) {
                sensorWidth = largest.width
                sensorHeight = largest.height
                Log.d("DualCameraRecorder", "Using resolution: $sensorWidth x $sensorHeight")
            }

            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    startCaptureSession()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                }
            }, cameraHandler)
        } catch (e: Exception) {
            Log.e("DualCameraRecorder", "Failed to open camera", e)
        }
    }

    fun updateDimensions(width: Int, height: Int) {
        this.previewWidth = width
        this.previewHeight = height
        cameraHandler.post {
            if (eglCore != null && displaySurfaceTexture != null) {
                displaySurface?.let { eglCore?.releaseSurface(it) }
                displaySurface = eglCore?.createWindowSurface(displaySurfaceTexture!!)
                surfaceTexture?.setDefaultBufferSize(width, height)
            }
        }
    }

    fun updateOrientation(deviceOrientation: Int) {
        synchronized(recordingLock) {
            this.currentDeviceOrientation = deviceOrientation
        }
    }

    private fun startCaptureSession() {
        surfaceTexture?.setDefaultBufferSize(sensorWidth, sensorHeight)
        val surface = Surface(surfaceTexture)
        val builder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        builder?.addTarget(surface)

        cameraDevice?.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                captureSession = session
                builder?.let { session.setRepeatingRequest(it.build(), null, cameraHandler) }
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {}
        }, cameraHandler)
    }

    fun startRecording(horizontalFile: File, verticalFile: File, deviceOrientation: Int) {
        synchronized(recordingLock) {
            this.horizontalFile = horizontalFile
            this.verticalFile = verticalFile
            this.currentDeviceOrientation = deviceOrientation
            
            // We set orientationHint to 0 because we will draw the frames already rotated correctly
            horizontalEncoder = VideoEncoder(3840, 2160, 20000000, horizontalFile, 0)
            verticalEncoder = VideoEncoder(2160, 3840, 20000000, verticalFile, 0)

            horizontalEncoder?.prepare()
            verticalEncoder?.prepare()

            cameraHandler.post {
                horizontalEglSurface = eglCore?.createWindowSurface(horizontalEncoder!!.getInputSurface()!!)
                verticalEglSurface = eglCore?.createWindowSurface(verticalEncoder!!.getInputSurface()!!)
                isRecording = true
            }
        }
    }

    fun stopRecording() {
        synchronized(recordingLock) {
            isRecording = false
            cameraHandler.post {
                horizontalEncoder?.drainEncoder(true)
                verticalEncoder?.drainEncoder(true)

                horizontalEncoder?.release()
                verticalEncoder?.release()

                horizontalEncoder = null
                verticalEncoder = null

                if (horizontalEglSurface != null) {
                    eglCore?.releaseSurface(horizontalEglSurface!!)
                    horizontalEglSurface = null
                }
                if (verticalEglSurface != null) {
                    eglCore?.releaseSurface(verticalEglSurface!!)
                    verticalEglSurface = null
                }

                horizontalFile?.let { saveToGallery(it) }
                verticalFile?.let { saveToGallery(it) }
                
                horizontalFile = null
                verticalFile = null
            }
        }
    }

    private fun saveToGallery(file: File) {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/DualCamera")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)

        uri?.let {
            resolver.openOutputStream(it).use { outputStream ->
                FileInputStream(file).use { inputStream ->
                    inputStream.copyTo(outputStream!!)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(it, values, null, null)
            }
        }
    }

    override fun onFrameAvailable(st: SurfaceTexture?) {
        cameraHandler.post {
            if (eglCore == null || displaySurface == null) return@post
            eglCore?.makeCurrent(displaySurface!!)
            surfaceTexture?.updateTexImage()
            surfaceTexture?.getTransformMatrix(stMatrix)

            // 1. Draw to Main Preview (Fill Screen)
            GLES20.glViewport(0, 0, previewWidth, previewHeight)
            // Use 0 rotation for preview because stMatrix already handles initial orientation
            drawFrame(previewWidth.toFloat(), previewHeight.toFloat(), 0)
            eglCore?.swapBuffers(displaySurface!!)

            // 2. Draw to Encoders
            if (isRecording) {
                // currentDeviceOrientation already tells us how much to rotate the upright image
                // We no longer add sensorOrientation because stMatrix already accounts for it.
                val finalRotation = currentDeviceOrientation

                horizontalEglSurface?.let {
                    eglCore?.makeCurrent(it)
                    GLES20.glViewport(0, 0, 3840, 2160)
                    drawFrame(3840f, 2160f, finalRotation)
                    eglCore?.setPresentationTime(it, surfaceTexture!!.timestamp)
                    eglCore?.swapBuffers(it)
                    horizontalEncoder?.drainEncoder(false)
                }

                verticalEglSurface?.let {
                    eglCore?.makeCurrent(it)
                    GLES20.glViewport(0, 0, 2160, 3840)
                    drawFrame(2160f, 3840f, finalRotation)
                    eglCore?.setPresentationTime(it, surfaceTexture!!.timestamp)
                    eglCore?.swapBuffers(it)
                    verticalEncoder?.drainEncoder(false)
                }
            }
        }
    }

    private fun drawFrame(targetWidth: Float, targetHeight: Float, rotation: Int) {
        Matrix.setIdentityM(mvpMatrix, 0)
        
        // stMatrix already delivers a "portrait-like" image on most Android phones.
        // Rotation here is absolute from sensor-corrected portrait to world.
        val isRotated = rotation == 90 || rotation == 270
        
        // Effective source dimensions after considering device rotation
        val effectiveSrcWidth = if (isRotated) sensorWidth.toFloat() else sensorHeight.toFloat()
        val effectiveSrcHeight = if (isRotated) sensorHeight.toFloat() else sensorWidth.toFloat()
        
        val sensorAspect = effectiveSrcWidth / effectiveSrcHeight
        val targetAspect = targetWidth / targetHeight
        
        var scaleX = 1.0f
        var scaleY = 1.0f
        
        if (sensorAspect > targetAspect) {
            // Source is wider than target -> zoom in by scaling up X to fill/crop
            scaleX = sensorAspect / targetAspect
        } else {
            // Source is taller than target -> zoom in by scaling up Y to fill/crop
            scaleY = targetAspect / sensorAspect
        }

        if (rotation != 0) {
            Matrix.rotateM(mvpMatrix, 0, -rotation.toFloat(), 0f, 0f, 1f)
        }
        Matrix.scaleM(mvpMatrix, 0, scaleX, scaleY, 1.0f)
        
        fullFrameRect?.drawFrame(textureId, stMatrix, mvpMatrix)
    }

    fun release() {
        stopRecording()
        cameraHandler.post {
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
            surfaceTexture?.release()
            surfaceTexture = null
            eglCore?.let {
                displaySurface?.let { s -> it.releaseSurface(s) }
                it.release()
            }
            eglCore = null
        }
        cameraThread.quitSafely()
    }
}

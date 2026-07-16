package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.TextureView
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.constraintlayout.widget.ConstraintLayout
import java.io.File

class MainActivity : AppCompatActivity(), TextureView.SurfaceTextureListener {

    private lateinit var textureView: TextureView
    private lateinit var btnRecord: ImageButton

    private var isRecording = false
    private var dualRecorder: DualCameraRecorder? = null
    private var currentOrientation = 0

    private lateinit var orientationDetector: OrientationDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textureView = findViewById(R.id.textureView)
        btnRecord = findViewById(R.id.btnRecord)

        textureView.surfaceTextureListener = this

        orientationDetector = OrientationDetector(this) { orientation ->
            currentOrientation = orientation
            dualRecorder?.updateOrientation(orientation)
        }

        btnRecord.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                if (checkPermissions()) {
                    startRecording()
                } else {
                    requestPermissions()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        orientationDetector.startListening()
        if (dualRecorder == null) {
            dualRecorder = DualCameraRecorder(this)
        }
        if (textureView.isAvailable) {
            startCameraPreview()
        }
    }

    override fun onPause() {
        super.onPause()
        orientationDetector.stopListening()
        if (isRecording) {
            stopRecording()
        }
        dualRecorder?.release()
        dualRecorder = null
    }

    private fun startCameraPreview() {
        if (checkPermissions()) {
            dualRecorder?.startPreview(
                textureView.surfaceTexture!!,
                null,
                textureView.width,
                textureView.height
            )
        } else {
            requestPermissions()
        }
    }

    private fun checkPermissions(): Boolean {
        val permissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
            1001
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            if (textureView.isAvailable) {
                startCameraPreview()
            }
        } else {
            Toast.makeText(this, "Permissions required", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startRecording() {
        val timestamp = System.currentTimeMillis()
        val horizontalFile = File(getExternalFilesDir(null), "video_horizontal_$timestamp.mp4")
        val verticalFile = File(getExternalFilesDir(null), "video_vertical_$timestamp.mp4")
        dualRecorder?.startRecording(horizontalFile, verticalFile, currentOrientation)
        isRecording = true
        btnRecord.setImageResource(R.drawable.record_button_recording)
        Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show()
    }

    private fun stopRecording() {
        dualRecorder?.stopRecording()
        isRecording = false
        btnRecord.setImageResource(R.drawable.record_button_idle)
        Toast.makeText(this, "Recording stopped. Saving to gallery...", Toast.LENGTH_SHORT).show()
    }

    override fun onSurfaceTextureAvailable(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {
        startCameraPreview()
    }

    override fun onSurfaceTextureSizeChanged(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {
        dualRecorder?.updateDimensions(width, height)
    }

    override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture): Boolean {
        dualRecorder?.release()
        dualRecorder = null
        return true
    }

    override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) {}

    override fun onDestroy() {
        super.onDestroy()
        dualRecorder?.release()
        dualRecorder = null
    }
}

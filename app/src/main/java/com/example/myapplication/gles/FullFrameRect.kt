package com.example.myapplication.gles

import android.opengl.Matrix

class FullFrameRect(private val program: Texture2dProgram) {
    private val vertexBuffer = GlUtil.createFloatBuffer(floatArrayOf(
        -1.0f, -1.0f,
        1.0f, -1.0f,
        -1.0f, 1.0f,
        1.0f, 1.0f
    ))
    private val texBuffer = GlUtil.createFloatBuffer(floatArrayOf(
        0.0f, 0.0f,
        1.0f, 0.0f,
        0.0f, 1.0f,
        1.0f, 1.0f
    ))
    private val identityMatrix = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }

    fun drawFrame(textureId: Int, texMatrix: FloatArray, mvpMatrix: FloatArray = identityMatrix) {
        program.draw(mvpMatrix, vertexBuffer, texMatrix, texBuffer, textureId)
    }

    fun createTextureObject(): Int = program.createTextureObject()
}
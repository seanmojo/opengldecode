package com.example.opengldecode

import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.graphics.SurfaceTexture.OnFrameAvailableListener
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaPlayer
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import android.view.Surface
import java.io.FileDescriptor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class MojoRenderer(val context: Context, private val mediaFd: FileDescriptor) : GLSurfaceView.Renderer, OnFrameAvailableListener {

    val TAG = "MOJO"

    private val FLOAT_SIZE_BYTES = 4
    private val TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 3 * FLOAT_SIZE_BYTES
    private val TEXTURE_VERTICES_DATA_STRIDE_BYTES = 2 * FLOAT_SIZE_BYTES
    private val TRIANGLE_VERTICES_DATA_POS_OFFSET = 0
    private val TRIANGLE_VERTICES_DATA_UV_OFFSET = 0
    private val mTriangleVerticesData = floatArrayOf(
        -1.0f, -1.0f, 0f, 1.0f,
        -1.0f, 0f, -1.0f, 1.0f, 0f, 1.0f, 1.0f, 0f
    )

    private val mTextureVerticesData = floatArrayOf(
        0f, 0.0f, 1.0f, 0f,
        0.0f, 1f, 1.0f, 1.0f
    )

    var triangleVertices: FloatBuffer? = null
    var textureVertices: FloatBuffer? = null

    private val vertexShader = """uniform mat4 uMVPMatrix;
uniform mat4 uSTMatrix;
attribute vec4 aPosition;
attribute vec4 aTextureCoord;
varying vec2 vTextureCoord;
void main() {
  gl_Position = uMVPMatrix * aPosition;
  vTextureCoord = (uSTMatrix * aTextureCoord).xy;
}
"""

    private val fragmentShader = """#extension GL_OES_EGL_image_external : require
precision mediump float;
varying vec2 vTextureCoord;
uniform samplerExternalOES sTexture;
void main() {
  gl_FragColor = texture2D(sTexture, vTextureCoord);
}
"""

    val mMVPMatrix = FloatArray(16)
    val mSTMatrix = FloatArray(16)
    val projectionMatrix = FloatArray(16)

    var program = 0
    var textureId = 0
    var mvpMatrixHandle = 0
    var stMatrixHandle = 0
    var attrPositionHandle = 0
    var textureHandle = 0

    lateinit var surfaceTexture: SurfaceTexture
    var updateSurface = false

    var mediaPlayer: MediaPlayer? = null

    var width = 0
    var height = 0

    private val GL_TEXTURE_EXTERNAL_OES = 0x8D65

    private var imageByteBuffer: ByteBuffer? = null
    private var bitmap: Bitmap? = null

    init {
        triangleVertices =
            ByteBuffer.allocateDirect(mTriangleVerticesData.size * FLOAT_SIZE_BYTES).order(
                ByteOrder.nativeOrder()
            ).asFloatBuffer()
        triangleVertices?.put(mTriangleVerticesData)?.position(0)

        textureVertices =
            ByteBuffer.allocateDirect(mTextureVerticesData.size * FLOAT_SIZE_BYTES).order(
                ByteOrder.nativeOrder()
            ).asFloatBuffer()
        textureVertices?.put(mTextureVerticesData)?.position(0)

        Matrix.setIdentityM(mSTMatrix, 0)
    }

    fun onDetachedFromWindow() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        if (vertexShader == 0) return 0

        val pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        if (pixelShader == 0) return 0

        var program = GLES20.glCreateProgram()

        if (program != 0) {
            GLES20.glAttachShader(program, vertexShader)
            checkGlError("vertexShader")
            GLES20.glAttachShader(program, pixelShader)
            checkGlError("pixelShader")

            GLES20.glLinkProgram(program)
            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)

            if (linkStatus[0] != GLES20.GL_TRUE) {
                Log.e(TAG, "Failed to link program: ${GLES20.glGetProgramInfoLog(program)}")
                GLES20.glDeleteProgram(program)
                program = 0
            }
        }

        return program
    }

    private fun loadShader(shaderType: Int, source: String): Int {
        var shader = GLES20.glCreateShader(shaderType)
        if (shader != 0) {
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)

            val compiled = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)

            if (compiled[0] == 0) {
                Log.e(
                    TAG,
                    "Failed to compile shader: $shaderType, ${GLES20.glGetShaderInfoLog(shader)}"
                )
                GLES20.glDeleteShader(shader)
                shader = 0
            }
        }

        return shader
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        program = createProgram(vertexShader, fragmentShader)

        if (program == 0) {
            return
        }

        updateAttributes()

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]

        GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, textureId)

        GLES20.glTexParameterf(
            GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_NEAREST.toFloat()
        )
        GLES20.glTexParameterf(
            GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR.toFloat()
        )

        surfaceTexture = SurfaceTexture(textureId)
        surfaceTexture.setOnFrameAvailableListener(this)

        val surface = Surface(surfaceTexture)
        mediaPlayer = MediaPlayer()

        try {
            mediaPlayer?.setDataSource(mediaFd)
            mediaPlayer?.setSurface(surface)
            surface.release()
            mediaPlayer?.prepare()

            val mediaExtractor = MediaExtractor().apply {
                setDataSource(mediaFd)
            }
            val videoTrackIndex = getVideoTrackIndex(mediaExtractor)

            mediaExtractor.selectTrack(videoTrackIndex)
            val mediaFormat = mediaExtractor.getTrackFormat(videoTrackIndex)
            this.width = mediaFormat.getInteger(MediaFormat.KEY_WIDTH)
            this.height = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT)
            val rotation = mediaFormat.getInteger(MediaFormat.KEY_ROTATION)

            if (rotation == 90 || rotation == 270) {
                val temp = width
                width = height
                height = temp
            }

            imageByteBuffer = ByteBuffer.allocateDirect(height*width*4).order(ByteOrder.nativeOrder())
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to config mp: $e")
        }

        synchronized(this) {
            updateSurface = false
        }

        mediaPlayer?.start()
    }

    private fun updateAttributes() {
        attrPositionHandle = GLES20.glGetAttribLocation(program, "aPosition")

        if (attrPositionHandle == -1) {
            throw RuntimeException("Couldn't get attrib location for aPosition")
        }

        textureHandle = GLES20.glGetAttribLocation(program, "aTextureCoord")

        if (textureHandle == -1) {
            throw RuntimeException("Couldn't get attrib location for textureCoord")
        }

        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")

        if (mvpMatrixHandle == -1) {
            throw RuntimeException("Couldn't get attrib location for uMVPMatrix")
        }

        stMatrixHandle = GLES20.glGetUniformLocation(program, "uSTMatrix")

        if (stMatrixHandle == -1) {
            throw RuntimeException("Couldn't get attrib location for uSTMatrix")
        }
    }

    override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        this.width = width
        this.height = height
        Matrix.frustumM(projectionMatrix, 0, -1f, 1f, -1f, 1f, 1f, 10f)
        Log.i(TAG, "SurfaceChanged w: $width, h:$height")
    }

    override fun onDrawFrame(gl: GL10?) {
        synchronized(this) {
            if (updateSurface) {
                surfaceTexture.updateTexImage()
                surfaceTexture.getTransformMatrix(mSTMatrix)
                updateSurface = false
            } else {
                return
            }
        }

        GLES20.glClearColor(255f, 255f, 255f, 1f)
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT or GLES20.GL_COLOR_BUFFER_BIT)

        GLES20.glUseProgram(program)
        checkGlError("useProgram")

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, textureId)

        triangleVertices?.position(TRIANGLE_VERTICES_DATA_POS_OFFSET)
        GLES20.glVertexAttribPointer(
            attrPositionHandle,
            3,
            GLES20.GL_FLOAT,
            false,
            TRIANGLE_VERTICES_DATA_STRIDE_BYTES,
            triangleVertices
        )
        GLES20.glEnableVertexAttribArray(attrPositionHandle)

        textureVertices?.position(TRIANGLE_VERTICES_DATA_UV_OFFSET)
        GLES20.glVertexAttribPointer(
            textureHandle,
            2,
            GLES20.GL_FLOAT,
            false,
            TEXTURE_VERTICES_DATA_STRIDE_BYTES,
            textureVertices
        )
        GLES20.glEnableVertexAttribArray(textureHandle)

        Matrix.setIdentityM(mMVPMatrix, 0)

        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mMVPMatrix, 0)
        GLES20.glUniformMatrix4fv(stMatrixHandle, 1, false, mSTMatrix, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glFinish()
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        updateSurface = true
    }

    private fun checkGlError(op: String) {
        var error: Int
        while (GLES20.glGetError().also { error = it } != GLES20.GL_NO_ERROR) {
            Log.e(TAG, "$op: glError $error")
            throw java.lang.RuntimeException("$op: glError $error")
        }
    }

    private fun getVideoTrackIndex(extractor: MediaExtractor): Int {
        val numTracks = extractor.trackCount
        for (i in 0 until numTracks) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime != null && mime.startsWith("video/")) return i
        }
        return -1
    }
}
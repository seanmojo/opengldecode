package com.example.opengldecode

import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.graphics.SurfaceTexture.OnFrameAvailableListener
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaPlayer
import android.opengl.GLES20
import android.opengl.GLES32
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import android.view.Surface
import org.json.JSONObject
import java.io.FileDescriptor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class MojoRenderer(
    private val context: Context,
    private val mediaFd: FileDescriptor,
    private val onFrameAvailableListener: OnFrameAvailableListener,
    private val onMediaReady: (mp: MediaPlayer) -> Unit
) : GLSurfaceView.Renderer {

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

    private var vertexShader = ""
    private var fragmentShader = ""

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
    var mediaPlayer: MediaPlayer? = null

    var width = 0
    var height = 0

    private val GL_TEXTURE_EXTERNAL_OES = 0x8D65

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
        val vertexShader = loadShader(GLES32.GL_VERTEX_SHADER, vertexSource)
        //if (vertexShader == 0) return 0

        val pixelShader = loadShader(GLES32.GL_FRAGMENT_SHADER, fragmentSource)
        //if (pixelShader == 0) return 0

        var program = GLES32.glCreateProgram()

        if (program != 0) {
            GLES32.glAttachShader(program, vertexShader)
            checkGlError("vertexShader")
            GLES32.glAttachShader(program, pixelShader)
            checkGlError("pixelShader")

            GLES32.glLinkProgram(program)
            val linkStatus = IntArray(1)
            GLES32.glGetProgramiv(program, GLES32.GL_LINK_STATUS, linkStatus, 0)

            if (linkStatus[0] != GLES32.GL_TRUE) {
                Log.e(TAG, "Failed to link program: ${GLES32.glGetProgramInfoLog(program)}")
                GLES32.glDeleteProgram(program)
                program = 0
            }
        }

        return program
    }

    private fun loadShader(shaderType: Int, source: String): Int {
        var shader = GLES32.glCreateShader(shaderType)
        if (shader != 0) {
            GLES32.glShaderSource(shader, source)

            try {
                GLES32.glCompileShader(shader)
            } catch (exc: Exception) {
                val err = GLES20.glGetShaderInfoLog(shader)
                Log.e(TAG, err)
                GLES32.glDeleteShader(shader)
                throw exc
            }

            GLES32.glCompileShader(shader)

            val compiled = IntArray(1)
            GLES32.glGetShaderiv(shader, GLES32.GL_COMPILE_STATUS, compiled, 0)

            if (compiled[0] == 0) {
                Log.e(
                    TAG,
                    "Failed to compile shader: $shaderType, ${GLES32.glGetShaderInfoLog(shader)}"
                )
                GLES32.glDeleteShader(shader)
                shader = 0
            }
        }

        return shader
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        vertexShader =
            context.resources.openRawResource(R.raw.boring_vertex_shader).bufferedReader()
                .readText()
        fragmentShader =
            context.resources.openRawResource(R.raw.glitch).bufferedReader()
                .readText()

        program = createProgram(vertexShader, fragmentShader)

        if (program == 0) {
            return
        }

        updateAttributes()

        val textures = IntArray(1)
        GLES32.glGenTextures(1, textures, 0)
        textureId = textures[0]

        GLES32.glBindTexture(GL_TEXTURE_EXTERNAL_OES, textureId)

        GLES32.glTexParameterf(
            GL_TEXTURE_EXTERNAL_OES, GLES32.GL_TEXTURE_MIN_FILTER,
            GLES32.GL_NEAREST.toFloat()
        )
        GLES32.glTexParameterf(
            GL_TEXTURE_EXTERNAL_OES, GLES32.GL_TEXTURE_MAG_FILTER,
            GLES32.GL_LINEAR.toFloat()
        )

        surfaceTexture = SurfaceTexture(textureId)
        surfaceTexture.setOnFrameAvailableListener(onFrameAvailableListener)

        val surface = Surface(surfaceTexture)
        mediaPlayer = MediaPlayer()

        try {
            mediaPlayer?.let { mp ->
                mp.setDataSource(mediaFd)
                mp.setSurface(surface)
                surface.release()
                mp.prepare()
                onMediaReady(mp)
            }


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

        } catch (e: Exception) {
            Log.e(TAG, "Failed to config mp: $e")
        }

        mediaPlayer?.start()
    }

    private fun updateAttributes() {
        attrPositionHandle = GLES32.glGetAttribLocation(program, "aPosition")
        checkGlError("aPosition")

        textureHandle = GLES32.glGetAttribLocation(program, "aTextureCoord")
        checkGlError("aTextureCoord")

        mvpMatrixHandle = GLES32.glGetUniformLocation(program, "uMVPMatrix")
        checkGlError("uMVPMatrix")

        stMatrixHandle = GLES32.glGetUniformLocation(program, "uSTMatrix")
        checkGlError("uSTMatrix")
    }

    override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
        GLES32.glViewport(0, 0, width, height)
        Matrix.frustumM(projectionMatrix, 0, -1f, 1f, -1f, 1f, 1f, 10f)
        Log.i(TAG, "SurfaceChanged w: $width, h:$height")
    }

    override fun onDrawFrame(gl: GL10?) {
        surfaceTexture.updateTexImage()
        surfaceTexture.getTransformMatrix(mSTMatrix)

        GLES32.glClearColor(255f, 255f, 255f, 1f)
        GLES32.glClear(GLES32.GL_DEPTH_BUFFER_BIT or GLES32.GL_COLOR_BUFFER_BIT)

        GLES32.glUseProgram(program)
        checkGlError("useProgram")

        GLES32.glActiveTexture(GLES32.GL_TEXTURE0)
        GLES32.glBindTexture(GL_TEXTURE_EXTERNAL_OES, textureId)

        triangleVertices?.position(TRIANGLE_VERTICES_DATA_POS_OFFSET)
        GLES32.glVertexAttribPointer(
            attrPositionHandle,
            3,
            GLES32.GL_FLOAT,
            false,
            TRIANGLE_VERTICES_DATA_STRIDE_BYTES,
            triangleVertices
        )
        GLES32.glEnableVertexAttribArray(attrPositionHandle)

        textureVertices?.position(TRIANGLE_VERTICES_DATA_UV_OFFSET)
        GLES32.glVertexAttribPointer(
            textureHandle,
            2,
            GLES32.GL_FLOAT,
            false,
            TEXTURE_VERTICES_DATA_STRIDE_BYTES,
            textureVertices
        )
        GLES32.glEnableVertexAttribArray(textureHandle)

        Matrix.setIdentityM(mMVPMatrix, 0)

        GLES32.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mMVPMatrix, 0)
        GLES32.glUniformMatrix4fv(stMatrixHandle, 1, false, mSTMatrix, 0)

        GLES32.glDrawArrays(GLES32.GL_TRIANGLE_STRIP, 0, 4)

        GLES32.glFinish()
    }

    private fun checkGlError(op: String) {
        var error: Int
        while (GLES32.glGetError().also { error = it } != GLES32.GL_NO_ERROR) {
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
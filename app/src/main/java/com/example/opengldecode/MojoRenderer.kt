package com.example.opengldecode

import android.content.Context
import android.graphics.SurfaceTexture
import android.graphics.SurfaceTexture.OnFrameAvailableListener
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaPlayer
import android.opengl.GLES32
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import android.view.Surface
import video.mojo.shader.GlProgram
import video.mojo.shader.useAndBind
import java.io.FileDescriptor
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class MojoRenderer(
    private val context: Context,
    private val mediaFds: List<FileDescriptor>,
    private val onFrameAvailableListener: OnFrameAvailableListener,
    private val onMediaReady: (mp: MediaPlayer) -> Unit
) : GLSurfaceView.Renderer {

    companion object {
        const val TAG = "MOJO"

        private const val TRIANGLE_VERTICES_DATA_SIZE = 3
        private const val TEXTURE_VERTICES_DATA_SIZE = 2
        private const val GL_TEXTURE_EXTERNAL_OES = 0x8D65
        private const val A_POSITION_NAME = "aPosition"
        private const val A_TEXTURE_COORD_NAME = "aTextureCoord"
        private const val U_MVPMATRIX_NAME = "uMVPMatrix"
        private const val U_STMATRIX_NAME = "uSTMatrix"
        private const val U_TEXTURE_NAME = "u_texture"
        private const val I_TIME_NAME = "i_time"
        private val triangleVerticesData = floatArrayOf(
            -1.0f, -1.0f, 0f, 1.0f,
            -1.0f, 0f, -1.0f, 1.0f,
            0f, 1.0f, 1.0f, 0f
        )

        private val textureVerticesData = floatArrayOf(
            0f, 0.0f, 1.0f, 0f,
            0.0f, 1f, 1.0f, 1.0f
        )
    }

    private val mMVPMatrix = FloatArray(16)
    private val mSTMatrix = FloatArray(16)

    private val projectionMatrix = FloatArray(16)
    private lateinit var resizeProgram: GlProgram
    private lateinit var effectsProgram: GlProgram
    private var textureId: Int = 0

    private lateinit var surfaceTexture: SurfaceTexture

    private var mediaPlayer: MediaPlayer? = null

    private var width = 0
    private var height = 0

    var applyFragShader = false

    fun onDetachedFromWindow() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        val vertexShader = context.resources.openRawResource(R.raw.boring_vertex_shader)
            .bufferedReader()
            .readText()
        val fragmentShader = context.resources.openRawResource(R.raw.boring_fragment_shader)
            .bufferedReader()
            .readText()

        val effectsShader = context.resources.openRawResource(R.raw.glitch)
            .bufferedReader()
            .readText()

        resizeProgram = GlProgram(vertexShader, fragmentShader)
        effectsProgram = GlProgram(vertexShader, effectsShader)


        val textures = IntArray(1)
        GLES32.glGenTextures(1, textures, 0)
        textureId = textures[0]

//        GLES32.glBindTexture(GL_TEXTURE_EXTERNAL_OES, textureId)

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
                mp.setDataSource(mediaFds[0])
                mp.setSurface(surface)
                surface.release()
                mp.prepare()
                onMediaReady(mp)
            }


            val mediaExtractor = MediaExtractor().apply {
                setDataSource(mediaFds[0])
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

        GLES32.glActiveTexture(GLES32.GL_TEXTURE0)
        GLES32.glBindTexture(GL_TEXTURE_EXTERNAL_OES, textureId)

        if (!applyFragShader) {
            resizeProgram.useAndBind {
                setSamplerTexIdUniform(U_TEXTURE_NAME, textureId, 0)

                setBufferAttribute(
                    A_POSITION_NAME,
                    triangleVerticesData,
                    TRIANGLE_VERTICES_DATA_SIZE
                )

                setBufferAttribute(
                    A_TEXTURE_COORD_NAME,
                    textureVerticesData,
                    TEXTURE_VERTICES_DATA_SIZE
                )

                Matrix.setIdentityM(mMVPMatrix, 0)

                setFloatsUniform(U_MVPMATRIX_NAME, mMVPMatrix)
                setFloatsUniform(U_STMATRIX_NAME, mSTMatrix)
            }
        } else {
            effectsProgram.useAndBind {
                setSamplerTexIdUniform(U_TEXTURE_NAME, textureId, 0)

                setBufferAttribute(
                    A_POSITION_NAME,
                    triangleVerticesData,
                    TRIANGLE_VERTICES_DATA_SIZE
                )

                setBufferAttribute(
                    A_TEXTURE_COORD_NAME,
                    textureVerticesData,
                    TEXTURE_VERTICES_DATA_SIZE
                )

                Matrix.setIdentityM(mMVPMatrix, 0)

                setFloatsUniform(U_MVPMATRIX_NAME, mMVPMatrix)
                setFloatsUniform(U_STMATRIX_NAME, mSTMatrix)

                mediaPlayer?.let { mp ->
                    val currentTime = mp.currentPosition.toFloat() / 1000f
                    setFloatUniform(I_TIME_NAME, currentTime)
                }
            }
        }

        GLES32.glBindFramebuffer(GLES32.GL_FRAMEBUFFER, 0)
        GLES32.glDrawArrays(GLES32.GL_TRIANGLE_STRIP, 0, 4)

        GLES32.glFinish()
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
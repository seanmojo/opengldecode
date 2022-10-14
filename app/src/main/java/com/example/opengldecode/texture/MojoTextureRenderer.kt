package com.example.opengldecode.texture

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.opengl.GLES32
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import android.view.Surface
import com.example.opengldecode.R
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import video.mojo.shader.GlProgram
import video.mojo.shader.useAndBind
import java.io.FileDescriptor
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class MojoTextureRenderer(
    private val context: Context,
    private val mediaFds: List<FileDescriptor>,
    private val onMediaReady: (mp: MediaPlayer) -> Unit
) : GLTextureView.Renderer {

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
        private const val I_TEXTURE_1_NAME = "i_texture_1"
        private const val I_TEXTURE_2_NAME = "i_texture_2"
        private const val I_TEXTURE_SIZE_1_NAME = "i_texture_size_1"
        private const val I_TEXTURE_SIZE_2_NAME = "i_texture_size_2"
        private const val I_TRANSITION_PROGRESS_NAME = "i_transition_progress"
        private const val I_TIME_NAME = "i_time"
        private const val I_SIZE_NAME = "i_size"

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

    //This is a fake value used to give a value between 0.0 and 1.0
    //to the transition shader in place of a real value (I_TRANSITION_TIME)
    private var globalTime = 0f

    private val mMVPMatrix = FloatArray(16)
    private val mSTMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)

    private lateinit var resizeProgram: GlProgram
    private lateinit var effectsProgram: GlProgram

    private var textureId: Int = 0
    private var textureId1: Int = 1

    private lateinit var surfaceTexture: SurfaceTexture
    private lateinit var surfaceTexture1: SurfaceTexture

    private var mediaPlayer: MediaPlayer? = null
    private var mediaPlayer1: MediaPlayer? = null

    var applyFragShader = true

    fun onDetachedFromWindow() {
        mediaPlayer?.let {
            it.stop()
            it.release()
        }

        mediaPlayer1?.let {
            it.stop()
            it.release()
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        val vertexShader = context.resources.openRawResource(R.raw.boring_vertex_shader)
            .bufferedReader()
            .readText()

        val fragmentShader = context.resources.openRawResource(R.raw.boring_fragment_shader)
            .bufferedReader()
            .readText()

        val effectsShader = context.resources.openRawResource(R.raw.fade)
            .bufferedReader()
            .readText()

        resizeProgram = GlProgram(vertexShader, fragmentShader)
        effectsProgram = GlProgram(vertexShader, effectsShader)

        val textures = IntArray(2)
        GLES32.glGenTextures(2, textures, 0)
        textureId = textures[0]
        textureId1 = textures[1]

        resizeProgram.setSamplerTexIdUniform(U_TEXTURE_NAME, textureId, 0)

        //Tell the transition shader what our textures are now that we've generated them
        effectsProgram.setSamplerTexIdUniform(I_TEXTURE_1_NAME, textureId, 0)
        effectsProgram.setSamplerTexIdUniform(I_TEXTURE_2_NAME, textureId1, 1)

        GLES32.glTexParameterf(
            GL_TEXTURE_EXTERNAL_OES, GLES32.GL_TEXTURE_MIN_FILTER,
            GLES32.GL_NEAREST.toFloat()
        )
        GLES32.glTexParameterf(
            GL_TEXTURE_EXTERNAL_OES, GLES32.GL_TEXTURE_MAG_FILTER,
            GLES32.GL_LINEAR.toFloat()
        )

        surfaceTexture = SurfaceTexture(textureId)
        //surfaceTexture.setOnFrameAvailableListener(onFrameAvailableListener)

        surfaceTexture1 = SurfaceTexture(textureId1)
        //surfaceTexture1.setOnFrameAvailableListener(onFrameAvailableListener)

        val surface = Surface(surfaceTexture)
        val surface1 = Surface(surfaceTexture1)

        try {
            if (mediaFds.isEmpty()) {
                throw RuntimeException("No media loaded!!")
            }

            mediaPlayer = MediaPlayer()
            mediaPlayer?.let { mp ->
                mp.setDataSource(mediaFds[0])
                mp.setSurface(surface)
                surface.release()
                mp.prepare()
                onMediaReady(mp)
            }

            if (mediaFds.size > 1) {
                mediaPlayer1 = MediaPlayer()
                mediaPlayer1?.let { mp ->
                    mp.setDataSource(mediaFds[1])
                    mp.setSurface(surface1)
                    surface1.release()
                    mp.prepare()
                    onMediaReady(mp)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to config mp: $e")
        }

        mediaPlayer?.start()
        mediaPlayer1?.start()

        GlobalScope.launch {
            while (globalTime <= 1f) {
                delay(100)

                mediaPlayer?.let {
                    val currentPosSeconds = it.currentPosition.toFloat()
                    val total = it.duration.toFloat()

                    val percentage = (currentPosSeconds / total)
                    //globalTime = percentage
                    globalTime += 0.075f
                }
            }
        }
    }

    override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
        GLES32.glViewport(0, 0, width, height)
        Matrix.frustumM(projectionMatrix, 0, -1f, 1f, -1f, 1f, 1f, 10f)
        Log.i(TAG, "SurfaceChanged w: $width, h:$height")

        effectsProgram.setFloatsUniform(
            I_TEXTURE_SIZE_1_NAME,
            floatArrayOf(width.toFloat(), height.toFloat())
        )
        effectsProgram.setFloatsUniform(
            I_TEXTURE_SIZE_2_NAME,
            floatArrayOf(width.toFloat(), height.toFloat())
        )
        effectsProgram.setFloatsUniform(
            I_SIZE_NAME,
            floatArrayOf(width.toFloat(), height.toFloat())
        )
    }

    override fun onDrawFrame(gl: GL10?) {
        onDraw(surfaceTexture, textureId)
        onDraw(surfaceTexture1, textureId1)
    }

    private fun onDraw(surfaceTexture: SurfaceTexture, textureId: Int) {
        surfaceTexture.updateTexImage()
        surfaceTexture.getTransformMatrix(mSTMatrix)

        GLES32.glClearColor(0f, 0f, 0f, 1f)
        GLES32.glClear(GLES32.GL_DEPTH_BUFFER_BIT or GLES32.GL_COLOR_BUFFER_BIT)

        GLES32.glActiveTexture(GLES32.GL_TEXTURE0)
        GLES32.glBindTexture(GL_TEXTURE_EXTERNAL_OES, textureId)

        if (!applyFragShader) {
            resizeProgram.useAndBind {
                onDrawBoundShaderProgram(this)
            }
        } else {
            effectsProgram.useAndBind {
                onDrawBoundShaderProgram(this)
                setFloatUniform(I_TRANSITION_PROGRESS_NAME, globalTime)

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

    private fun onDrawBoundShaderProgram(glProgram: GlProgram) {
        with(glProgram) {
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
    }
}
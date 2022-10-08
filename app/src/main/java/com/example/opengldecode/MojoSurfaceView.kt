package com.example.opengldecode

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.SurfaceTexture
import android.graphics.SurfaceTexture.OnFrameAvailableListener
import android.media.MediaPlayer
import android.net.Uri
import android.opengl.GLSurfaceView

class MojoSurfaceView(context: Context, mediaUri: Uri, onMediaReady: (mp: MediaPlayer) -> Unit) :
    GLSurfaceView(context), OnFrameAvailableListener {

    private var renderer: MojoRenderer? = null

    init {
        setEGLContextClientVersion(2)

        holder.setFormat(PixelFormat.TRANSLUCENT)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)

        context.contentResolver.openFileDescriptor(mediaUri, "r")?.fileDescriptor?.let {
            renderer = MojoRenderer(context, it, this@MojoSurfaceView, onMediaReady = onMediaReady)
            setRenderer(renderer)
            renderMode = RENDERMODE_WHEN_DIRTY
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        renderer?.onDetachedFromWindow()
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) = requestRender()

    fun toggleApplyShader() {
        renderer?.let {
            it.applyFragShader = !it.applyFragShader
        }
    }

}
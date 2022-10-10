package com.example.opengldecode

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.SurfaceTexture
import android.graphics.SurfaceTexture.OnFrameAvailableListener
import android.media.MediaPlayer
import android.net.Uri
import android.opengl.GLSurfaceView
import java.io.FileDescriptor

class MojoSurfaceView(
    context: Context,
    mediaUris: List<Uri>,
    onMediaReady: (mp: MediaPlayer) -> Unit
) :
    GLSurfaceView(context), OnFrameAvailableListener {

    private var renderer: MojoRenderer? = null

    init {
        setEGLContextClientVersion(2)

        holder.setFormat(PixelFormat.TRANSLUCENT)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)

        val mediaFds = mutableListOf<FileDescriptor>()
        mediaUris.forEach { uri ->
            context.contentResolver.openFileDescriptor(uri, "r")?.fileDescriptor?.let {
                mediaFds.add(it)
            }
        }

        renderer =
            MojoRenderer(context, mediaFds, this@MojoSurfaceView, onMediaReady = onMediaReady)
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
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
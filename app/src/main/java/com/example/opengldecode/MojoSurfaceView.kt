package com.example.opengldecode

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.SurfaceTexture
import android.graphics.SurfaceTexture.OnFrameAvailableListener
import android.net.Uri
import android.opengl.GLSurfaceView
import android.util.Log

class MojoSurfaceView(context: Context, mediaUri: Uri) : GLSurfaceView(context), OnFrameAvailableListener {

    private var renderer: MojoRenderer? = null

    init {
        setEGLContextClientVersion(2)

        holder.setFormat(PixelFormat.TRANSLUCENT)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)

        context.contentResolver.openFileDescriptor(mediaUri, "r")?.fileDescriptor?.let {
            renderer = MojoRenderer(it, this@MojoSurfaceView)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        renderer?.onDetachedFromWindow()
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        Log.i("MOJO", "onFrameAvailable")
        requestRender()
    }

}
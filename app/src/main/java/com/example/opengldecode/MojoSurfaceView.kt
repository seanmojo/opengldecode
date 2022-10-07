package com.example.opengldecode

import android.content.Context
import android.graphics.PixelFormat
import android.net.Uri
import android.opengl.GLSurfaceView

class MojoSurfaceView(context: Context, mediaUri: Uri) : GLSurfaceView(context) {

    private var renderer: MojoRenderer? = null

    init {
        setEGLContextClientVersion(2)

        holder.setFormat(PixelFormat.TRANSLUCENT)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)

        context.contentResolver.openFileDescriptor(mediaUri, "r")?.fileDescriptor?.let {
            renderer = MojoRenderer(context, it)
            setRenderer(renderer)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        renderer?.onDetachedFromWindow()
    }

}
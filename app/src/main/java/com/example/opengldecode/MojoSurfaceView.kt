package com.example.opengldecode

import android.content.Context
import android.opengl.GLSurfaceView

class MojoSurfaceView(context: Context) : GLSurfaceView(context) {

    private val renderer: MojoRenderer

    init {
        setEGLContextClientVersion(2)
        renderer = MojoRenderer()
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

}
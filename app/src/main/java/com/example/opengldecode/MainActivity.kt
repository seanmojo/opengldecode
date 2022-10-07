package com.example.opengldecode

import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.Log
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    lateinit var glSurfaceView: GLSurfaceView

    private val pickMedia =
        registerForActivityResult(PickVisualMedia()) { uri ->
            Log.i("MOJO", "Got uri: $uri")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        glSurfaceView = MojoSurfaceView(this)
        setContentView(glSurfaceView)

        pickMedia.launch(PickVisualMediaRequest(PickVisualMedia.VideoOnly))
    }
}
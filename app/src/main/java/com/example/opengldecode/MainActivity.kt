package com.example.opengldecode

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.FrameLayout
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    lateinit var mojoSurfaceView: MojoSurfaceView
    lateinit var content: FrameLayout

    private val pickMedia =
        registerForActivityResult(PickVisualMedia()) { uri ->
            Log.i("MOJO", "Got uri: $uri")

            uri?.let {
                onMediaPicked(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        content = findViewById(R.id.content)

        pickMedia.launch(PickVisualMediaRequest(PickVisualMedia.VideoOnly))
    }

    private fun onMediaPicked(uri: Uri) {
        mojoSurfaceView = MojoSurfaceView(this, uri)
        content.addView(mojoSurfaceView)
    }
}
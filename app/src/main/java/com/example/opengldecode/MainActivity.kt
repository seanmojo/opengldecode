package com.example.opengldecode

import android.media.MediaPlayer
import android.media.MediaPlayer.OnInfoListener
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    lateinit var mojoSurfaceView: MojoSurfaceView
    lateinit var content: FrameLayout
    lateinit var seekBar: SeekBar
    lateinit var mediaPlayer: MediaPlayer

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
        seekBar = findViewById(R.id.seekBar)

        pickMedia.launch(PickVisualMediaRequest(PickVisualMedia.VideoOnly))
    }

    private fun onMediaPicked(uri: Uri) {
        seekBar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                Log.i("MOJO", "onProgressChanged: $progress, byUser: $fromUser")
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                Log.i("MOJO", "onStartTrackingTouch")
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                Log.i("MOJO", "onStopTrackingTouch")
            }
        })

        mojoSurfaceView = MojoSurfaceView(this, uri, onMediaReady = { mp ->
            seekBar.max = mp.duration
            seekBar.progress = mp.currentPosition

            this.mediaPlayer = mp
            this.mediaPlayer.setOnSeekCompleteListener { _ -> Log.i("MOJO", "onSeekComplete") }

            lifecycleScope.launch {
                while(true) {
                    seekBar.progress = mediaPlayer.currentPosition
                    delay(1000)
                }
            }
        })



        content.addView(mojoSurfaceView)
    }
}
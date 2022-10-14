package com.example.opengldecode

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.FrameLayout
import android.widget.SeekBar
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.appcompat.app.AppCompatActivity
import com.example.opengldecode.texture.GLTextureView
import kotlin.collections.ArrayList


class MainActivity : AppCompatActivity() {

//    lateinit var mojoSurfaceView: MojoSurfaceView
    lateinit var mojoTextureView: GLTextureView
    lateinit var content: FrameLayout
    lateinit var seekBar: SeekBar
    lateinit var captureButton: Button
    private lateinit var toggleShaderButton: Button

    private val pickMedia =
        registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(2)) { uris ->
            if (uris.isNotEmpty()) {
                onMediaPicked(uris)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        content = findViewById(R.id.content)
        seekBar = findViewById(R.id.seekBar)
        captureButton = findViewById(R.id.captureButton)
        toggleShaderButton = findViewById(R.id.toggleShaderButton)

        pickMedia.launch(PickVisualMediaRequest(PickVisualMedia.VideoOnly))

        toggleShaderButton.setOnClickListener {
            //mojoSurfaceView.toggleApplyShader()
        }

        captureButton.setOnClickListener {
            Log.i("MOJO", "Tapped capture")

//            mojoSurfaceView.usePixelCopy { bitmap ->
//                bitmap?.let { bmp ->
//                    val dir = filesDir
//                    if (!dir.exists()) dir.mkdirs()
//                    val file = File(dir, "capture" + UUID.randomUUID() + ".png")
//                    val fOut = FileOutputStream(file)
//
//                    bmp.compress(Bitmap.CompressFormat.PNG, 85, fOut)
//                    fOut.flush()
//                    fOut.close()
//
//                    Log.i("MOJO", "Stored captured image to: ${file.absolutePath}")
//                }
//            }
        }
    }

    private fun onMediaPicked(uris: List<Uri>) {

//        seekBar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
//            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
//                Log.i("MOJO", "onProgressChanged: $progress, byUser: $fromUser")
//                if (fromUser) {
//                    mediaPlayer.pause()
//                    mediaPlayer.seekTo(progress.toLong(), SEEK_CLOSEST)
//                }
//            }
//
//            override fun onStartTrackingTouch(seekBar: SeekBar?) {
//                Log.i("MOJO", "onStartTrackingTouch")
//            }
//
//            override fun onStopTrackingTouch(seekBar: SeekBar?) {
//                Log.i("MOJO", "onStopTrackingTouch")
//            }
//        })

        mojoTextureView = GLTextureView(this, ArrayList(uris))

//        mojoSurfaceView = MojoSurfaceView(this, uris, onMediaReady = { mp ->
//            seekBar.max = mp.duration
//            seekBar.progress = mp.currentPosition

//            this.mediaPlayer = mp
//            this.mediaPlayer.setOnSeekCompleteListener { _ ->
//                Log.i("MOJO", "onSeekComplete")
//                mediaPlayer.start()
//            }
//
//            lifecycleScope.launch {
//                while (true) {
//                    seekBar.progress = mediaPlayer.currentPosition
//                    delay(1000)
//                }
//            }
//        })



//        content.addView(mojoSurfaceView)
        content.addView(mojoTextureView)
    }
}
package com.example.opengldecode

import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.view.PixelCopy
import android.view.SurfaceView

/**
 * Pixel copy to copy SurfaceView/VideoView into BitMap
 */
fun SurfaceView.usePixelCopy(callback: (Bitmap?) -> Unit) {
    val bitmap: Bitmap = Bitmap.createBitmap(
        width,
        height,
        Bitmap.Config.ARGB_8888
    );
    try {
        // Create a handler thread to offload the processing of the image.
        val handlerThread = HandlerThread("PixelCopier");
        handlerThread.start()
        PixelCopy.request(
            this, bitmap, { copyResult ->
                if (copyResult == PixelCopy.SUCCESS) {
                    callback(bitmap)
                }
                handlerThread.quitSafely()
            },
            Handler(handlerThread.looper)
        )
    } catch (e: IllegalArgumentException) {
        callback(null)
        // PixelCopy may throw IllegalArgumentException, make sure to handle it
        e.printStackTrace()
    }
}
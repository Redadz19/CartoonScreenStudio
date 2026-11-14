package com.example.cartoonscreenstudio

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.OutputStream
import kotlin.math.abs
import kotlin.math.max

/**
 * Extracts a representative frame from a selected video and applies an offline
 * posterize + edge-enhancement cartoon effect. This is a frame preview, not a
 * complete video-to-video renderer.
 */
object CartoonProcessor {
    fun createPreview(context: Context, uri: Uri): Bitmap {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val frame = retriever.getFrameAtTime(
                1_000_000L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: throw IllegalStateException("تعذر استخراج إطار من الفيديو")
            val scaled = scaleDown(frame, 960)
            return cartoonizeFrame(scaled)
        } finally {
            retriever.release()
        }
    }

    private fun scaleDown(source: Bitmap, maxSide: Int): Bitmap {
        val largest = max(source.width, source.height)
        if (largest <= maxSide) return source
        val ratio = maxSide.toFloat() / largest
        return Bitmap.createScaledBitmap(
            source,
            (source.width * ratio).toInt().coerceAtLeast(1),
            (source.height * ratio).toInt().coerceAtLeast(1),
            true
        )
    }

    fun cartoonizeFrame(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val input = IntArray(width * height)
        source.getPixels(input, 0, width, 0, 0, width, height)
        val output = IntArray(input.size)

        fun luminance(c: Int): Int {
            val r = (c shr 16) and 0xff
            val g = (c shr 8) and 0xff
            val b = c and 0xff
            return (r * 30 + g * 59 + b * 11) / 100
        }

        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = y * width + x
                val c = input[i]
                val r = (c shr 16) and 0xff
                val g = (c shr 8) and 0xff
                val b = c and 0xff

                val left = input[y * width + (x - 1).coerceAtLeast(0)]
                val right = input[y * width + (x + 1).coerceAtMost(width - 1)]
                val up = input[(y - 1).coerceAtLeast(0) * width + x]
                val down = input[(y + 1).coerceAtMost(height - 1) * width + x]
                val edge = abs(luminance(left) - luminance(right)) +
                    abs(luminance(up) - luminance(down))

                val posterR = ((r / 48) * 48 + 24).coerceIn(0, 255)
                val posterG = ((g / 48) * 48 + 24).coerceIn(0, 255)
                val posterB = ((b / 48) * 48 + 24).coerceIn(0, 255)
                val shade = if (edge > 82) 0.28f else 1.0f

                val nr = (posterR * shade).toInt().coerceIn(0, 255)
                val ng = (posterG * shade).toInt().coerceIn(0, 255)
                val nb = (posterB * shade).toInt().coerceIn(0, 255)
                output[i] = (0xff shl 24) or (nr shl 16) or (ng shl 8) or nb
            }
        }

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            it.setPixels(output, 0, width, 0, 0, width, height)
        }
    }

    fun savePreview(context: Context, bitmap: Bitmap): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "CartoonPreview_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CartoonScreenStudio")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        try {
            resolver.openOutputStream(uri).use { stream: OutputStream? ->
                if (stream == null || !bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    throw IllegalStateException("تعذر حفظ الصورة")
                }
            }
            if (Build.VERSION.SDK_INT >= 29) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            return uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }
}

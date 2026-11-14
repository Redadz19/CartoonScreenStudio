package com.example.cartoonscreenstudio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.content.ContentValues
import android.os.Build
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.max

/**
 * Offline video-to-cartoon export.
 * Decodes sampled frames, applies the app's posterize/edge effect, then encodes
 * a silent H.264 MP4. Sampling is intentionally capped at 12 fps and 720p to
 * keep memory/thermal load reasonable on phones.
 */
object CartoonVideoExporter {
    data class Result(val uri: Uri, val frames: Int)

    fun export(context: Context, input: Uri, onProgress: (Int) -> Unit = { }): Result {
        val retriever = MediaMetadataRetriever()
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var outputUri: Uri? = null
        var outputPath: String? = null
        var muxerStarted = false
        var trackIndex = -1
        var frameCount = 0
        try {
            retriever.setDataSource(context, input)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.coerceAtLeast(1L) ?: error("تعذر قراءة مدة الفيديو")
            val srcW = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1280
            val srcH = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 720
            val scale = minOf(1f, 720f / max(srcW, srcH))
            val width = ((srcW * scale).toInt() / 2 * 2).coerceAtLeast(2)
            val height = ((srcH * scale).toInt() / 2 * 2).coerceAtLeast(2)
            val fps = 12
            val frameIntervalUs = 1_000_000L / fps
            val totalFrames = ((durationMs * 1000L) / frameIntervalUs + 1).toInt()

            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "Cartoon_${System.currentTimeMillis()}.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= 29) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/CartoonScreenStudio")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }
            outputUri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("تعذر إنشاء ملف الفيديو")
            val descriptor = context.contentResolver.openFileDescriptor(outputUri, "w")
                ?: error("تعذر فتح ملف الفيديو")
            // MediaMuxer owns the file descriptor after construction.
            muxer = MediaMuxer(descriptor.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            descriptor.close()

            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar)
                setInteger(MediaFormat.KEY_BIT_RATE, 3_000_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            }
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            val info = MediaCodec.BufferInfo()
            var ptsUs = 0L
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = encoder.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val inputBuffer = encoder.getInputBuffer(inputIndex) ?: error("تعذر الوصول إلى مخزن الترميز")
                        inputBuffer.clear()
                        if (ptsUs <= durationMs * 1000L) {
                            val frame = retriever.getFrameAtTime(ptsUs, MediaMetadataRetriever.OPTION_CLOSEST)
                                ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                                ?: error("تعذر استخراج إطار الفيديو")
                            val scaled = if (frame.width != width || frame.height != height)
                                android.graphics.Bitmap.createScaledBitmap(frame, width, height, true) else frame
                            val cartoon = CartoonProcessor.cartoonizeFrame(scaled)
                            val yuv = bitmapToYuv420Planar(cartoon)
                            if (inputBuffer.remaining() < yuv.size) error("مخزن الترميز صغير جداً")
                            inputBuffer.put(yuv)
                            encoder.queueInputBuffer(inputIndex, 0, yuv.size, ptsUs, 0)
                            frameCount++
                            onProgress((frameCount * 100 / totalFrames).coerceIn(0, 99))
                            if (cartoon !== frame && cartoon !== scaled) cartoon.recycle()
                            if (scaled !== frame) scaled.recycle()
                            if (frame !== scaled) frame.recycle()
                            ptsUs += frameIntervalUs
                        } else {
                            encoder.queueInputBuffer(inputIndex, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        }
                    }
                }

                when (val outputIndex = encoder.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (muxerStarted) error("ترميز غير متوقع لتنسيق الإخراج")
                        trackIndex = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    else -> if (outputIndex >= 0) {
                        val encoded = encoder.getOutputBuffer(outputIndex)
                        if (encoded != null && info.size > 0) {
                            if (!muxerStarted) error("لم يبدأ مُجمّع الفيديو")
                            encoded.position(info.offset)
                            encoded.limit(info.offset + info.size)
                            muxer.writeSampleData(trackIndex, encoded, info)
                        }
                        outputDone = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        encoder.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }
            encoder.stop()
            encoder.release()
            encoder = null
            muxer.stop()
            muxer.release()
            muxer = null

            if (Build.VERSION.SDK_INT >= 29) {
                val done = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                context.contentResolver.update(outputUri, done, null, null)
            }
            onProgress(100)
            return Result(outputUri, frameCount)
        } catch (e: Exception) {
            outputUri?.let { context.contentResolver.delete(it, null, null) }
            throw e
        } finally {
            try { encoder?.stop() } catch (_: Exception) {}
            try { encoder?.release() } catch (_: Exception) {}
            try { muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
            retriever.release()
        }
    }

    private fun bitmapToYuv420Planar(bitmap: android.graphics.Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val ySize = width * height
        val uvSize = ySize / 4
        val result = ByteArray(ySize + uvSize * 2)
        var yIndex = 0
        var uIndex = ySize
        var vIndex = ySize + uvSize

        for (row in 0 until height) {
            for (col in 0 until width) {
                val c = pixels[row * width + col]
                val r = (c shr 16) and 0xff
                val g = (c shr 8) and 0xff
                val b = c and 0xff
                val y = (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).coerceIn(0, 255)
                result[yIndex++] = y.toByte()
                if (row % 2 == 0 && col % 2 == 0) {
                    val u = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).coerceIn(0, 255)
                    val v = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).coerceIn(0, 255)
                    result[uIndex++] = u.toByte()
                    result[vIndex++] = v.toByte()
                }
            }
        }
        return result
    }
}

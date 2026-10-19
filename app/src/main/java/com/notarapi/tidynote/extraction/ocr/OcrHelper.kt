package com.notarapi.tidynote.extraction.ocr

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream

class OcrHelper {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognizeText(
        bitmap: Bitmap,
        rotation: Int = 0,
    ): String =
        withContext(Dispatchers.IO) {
            val processed = preprocessForOcr(bitmap)
            val image = InputImage.fromBitmap(processed, rotation)
            val result = recognizer.process(image).await()
            result.text
        }

    suspend fun recognizeTextFromUri(
        contentResolver: ContentResolver,
        uri: android.net.Uri,
    ): String =
        withContext(Dispatchers.IO) {
            val rotation = getExifOrientation(contentResolver, uri)
            // Decode sampel, bukan full-res: foto 12MP = ~48 MB ARGB_8888 dan
            // preprocessForOcr mengalokasikan bitmap kedua seukuran penuh.
            val bitmap = decodeSampled(contentResolver, uri) ?: return@withContext ""
            try {
                val processed = preprocessForOcr(bitmap)
                try {
                    val image = InputImage.fromBitmap(processed, rotation)
                    val result = recognizer.process(image).await()
                    result.text
                } finally {
                    if (!processed.isRecycled) processed.recycle()
                }
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }

    suspend fun recognizeTextFromPath(imagePath: String): String =
        withContext(Dispatchers.IO) {
            val bitmap = decodeSampled(imagePath) ?: return@withContext ""
            try {
                val processed = preprocessForOcr(bitmap)
                try {
                    val image = InputImage.fromBitmap(processed, 0)
                    val result = recognizer.process(image).await()
                    result.text
                } finally {
                    if (!processed.isRecycled) processed.recycle()
                }
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }

    /** Sisi terpanjang bitmap hasil decode. Cukup untuk OCR, jauh lebih hemat heap. */
    private val maxDecodeDim = 2048

    private fun decodeSampled(
        contentResolver: ContentResolver,
        uri: android.net.Uri,
    ): Bitmap? {
        val bounds =
            BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val opts =
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize(maxOf(bounds.outWidth, bounds.outHeight), maxDecodeDim)
            }
        return contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
    }

    private fun decodeSampled(imagePath: String): Bitmap? {
        val bounds =
            BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(imagePath, bounds)
        val opts =
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize(maxOf(bounds.outWidth, bounds.outHeight), maxDecodeDim)
            }
        return BitmapFactory.decodeFile(imagePath, opts)
    }

    private fun sampleSize(
        largest: Int,
        maxDim: Int,
    ): Int {
        var sample = 1
        while (largest / sample > maxDim) sample *= 2
        return sample
    }

    private fun getExifOrientation(
        contentResolver: ContentResolver,
        uri: android.net.Uri,
    ): Int {
        var inputStream: InputStream? = null
        return try {
            inputStream = contentResolver.openInputStream(uri) ?: return 0
            val exif = ExifInterface(inputStream)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (_: IOException) {
            0
        } finally {
            inputStream?.close()
        }
    }

    private fun preprocessForOcr(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height

        val targetMin = 1200f
        val scale =
            if (w < targetMin || h < targetMin) {
                maxOf(targetMin / w, targetMin / h).coerceAtMost(2.5f)
            } else {
                1.0f
            }

        val scaledW = (w * scale).toInt()
        val scaledH = (h * scale).toInt()
        val scaled = if (scale > 1.05f) Bitmap.createScaledBitmap(src, scaledW, scaledH, true) else src
        val result = Bitmap.createBitmap(scaledW, scaledH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        val cm =
            ColorMatrix().apply {
                setSaturation(0f)
                val contrast = 1.4f
                val brightness = -30f
                set(
                    floatArrayOf(
                        contrast,
                        0f,
                        0f,
                        0f,
                        brightness,
                        0f,
                        contrast,
                        0f,
                        0f,
                        brightness,
                        0f,
                        0f,
                        contrast,
                        0f,
                        brightness,
                        0f,
                        0f,
                        0f,
                        1f,
                        0f,
                    ),
                )
            }
        val paint =
            Paint().apply {
                colorFilter = ColorMatrixColorFilter(cm)
            }
        canvas.drawBitmap(scaled, 0f, 0f, paint)
        // Bitmap perantara (hasil scale) tidak dipakai lagi; `src` milik pemanggil.
        if (scaled !== src && !scaled.isRecycled) scaled.recycle()
        return result
    }

    fun close() {
        recognizer.close()
    }
}

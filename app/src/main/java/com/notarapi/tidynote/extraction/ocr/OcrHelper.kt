package com.notarapi.tidynote.extraction.ocr

import android.content.ContentResolver
import android.graphics.Bitmap
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
            val inputStream = contentResolver.openInputStream(uri) ?: return@withContext ""
            val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream) ?: return@withContext ""
            inputStream.close()
            val processed = preprocessForOcr(bitmap)
            val image = InputImage.fromBitmap(processed, rotation)
            val result = recognizer.process(image).await()
            result.text
        }

    suspend fun recognizeTextFromPath(imagePath: String): String =
        withContext(Dispatchers.IO) {
            val bitmap = android.graphics.BitmapFactory.decodeFile(imagePath) ?: return@withContext ""
            val processed = preprocessForOcr(bitmap)
            val image = InputImage.fromBitmap(processed, 0)
            val result = recognizer.process(image).await()
            result.text
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
        return result
    }

    fun close() {
        recognizer.close()
    }
}

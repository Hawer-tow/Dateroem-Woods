package com.dateroem.woodsales.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Downsamples, EXIF-rotates and re-encodes a picked image to JPEG before
 * upload. Shared by product photos and campaign images (extracted from
 * ProductFormViewModel, which had this first) - a raw camera capture can
 * be 6-12MB, unusable to upload/preview over the dev USB tunnel, and this
 * is what actually fixed a photo silently failing to show up.
 */
object ImageCompressor {

    suspend fun compressToJpeg(
        context: Context,
        uri: Uri,
        destination: File,
        maxDimension: Int = 1600,
        quality: Int = 85
    ) = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > maxDimension * 2 || bounds.outHeight / sampleSize > maxDimension * 2) {
            sampleSize *= 2
        }

        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sampleSize })
        } ?: error("Couldn't read the picked image")

        val scale = maxDimension.toFloat() / maxOf(decoded.width, decoded.height)
        val resized = if (scale < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
        } else decoded

        val oriented = applyExifRotation(context, uri, resized)
        FileOutputStream(destination).use { out -> oriented.compress(Bitmap.CompressFormat.JPEG, quality, out) }
    }

    // Camera captures commonly store rotation as EXIF metadata rather than
    // baking it into the pixels - skip this and photos taken in portrait
    // come out sideways.
    private fun applyExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = context.contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL

        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}

package com.example.photoapp10.core.thumb

import android.graphics.*
import android.media.ThumbnailUtils
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

data class ThumbnailResult(
    val path: String,
    val width: Int,
    val height: Int
)

/**
 * Nougat-safe thumbnail generator.
 * - Reads EXIF and applies orientation.
 * - Scales so the longer side == maxDim (default 512 px).
 * - Saves JPEG (quality 85).
 */
class Thumbnailer {

    suspend fun generate(
        sourceFile: File,
        destFile: File,
        maxDim: Int = 512,
        jpegQuality: Int = 85
    ): ThumbnailResult = withContext(Dispatchers.IO) {
        // 1) Decode bounds to compute sample size
        val optsBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        FileInputStream(sourceFile).use { BitmapFactory.decodeStream(it, null, optsBounds) }
        val (srcW, srcH) = optsBounds.outWidth to optsBounds.outHeight
        require(srcW > 0 && srcH > 0) { "Invalid source image bounds." }

        val inSample = computeInSampleSize(srcW, srcH, maxDim * 2) // decode a bit larger than final
        val optsDecode = BitmapFactory.Options().apply {
            inJustDecodeBounds = false
            inSampleSize = inSample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        // 2) Decode bitmap
        val decoded: Bitmap = FileInputStream(sourceFile).use {
            BitmapFactory.decodeStream(it, null, optsDecode)
                ?: error("Failed to decode source image.")
        }

        var oriented: Bitmap? = null
        try {
            // 3) Apply EXIF orientation
            oriented = applyExifOrientation(decoded, sourceFile)

            // 4) Scale to maxDim (long side)
            val (tw, th) = targetSize(oriented.width, oriented.height, maxDim)
            val scaled = if (oriented.width != tw || oriented.height != th) {
                ThumbnailUtils.extractThumbnail(oriented, tw, th, ThumbnailUtils.OPTIONS_RECYCLE_INPUT)
            } else oriented

            // 5) Compress to JPEG
            destFile.parentFile?.mkdirs()
            FileOutputStream(destFile).use { fos ->
                if (!scaled.compress(Bitmap.CompressFormat.JPEG, jpegQuality, fos)) {
                    error("JPEG compression failed.")
                }
            }

            ThumbnailResult(destFile.absolutePath, scaled.width, scaled.height)
        } finally {
            // Ensure underlying memory is freed promptly on N
            if (!decoded.isRecycled) decoded.recycle()
            // Also recycle oriented bitmap if it's different from decoded
            if (oriented != null && oriented !== decoded && !oriented.isRecycled) {
                oriented.recycle()
            }
        }
    }

    private fun computeInSampleSize(srcW: Int, srcH: Int, reqLongSide: Int): Int {
        val longSide = maxOf(srcW, srcH)
        var sample = 1
        while ((longSide / sample) > reqLongSide) sample *= 2
        return sample
    }

    private fun targetSize(w: Int, h: Int, maxDim: Int): Pair<Int, Int> {
        return if (w >= h) {
            val ratio = maxDim.toFloat() / w.toFloat()
            val nh = (h * ratio).toInt().coerceAtLeast(1)
            maxDim to nh
        } else {
            val ratio = maxDim.toFloat() / h.toFloat()
            val nw = (w * ratio).toInt().coerceAtLeast(1)
            nw to maxDim
        }
    }

    private fun applyExifOrientation(src: Bitmap, sourceFile: File): Bitmap {
        val exif = try { ExifInterface(sourceFile.absolutePath) } catch (e: Exception) {
            Timber.w(e, "EXIF read failed")
            return src
        }
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
        )
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> {
                m.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                m.postScale(1f, -1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> { // flip + rotate 90
                m.postScale(-1f, 1f); m.postRotate(90f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> { // flip + rotate 270
                m.postScale(-1f, 1f); m.postRotate(270f)
            }
            else -> return src
        }
        return try {
            if (src.isRecycled) {
                Timber.w("Source bitmap is already recycled")
                return src
            }
            val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
            if (out !== src && !src.isRecycled) {
                src.recycle()
            }
            out
        } catch (e: OutOfMemoryError) {
            Timber.e(e, "OOM rotating bitmap")
            src // return original if rotate fails
        } catch (e: Exception) {
            Timber.e(e, "Error rotating bitmap")
            src // return original if rotate fails
        }
    }

    /**
     * Generate thumbnail for video file.
     * Uses MediaStore.Images.Thumbnails or ThumbnailUtils.createVideoThumbnail.
     */
    suspend fun generateVideoThumbnail(
        videoFile: File,
        destFile: File,
        maxDim: Int = 512,
        jpegQuality: Int = 85
    ): ThumbnailResult = withContext(Dispatchers.IO) {
        try {
            // Use ThumbnailUtils to create video thumbnail
            val thumbnail: Bitmap? = ThumbnailUtils.createVideoThumbnail(
                videoFile.absolutePath,
                MediaStore.Images.Thumbnails.MINI_KIND
            )
            
            if (thumbnail == null) {
                Timber.e("Failed to create video thumbnail for: ${videoFile.absolutePath}")
                error("Failed to create video thumbnail")
            }

            try {
                // Scale to maxDim (long side)
                val (tw, th) = targetSize(thumbnail.width, thumbnail.height, maxDim)
                val scaled = if (thumbnail.width != tw || thumbnail.height != th) {
                    ThumbnailUtils.extractThumbnail(thumbnail, tw, th, ThumbnailUtils.OPTIONS_RECYCLE_INPUT)
                } else thumbnail

                // Compress to JPEG
                destFile.parentFile?.mkdirs()
                FileOutputStream(destFile).use { fos ->
                    if (!scaled.compress(Bitmap.CompressFormat.JPEG, jpegQuality, fos)) {
                        error("JPEG compression failed for video thumbnail")
                    }
                }

                ThumbnailResult(destFile.absolutePath, scaled.width, scaled.height)
            } finally {
                // Recycle bitmaps
                if (!thumbnail.isRecycled) thumbnail.recycle()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error generating video thumbnail for: ${videoFile.absolutePath}")
            throw e
        }
    }
}
















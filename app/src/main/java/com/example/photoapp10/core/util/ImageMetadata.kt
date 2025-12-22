package com.example.photoapp10.core.util

import android.graphics.BitmapFactory
import java.io.File
import java.io.FileInputStream

data class ImageMetadata(
    val width: Int,
    val height: Int,
    val sizeBytes: Long
) {
    companion object {
        fun fromFile(file: File): ImageMetadata {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            FileInputStream(file).use { BitmapFactory.decodeStream(it, null, opts) }
            return ImageMetadata(
                width = opts.outWidth,
                height = opts.outHeight,
                sizeBytes = file.length()
            )
        }
    }
}















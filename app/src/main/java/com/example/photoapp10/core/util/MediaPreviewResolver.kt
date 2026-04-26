package com.example.photoapp10.core.util

import java.io.File

object MediaPreviewResolver {

    fun resolvePreviewPath(thumbPath: String?, mediaPath: String?): String? {
        val thumb = thumbPath
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf { it.exists() }
            ?.absolutePath
        if (thumb != null) return thumb

        return mediaPath
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf { it.exists() }
            ?.absolutePath
    }
}

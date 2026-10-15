package com.aksworns22.setty.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/** 사진 선택기로 고른 이미지를 매물 등록·수정 multipart 파트로 읽는다. */
class ListingImageReader(
    private val context: Context,
) {
    suspend fun read(uris: List<Uri>): List<MultipartBody.Part> = withContext(Dispatchers.IO) {
        val images = uris.map { uri -> readImage(uri) }
        if (images.sumOf { it.bytes.size.toLong() } > MAX_TOTAL_BYTES) {
            throw ListingImageTooLargeException()
        }
        images.mapIndexed { index, image ->
            MultipartBody.Part.createFormData(
                name = PART_NAME,
                filename = "image_$index.${image.extension}",
                body = image.bytes.toRequestBody(image.mimeType.toMediaType()),
            )
        }
    }

    private fun readImage(uri: Uri): ImageFile {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri) ?: DEFAULT_MIME_TYPE
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("이미지를 읽지 못했습니다")
        return ImageFile(bytes, mimeType)
    }

    private class ImageFile(val bytes: ByteArray, val mimeType: String) {
        val extension: String
            get() = when (mimeType) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                else -> "jpg"
            }
    }

    companion object {
        private const val PART_NAME = "images"
        private const val DEFAULT_MIME_TYPE = "image/jpeg"

        // 서버는 한 번에 올리는 이미지 전체 용량을 25MB로 제한한다
        private const val MAX_TOTAL_BYTES = 25L * 1024 * 1024
    }
}

class ListingImageTooLargeException : Exception("사진 전체 용량은 25MB 이하여야 합니다")

package com.scrctl.client.core.utils

object PngUtils {
    private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private val PNG_IEND_CHUNK = byteArrayOf(0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(), 0x42, 0x60, 0x82.toByte())

    fun extractPngBytes(raw: ByteArray): ByteArray? {
        if (raw.isEmpty()) {
            return null
        }
        if (raw.startsWith(PNG_SIGNATURE)) {
            return raw
        }

        val pngStart = raw.indexOfSubArray(PNG_SIGNATURE)
        if (pngStart < 0) {
            return null
        }

        val iendStart = raw.indexOfSubArray(PNG_IEND_CHUNK, pngStart + PNG_SIGNATURE.size)
        if (iendStart < 0) {
            return raw.copyOfRange(pngStart, raw.size)
        }

        val pngEndExclusive = iendStart + PNG_IEND_CHUNK.size
        return raw.copyOfRange(pngStart, pngEndExclusive)
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) {
            return false
        }
        for (i in prefix.indices) {
            if (this[i] != prefix[i]) {
                return false
            }
        }
        return true
    }

    private fun ByteArray.indexOfSubArray(target: ByteArray, fromIndex: Int = 0): Int {
        if (target.isEmpty()) {
            return fromIndex.coerceIn(0, size)
        }
        if (size < target.size || fromIndex >= size) {
            return -1
        }
        val start = fromIndex.coerceAtLeast(0)
        val last = size - target.size
        for (i in start..last) {
            var matched = true
            for (j in target.indices) {
                if (this[i + j] != target[j]) {
                    matched = false
                    break
                }
            }
            if (matched) {
                return i
            }
        }
        return -1
    }
}

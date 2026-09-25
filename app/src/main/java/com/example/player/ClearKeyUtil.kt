package com.example.player

import android.util.Base64

object ClearKeyUtil {

    /**
     * Converts a hexadecimal string (KID or Key) to a ByteArray.
     */
    fun hexToByteArray(hex: String): ByteArray {
        val clean = hex.trim().replace(" ", "").replace("-", "")
        val len = clean.length
        val result = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            val byteVal = clean.substring(i, i + 2).toInt(16)
            result[i / 2] = byteVal.toByte()
        }
        return result
    }

    /**
     * Encodes bytes to standard URL-safe Base64 without trailing padding '=' as specified by W3C ClearKey JWK.
     * Uses java.util.Base64 with fallback to android.util.Base64 so it executes cleanly in both JVM unit tests and Android.
     */
    fun toBase64Url(bytes: ByteArray): String {
        return try {
            java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).trim()
        } catch (_: Throwable) {
            android.util.Base64.encodeToString(
                bytes,
                android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
            ).trim()
        }
    }

    /**
     * Converts a map of {kid: key} to standard W3C ClearKey JSON format expected by Android MediaDrm ClearKey CDM:
     * {"keys":[{"kty":"oct","k":"<key>","kid":"<kid>"},...],"type":"temporary"}
     */
    fun createClearKeyJwkJson(clearKeys: Map<String, String>): String {
        val keyEntries = clearKeys.map { (kid, key) ->
            val kidBase64 = toBase64Url(hexToByteArray(kid))
            val keyBase64 = toBase64Url(hexToByteArray(key))
            """{"kty":"oct","k":"$keyBase64","kid":"$kidBase64"}"""
        }.joinToString(",")
        return """{"keys":[$keyEntries],"type":"temporary"}"""
    }

    fun createClearKeyJwkBytes(clearKeys: Map<String, String>): ByteArray {
        return createClearKeyJwkJson(clearKeys).toByteArray(Charsets.UTF_8)
    }

    /**
     * Overloaded helper for a single kid and key pair.
     */
    fun createClearKeyJwkJson(kidHex: String, keyHex: String): String {
        return createClearKeyJwkJson(mapOf(kidHex to keyHex))
    }

    fun createClearKeyJwkBytes(kidHex: String, keyHex: String): ByteArray {
        return createClearKeyJwkJson(kidHex, keyHex).toByteArray(Charsets.UTF_8)
    }
}

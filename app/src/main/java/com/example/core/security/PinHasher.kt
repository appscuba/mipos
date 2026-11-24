package com.example.core.security

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Per-user salted SHA-256 PIN hasher. Never stores plain-text PINs.
 */
object PinHasher {
  private val secureRandom = SecureRandom()

  fun hashPin(pin: String): String {
    val clean = pin.trim()
    require(clean.length in 4..8 && clean.all { it.isDigit() }) {
      "El PIN debe tener entre 4 y 8 dígitos numéricos."
    }
    val salt = ByteArray(16).also { secureRandom.nextBytes(it) }
    val digest = computeSha256(salt, clean.toByteArray(Charsets.UTF_8))
    return "${salt.toHex()}:${digest.toHex()}"
  }

  fun verifyPin(pin: String, storedHash: String?): Boolean {
    if (storedHash.isNullOrBlank()) return true
    val parts = storedHash.split(":")
    if (parts.size != 2) return false
    val salt = parts[0].fromHexOrNull() ?: return false
    val expected = parts[1].fromHexOrNull() ?: return false
    val actual = computeSha256(salt, pin.trim().toByteArray(Charsets.UTF_8))
    return MessageDigest.isEqual(expected, actual)
  }

  private fun computeSha256(salt: ByteArray, input: ByteArray): ByteArray {
    val md = MessageDigest.getInstance("SHA-256")
    md.update(salt)
    md.update(input)
    return md.digest()
  }

  private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

  private fun String.fromHexOrNull(): ByteArray? {
    if (length % 2 != 0) return null
    return try {
      ByteArray(length / 2) { i ->
        substring(i * 2, i * 2 + 2).toInt(16).toByte()
      }
    } catch (_: NumberFormatException) {
      null
    }
  }
}

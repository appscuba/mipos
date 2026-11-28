package com.example.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Per-user salted PIN hasher. Never stores plain-text PINs.
 *
 * Current scheme (fix/security-gaps): PBKDF2WithHmacSHA256 with a per-user
 * 16-byte salt and 600_000 iterations (OWASP recommendation for
 * PBKDF2-HMAC-SHA256), stored as `pbkdf2$<iterations>$<saltHex>:<hashHex>`.
 *
 * Legacy hashes (`<saltHex>:<hashHex>`, single-pass salted SHA-256) are still
 * verified so existing users are not locked out, but [isLegacyHash] flags
 * them: SecurityRepository re-hashes the PIN with PBKDF2 on the next
 * successful login/unlock, and new/changed PINs always use PBKDF2.
 */
object PinHasher {
  const val PBKDF2_ITERATIONS = 600_000
  private const val KEY_LENGTH_BITS = 256
  private const val SALT_BYTES = 16

  private val secureRandom = SecureRandom()

  fun hashPin(pin: String): String {
    val clean = pin.trim()
    require(clean.length in 4..8 && clean.all { it.isDigit() }) {
      "El PIN debe tener entre 4 y 8 dígitos numéricos."
    }
    val salt = ByteArray(SALT_BYTES).also { secureRandom.nextBytes(it) }
    val derived = derivePbkdf2(clean.toCharArray(), salt, PBKDF2_ITERATIONS)
    return "pbkdf2\$$PBKDF2_ITERATIONS\$${salt.toHex()}:${derived.toHex()}"
  }

  fun verifyPin(pin: String, storedHash: String?): Boolean {
    if (storedHash.isNullOrBlank()) return true
    return if (storedHash.startsWith("pbkdf2\$")) {
      verifyPbkdf2(pin, storedHash)
    } else {
      verifyLegacySha256(pin, storedHash)
    }
  }

  /** True for hashes created by the old single-pass SHA-256 scheme. */
  fun isLegacyHash(storedHash: String?): Boolean {
    if (storedHash.isNullOrBlank()) return false
    return !storedHash.startsWith("pbkdf2\$")
  }

  private fun verifyPbkdf2(pin: String, storedHash: String): Boolean {
    // Format: pbkdf2$<iterations>$<saltHex>:<hashHex>
    val withoutPrefix = storedHash.removePrefix("pbkdf2\$")
    val parts = withoutPrefix.split("$")
    if (parts.size != 2) return false
    val iterations = parts[0].toIntOrNull() ?: return false
    val hashParts = parts[1].split(":")
    if (hashParts.size != 2) return false
    val salt = hashParts[0].fromHexOrNull() ?: return false
    val expected = hashParts[1].fromHexOrNull() ?: return false
    val actual = derivePbkdf2(pin.trim().toCharArray(), salt, iterations)
    return MessageDigest.isEqual(expected, actual)
  }

  private fun verifyLegacySha256(pin: String, storedHash: String): Boolean {
    val parts = storedHash.split(":")
    if (parts.size != 2) return false
    val salt = parts[0].fromHexOrNull() ?: return false
    val expected = parts[1].fromHexOrNull() ?: return false
    val actual = computeSha256(salt, pin.trim().toByteArray(Charsets.UTF_8))
    return MessageDigest.isEqual(expected, actual)
  }

  private fun derivePbkdf2(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    val spec = PBEKeySpec(password, salt, iterations, KEY_LENGTH_BITS)
    return factory.generateSecret(spec).encoded
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

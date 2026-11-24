package com.example.core.security

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@Serializable
data class BackupFileEnvelope(
  val magic: String = MAGIC_HEADER,
  val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
  val createdAt: Long = System.currentTimeMillis(),
  val businessName: String,
  val requiresPassphrase: Boolean,
  val saltBase64: String,
  val ivBase64: String,
  val payloadSha256: String,
  val cipherTextBase64: String,
) {
  companion object {
    const val MAGIC_HEADER = "GESTOR_COMERCIAL_BACKUP_V1"
    const val CURRENT_SCHEMA_VERSION = 1
  }
}

/**
 * Offline AES-256-GCM encryption + PBKDF2WithHmacSHA256 + SHA-256 integrity checksum
 * for portable encrypted backups stored via Storage Access Framework.
 */
object BackupCrypto {
  private const val STANDARD_BACKUP_SECRET = "GestorComercial-Offline-Portable-Key-2026"
  private const val PBKDF2_ITERATIONS = 10_000
  private const val KEY_LENGTH_BITS = 256
  private const val GCM_TAG_BITS = 128

  private val secureRandom = SecureRandom()
  private val json = Json { ignoreUnknownKeys = true }

  fun encryptBackup(
    plaintextJson: String,
    businessName: String,
    passphrase: String?,
  ): ByteArray {
    val cleanPass = passphrase?.trim().orEmpty()
    val requiresPassphrase = cleanPass.isNotEmpty()
    val secretChars = (if (requiresPassphrase) cleanPass else STANDARD_BACKUP_SECRET).toCharArray()

    val salt = ByteArray(16).also { secureRandom.nextBytes(it) }
    val iv = ByteArray(12).also { secureRandom.nextBytes(it) }

    val keySpec = deriveKey(secretChars, salt)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_BITS, iv))

    val plainBytes = plaintextJson.toByteArray(Charsets.UTF_8)
    val checksum = sha256Hex(plainBytes)
    val cipherBytes = cipher.doFinal(plainBytes)

    val encoder = Base64.getEncoder()
    val envelope = BackupFileEnvelope(
      businessName = businessName,
      requiresPassphrase = requiresPassphrase,
      saltBase64 = encoder.encodeToString(salt),
      ivBase64 = encoder.encodeToString(iv),
      payloadSha256 = checksum,
      cipherTextBase64 = encoder.encodeToString(cipherBytes),
    )
    return json.encodeToString(envelope).toByteArray(Charsets.UTF_8)
  }

  fun inspectEnvelope(fileBytes: ByteArray): BackupFileEnvelope {
    val raw = try {
      fileBytes.toString(Charsets.UTF_8)
    } catch (e: Exception) {
      throw IllegalArgumentException("El archivo no es un respaldo válido.", e)
    }
    val envelope = try {
      json.decodeFromString<BackupFileEnvelope>(raw)
    } catch (e: Exception) {
      throw IllegalArgumentException("Formato de respaldo inválido o archivo dañado.", e)
    }
    require(envelope.magic == BackupFileEnvelope.MAGIC_HEADER) {
      "Este archivo no corresponde a un respaldo de Gestor Comercial."
    }
    require(envelope.schemaVersion in 1..BackupFileEnvelope.CURRENT_SCHEMA_VERSION) {
      "Versión de respaldo incompatible (${envelope.schemaVersion})."
    }
    return envelope
  }

  fun decryptBackup(fileBytes: ByteArray, passphrase: String?): String {
    val envelope = inspectEnvelope(fileBytes)
    val cleanPass = passphrase?.trim().orEmpty()
    if (envelope.requiresPassphrase && cleanPass.isEmpty()) {
      throw IllegalArgumentException("Este respaldo está protegido con contraseña. Ingrésala para continuar.")
    }

    val secretChars = if (envelope.requiresPassphrase) {
      cleanPass.toCharArray()
    } else {
      STANDARD_BACKUP_SECRET.toCharArray()
    }

    val decoder = Base64.getDecoder()
    val salt = decoder.decode(envelope.saltBase64)
    val iv = decoder.decode(envelope.ivBase64)
    val cipherBytes = decoder.decode(envelope.cipherTextBase64)

    val plainBytes = try {
      val keySpec = deriveKey(secretChars, salt)
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_BITS, iv))
      cipher.doFinal(cipherBytes)
    } catch (e: Exception) {
      throw IllegalArgumentException("Contraseña incorrecta o archivo de respaldo alterado.", e)
    }

    val actualChecksum = sha256Hex(plainBytes)
    require(actualChecksum == envelope.payloadSha256) {
      "Fallo de verificación de integridad (checksum) en el respaldo."
    }
    return plainBytes.toString(Charsets.UTF_8)
  }

  private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
    val tmp = factory.generateSecret(spec)
    return SecretKeySpec(tmp.encoded, "AES")
  }

  private fun sha256Hex(data: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(data)
    return digest.joinToString("") { "%02x".format(it) }
  }
}

package com.notarapi.tidynote.core.backup

import android.content.Context
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDate
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

/**
 * Backup terenkripsi (plan §10) memakai AES-256-GCM + PBKDF2.
 * Built-in Android (javax.crypto), tanpa dependency tambahan agar aman offline.
 * Format file: `TNBAK2.<saltB64>.<ivB64>.<cipherB64>`.
 * Cara pakai: Settings -> Backup terenkripsi -> passphrase -> simpan .tnbak.
 * Restore: pilih file + passphrase sama -> CSV -> impor ke DB.
 */
object EncryptedBackup {

    const val EXTENSION = ".tnbak"
    private const val MAGIC = "TNBAK2"
    private const val ITERATIONS = 100_000
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128

    fun exportEncrypted(context: Context, csv: String, passphrase: String): File {
        require(passphrase.length >= 4) { "Passphrase minimal 4 karakter" }
        val salt = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val enc = cipher.doFinal(csv.toByteArray(Charsets.UTF_8))
        val payload = listOf(MAGIC, b64(salt), b64(iv), b64(enc)).joinToString(".")
        val dir = File(context.filesDir, "backups").apply { mkdirs() }
        val file = File(dir, "tidynote_${LocalDate.now()}$EXTENSION")
        file.writeText(payload)
        return file
    }

    fun importEncrypted(file: File, passphrase: String): String {
        val parts = file.readText().trim().split(".")
        require(parts.size == 4 && parts[0] == MAGIC) { "Bukan file backup TidyNote v2" }
        val key = deriveKey(passphrase, unb64(parts[1]))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, unb64(parts[2])))
        val csv = try {
            String(cipher.doFinal(unb64(parts[3])), Charsets.UTF_8)
        } catch (e: Exception) {
            throw IllegalArgumentException("Passphrase salah atau file rusak")
        }
        require(csv.contains(",")) { "Isi backup tidak dikenali" }
        return csv
    }

    fun shareUri(context: Context, file: File): android.net.Uri =
        FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)

    private fun deriveKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, KEY_BITS)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)
}

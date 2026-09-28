package com.verlintas.baic2.core.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AES/GCM secret storage backed by the Android Keystore. Ciphertexts are
 * self-describing (`v1.<iv>.<ciphertext>`, base64) so the format can evolve.
 *
 * Decryption failures are returned as [Result.failure] — never silently
 * discarded — so callers can surface a clear message instead of losing keys.
 */
@Singleton
class SecretCipher @Inject constructor() {

    fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return buildString {
            append(PREFIX)
            append(Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            append('.')
            append(Base64.encodeToString(ciphertext, Base64.NO_WRAP))
        }
    }

    fun decrypt(payload: String): Result<String> = runCatching {
        require(payload.startsWith(PREFIX)) { "Unsupported ciphertext format" }
        val parts = payload.removePrefix(PREFIX).split('.')
        require(parts.size == 2) { "Malformed ciphertext" }
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, existingKey(), GCMParameterSpec(TAG_BITS, iv))
        String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    fun isEncrypted(payload: String): Boolean = payload.startsWith(PREFIX)

    private fun getOrCreateKey(): SecretKey {
        keyStore().getKey(KEY_ALIAS, null)?.let { return it as SecretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun existingKey(): SecretKey =
        keyStore().getKey(KEY_ALIAS, null) as? SecretKey
            ?: throw IllegalStateException("Encryption key is missing")

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "baic2_secret_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val PREFIX = "v1."
    }
}

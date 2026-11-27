package dev.lunikodevelopment.ludoform.transfer

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Upload server passwords, AES-GCM encrypted with a key that never leaves the Android Keystore, one entry per
 * server id. A copy of the preferences file alone does not reveal them, and nothing here is ever logged.
 */
class UploadSecrets(context: Context, file: String = FILE) {
    private val prefs = context.applicationContext.getSharedPreferences(file, Context.MODE_PRIVATE)

    fun has(serverId: Long): Boolean = prefs.contains(key(serverId))

    /** Null when none is saved or the Keystore key is gone (for example after a restore to another device). */
    fun password(serverId: Long): String? = prefs.getString(key(serverId), null)?.let(::open)

    fun save(
        serverId: Long,
        password: String,
    ) {
        prefs.edit().putString(key(serverId), seal(password)).apply()
    }

    fun remove(serverId: Long) {
        prefs.edit().remove(key(serverId)).apply()
    }

    private fun key(serverId: Long) = "password.$serverId"

    private fun seal(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return encode(cipher.iv) + ":" + encode(sealed)
    }

    private fun open(sealed: String): String? =
        runCatching {
            val (iv, data) = sealed.split(':', limit = 2).map { Base64.decode(it, Base64.NO_WRAP) }
            val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv)) }
            String(cipher.doFinal(data), Charsets.UTF_8)
        }.getOrNull()

    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    private companion object {
        const val FILE = "upload-secrets"
        const val ALIAS = "ludoform.upload.secret"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

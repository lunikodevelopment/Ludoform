package dev.lunikodevelopment.ludoform.romm

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The saved server. The password is kept apart, encrypted, and only read when a request needs it. */
data class RommServer(val address: String, val username: String)

/** The last health check, kept so Settings can show it after a restart. */
data class RommCheck(val health: RommHealth, val checkedAt: Long)

/**
 * The RomM server settings. The address and user name are plain preferences; the password is AES-GCM encrypted with
 * a key that never leaves the Android Keystore, so a copy of the preferences file alone does not reveal it.
 */
class RommSettings(context: Context, file: String = FILE) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(file, Context.MODE_PRIVATE)

    fun server(): RommServer? {
        val address = prefs.getString(ADDRESS, null) ?: return null
        return RommServer(address, prefs.getString(USERNAME, "").orEmpty())
    }

    fun hasPassword(): Boolean = prefs.contains(PASSWORD)

    /** Null when no password is saved or the Keystore key is gone (for example after a restore to another device). */
    fun password(): String? = prefs.getString(PASSWORD, null)?.let { SecretBox.open(it) }

    /**
     * Saves the server. A null [password] keeps the saved one, so editing the address does not ask for it again.
     * Any change clears the last check, which no longer describes the saved server.
     */
    fun save(
        address: String,
        username: String,
        password: String?,
    ) {
        prefs.edit().apply {
            putString(ADDRESS, address)
            putString(USERNAME, username)
            if (password != null) putString(PASSWORD, SecretBox.seal(password))
            clearCheck(this)
        }.apply()
    }

    fun remove() {
        prefs.edit().clear().apply()
    }

    fun lastCheck(): RommCheck? {
        val kind = prefs.getString(CHECK_KIND, null) ?: return null
        val version = prefs.getString(CHECK_VERSION, "").orEmpty()
        val detail = prefs.getString(CHECK_DETAIL, "").orEmpty()
        val health =
            when (kind) {
                "bad_address" -> RommHealth.BadAddress
                "unreachable" -> RommHealth.Unreachable(detail)
                "not_romm" -> RommHealth.NotRomm
                "too_old" -> RommHealth.TooOld(version)
                "sign_in_failed" -> RommHealth.SignInFailed(version)
                "server_error" -> RommHealth.ServerError(version, detail.toIntOrNull() ?: 0)
                "connected" -> RommHealth.Connected(version, detail)
                else -> return null
            }
        return RommCheck(health, prefs.getLong(CHECK_AT, 0))
    }

    fun saveCheck(check: RommCheck) {
        val (kind, version, detail) =
            when (val health = check.health) {
                RommHealth.BadAddress -> Triple("bad_address", "", "")
                is RommHealth.Unreachable -> Triple("unreachable", "", health.reason)
                RommHealth.NotRomm -> Triple("not_romm", "", "")
                is RommHealth.TooOld -> Triple("too_old", health.version, "")
                is RommHealth.SignInFailed -> Triple("sign_in_failed", health.version, "")
                is RommHealth.ServerError -> Triple("server_error", health.version, health.code.toString())
                is RommHealth.Connected -> Triple("connected", health.version, health.username)
            }
        prefs.edit()
            .putString(CHECK_KIND, kind)
            .putString(CHECK_VERSION, version)
            .putString(CHECK_DETAIL, detail)
            .putLong(CHECK_AT, check.checkedAt)
            .apply()
    }

    /** Emits once at once and again after every change. */
    val changes: Flow<Unit> =
        callbackFlow {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(Unit) }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            trySend(Unit)
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }

    private fun clearCheck(editor: SharedPreferences.Editor) {
        listOf(CHECK_KIND, CHECK_VERSION, CHECK_DETAIL, CHECK_AT).forEach { editor.remove(it) }
    }

    private companion object {
        const val FILE = "romm"
        const val ADDRESS = "address"
        const val USERNAME = "username"
        const val PASSWORD = "password.sealed"
        const val CHECK_KIND = "check.kind"
        const val CHECK_VERSION = "check.version"
        const val CHECK_DETAIL = "check.detail"
        const val CHECK_AT = "check.at"
    }
}

/** AES-GCM with a Keystore key. A sealed value is `base64(iv):base64(ciphertext)`. */
internal object SecretBox {
    private const val ALIAS = "ludoform.romm.secret"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    fun seal(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return encode(cipher.iv) + ":" + encode(sealed)
    }

    fun open(sealed: String): String? =
        runCatching {
            val (iv, data) = sealed.split(':', limit = 2).map { Base64.decode(it, Base64.NO_WRAP) }
            val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
            String(cipher.doFinal(data), Charsets.UTF_8)
        }.getOrNull()

    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun key(): SecretKey {
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
}

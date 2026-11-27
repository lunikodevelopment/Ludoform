package dev.lunikodevelopment.ludoform.romm

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dev.lunikodevelopment.ludoform.data.RommOffline
import dev.lunikodevelopment.ludoform.data.RommOutage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Waits between automatic tries while RomM is down. */
val ROMM_RETRY_STEPS = listOf(30_000L, 60_000L, 120_000L, 300_000L, 600_000L, 900_000L)

/** The saved server. The password is kept apart, encrypted, and only read when a request needs it. */
data class RommServer(val address: String, val username: String)

/** The last health check, kept so Settings can show it after a restart. */
data class RommCheck(val health: RommHealth, val checkedAt: Long)

/** The last library sync. [problem] is null when it succeeded. */
data class RommSyncResult(val at: Long, val games: Int, val problem: RommRequestException.Kind?)

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

    /** The server the library tables were filled from, `address|username`; a different one clears them first. */
    var syncedServer: String?
        get() = prefs.getString(SYNC_SERVER, null)
        set(value) = prefs.edit().putString(SYNC_SERVER, value).apply()

    /** The newest server `updated_at` seen, for the next incremental request. */
    var syncWatermark: String?
        get() = prefs.getString(SYNC_WATERMARK, null)
        set(value) = prefs.edit().putString(SYNC_WATERMARK, value).apply()

    var lastFullSync: Long
        get() = prefs.getLong(SYNC_FULL_AT, 0)
        set(value) = prefs.edit().putLong(SYNC_FULL_AT, value).apply()

    /** RomM as down since a failed request, with the next automatic try; null while it works. */
    fun offline(): RommOffline? {
        val name = prefs.getString(OUTAGE, null) ?: return null
        val outage = RommOutage.entries.firstOrNull { it.name == name } ?: return null
        return RommOffline(outage, prefs.getLong(OUTAGE_SINCE, 0), prefs.getLong(OUTAGE_NEXT, 0))
    }

    /**
     * Records that RomM failed with [outage] and returns how long to wait before trying again: 30 s, then longer
     * each time up to 15 minutes, so a server that stays down is not asked every few seconds.
     */
    fun reportOutage(
        outage: RommOutage,
        now: Long,
    ): Long {
        val step = if (prefs.contains(OUTAGE)) (prefs.getInt(OUTAGE_STEP, 0) + 1).coerceAtMost(ROMM_RETRY_STEPS.lastIndex) else 0
        val wait = ROMM_RETRY_STEPS[step]
        prefs.edit()
            .putString(OUTAGE, outage.name)
            .putLong(OUTAGE_SINCE, if (prefs.contains(OUTAGE)) prefs.getLong(OUTAGE_SINCE, now) else now)
            .putLong(OUTAGE_NEXT, now + wait)
            .putInt(OUTAGE_STEP, step)
            .apply()
        return wait
    }

    /** RomM answered: it is not down any more. */
    fun reportReachable() {
        if (!prefs.contains(OUTAGE)) return
        prefs.edit().remove(OUTAGE).remove(OUTAGE_SINCE).remove(OUTAGE_NEXT).remove(OUTAGE_STEP).apply()
    }

    /** Which version of the sync filled the ROM rows (see `RommSyncWorker`). */
    var syncFormat: Int
        get() = prefs.getInt(SYNC_FORMAT, 0)
        set(value) = prefs.edit().putInt(SYNC_FORMAT, value).apply()

    fun lastSync(): RommSyncResult? {
        if (!prefs.contains(SYNC_AT)) return null
        val problem = prefs.getString(SYNC_PROBLEM, null)?.let { name -> RommRequestException.Kind.entries.firstOrNull { it.name == name } }
        return RommSyncResult(prefs.getLong(SYNC_AT, 0), prefs.getInt(SYNC_GAMES, 0), problem)
    }

    fun saveSync(result: RommSyncResult) {
        prefs.edit().putLong(SYNC_AT, result.at).putInt(SYNC_GAMES, result.games)
            .apply { if (result.problem == null) remove(SYNC_PROBLEM) else putString(SYNC_PROBLEM, result.problem.name) }
            .apply()
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
        const val SYNC_SERVER = "sync.server"
        const val SYNC_WATERMARK = "sync.watermark"
        const val SYNC_FULL_AT = "sync.full_at"
        const val SYNC_AT = "sync.at"
        const val SYNC_GAMES = "sync.games"
        const val SYNC_PROBLEM = "sync.problem"
        const val SYNC_FORMAT = "sync.format"
        const val OUTAGE = "outage"
        const val OUTAGE_SINCE = "outage.since"
        const val OUTAGE_NEXT = "outage.next"
        const val OUTAGE_STEP = "outage.step"
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

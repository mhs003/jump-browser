package com.jump.browser.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** One saved login. The password is only ever held decrypted in memory. */
data class SavedLogin(
    val id: Long,
    val host: String,
    val username: String,
    val password: String,
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * Local password store.
 *
 * Saved logins are encrypted with an AES key held in the Android keystore, so
 * the on-disk file is useless without the device: this is the minimum bar for
 * storing credentials, even though the store is local and never synced.
 */
class PasswordRepository(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val prefs = context.getSharedPreferences("via_clone_prefs", Context.MODE_PRIVATE)
    private val idGen = java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis())

    var logins by mutableStateOf<List<SavedLogin>>(emptyList())
        private set

    /** True when the keystore could not be used, in which case nothing is saved. */
    var unavailable by mutableStateOf(false)
        private set

    init {
        // Decryption is quick, but reading prefs and touching the keystore on
        // the main thread during startup is avoidable.
        scope.launch(Dispatchers.IO) {
            val decoded = readAll()
            logins = decoded
        }
    }

    fun forHost(host: String?): List<SavedLogin> {
        if (host.isNullOrBlank()) return emptyList()
        return logins.filter { it.host.equals(host, ignoreCase = true) }
    }

    /** Saves or updates the login for a host and username pair. */
    fun save(host: String, username: String, password: String): SavedLogin? {
        if (host.isBlank() || username.isBlank()) return null
        val existing = logins.firstOrNull {
            it.host.equals(host, true) && it.username == username
        }
        val entry = existing?.copy(password = password, updatedAt = System.currentTimeMillis())
            ?: SavedLogin(idGen.incrementAndGet(), host, username, password)
        val next = if (existing != null) {
            logins.map { if (it.id == existing.id) entry else it }
        } else {
            logins + entry
        }
        if (!writeAll(next)) return null
        logins = next
        return entry
    }

    fun remove(id: Long) {
        val next = logins.filterNot { it.id == id }
        if (writeAll(next)) logins = next
    }

    fun clear() {
        prefs.edit().remove(KEY_PAYLOADS).apply()
        logins = emptyList()
    }

    // ------------------------------------------------------------- storage

    private fun keystoreKey(): SecretKey? = runCatching {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey ?: run {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            generator.init(
                KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generator.generateKey()
        }
    }.getOrNull()

    private fun encrypt(plain: String, key: SecretKey): String? = runCatching {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val body = cipher.doFinal(plain.toByteArray())
        Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(body, Base64.NO_WRAP)
    }.getOrNull()

    private fun decrypt(payload: String, key: SecretKey): String? = runCatching {
        val (ivPart, bodyPart) = payload.split(":", limit = 2)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(128, Base64.decode(ivPart, Base64.NO_WRAP)),
        )
        String(cipher.doFinal(Base64.decode(bodyPart, Base64.NO_WRAP)))
    }.getOrNull()

    private fun readAll(): List<SavedLogin> {
        val raw = prefs.getString(KEY_PAYLOADS, null) ?: return emptyList()
        val key = keystoreKey() ?: run {
            unavailable = true
            return emptyList()
        }
        val plain = decrypt(raw, key) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(plain)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                SavedLogin(
                    id = o.optLong("id"),
                    host = o.optString("host"),
                    username = o.optString("username"),
                    password = o.optString("password"),
                    updatedAt = o.optLong("updatedAt"),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun writeAll(items: List<SavedLogin>): Boolean {
        val key = keystoreKey() ?: run {
            unavailable = true
            return false
        }
        val json = JSONArray().also { arr ->
            items.forEach { login ->
                arr.put(
                    JSONObject()
                        .put("id", login.id)
                        .put("host", login.host)
                        .put("username", login.username)
                        .put("password", login.password)
                        .put("updatedAt", login.updatedAt),
                )
            }
        }.toString()
        val payload = encrypt(json, key) ?: return false
        prefs.edit().putString(KEY_PAYLOADS, payload).apply()
        return true
    }

    private companion object {
        const val ALIAS = "jump_passwords"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_PAYLOADS = "password_payloads"
    }
}

/** Fills a login into the page: works with plain forms and most SPAs. */
object LoginAutofill {
    fun script(username: String, password: String): String {
        fun q(value: String) = JSONObject.quote(value)
        return """
            (function(){
              var u = ${q(username)}, p = ${q(password)};
              function set(el, value) {
                if (!el) return false;
                var proto = el instanceof HTMLTextAreaElement
                    ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
                var setter = Object.getOwnPropertyDescriptor(proto, 'value');
                if (setter && setter.set) setter.set.call(el, value); else el.value = value;
                el.dispatchEvent(new Event('input', {bubbles: true}));
                el.dispatchEvent(new Event('change', {bubbles: true}));
                return true;
              }
              var pw = document.querySelector('input[type=password]:not([disabled])');
              if (!pw) return 'none';
              var form = pw.form || document;
              var user = form.querySelector(
                'input[autocomplete=username], input[type=email], input[type=text]:not([disabled]), input[type=tel]');
              if (user) set(user, u);
              set(pw, p);
              return 'ok';
            })();
        """.trimIndent()
    }

    /**
     * Reads a submitted login from the page. Only called after a form submits,
     * so credentials are never harvested from pages the user just visited.
     */
    val captureScript: String = """
        (function(){
          var pw = document.querySelector('input[type=password]');
          if (!pw || !pw.value) return '';
          var form = pw.form || document;
          var user = form.querySelector(
            'input[autocomplete=username], input[type=email], input[type=text], input[type=tel]');
          return JSON.stringify({u: user ? user.value : '', p: pw.value});
        })();
    """.trimIndent()
}

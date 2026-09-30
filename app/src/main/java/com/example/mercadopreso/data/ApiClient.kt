package com.example.mercadopreso.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.mercadopreso.BuildConfig
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Somente HTTPS. DATABASE_URL e senha do Neon nunca pertencem ao Android. */
object ApiClient {
    val enabled
        get() = BuildConfig.API_BASE_URL.isNotBlank()

    private lateinit var prefs: android.content.SharedPreferences
    private var token: String? = null
    val hasSession
        get() = token != null

    fun init(context: Context) {
        prefs = context.getSharedPreferences("mercado_preso_api", Context.MODE_PRIVATE)
        token = null
        if (!enabled || prefs.getString("server", null) != BuildConfig.API_BASE_URL) return
        try {
            val raw = prefs.getString("token", null) ?: return
            val bytes = Base64.decode(raw, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            token = String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
        } catch (_: Exception) {
            clearSession()
        }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("mercado_preso_session", null) as? SecretKey)?.let {
            return it
        }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                            "mercado_preso_session",
                            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                        )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build()
                )
            }
            .generateKey()
    }

    fun saveSession(value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encoded =
            Base64.encodeToString(
                cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)),
                Base64.NO_WRAP,
            )
        check(
            prefs
                .edit()
                .putString("server", BuildConfig.API_BASE_URL)
                .putString("token", encoded)
                .commit()
        ) {
            "Não foi possível salvar a sessão."
        }
        token = value
    }

    fun clearSession() {
        token = null
        prefs.edit().remove("token").remove("checkoutKey").remove("checkoutFingerprint").commit()
    }

    fun checkoutKey(fingerprint: String): String {
        if (prefs.getString("checkoutFingerprint", null) == fingerprint)
            prefs.getString("checkoutKey", null)?.let {
                return it
            }
        val key = java.util.UUID.randomUUID().toString()
        check(
            prefs
                .edit()
                .putString("checkoutFingerprint", fingerprint)
                .putString("checkoutKey", key)
                .commit()
        )
        return key
    }

    fun checkoutCompleted() {
        prefs.edit().remove("checkoutKey").remove("checkoutFingerprint").commit()
    }

    suspend fun request(path: String, body: JSONObject? = null): JSONObject =
        withContext(Dispatchers.IO) {
            val base = BuildConfig.API_BASE_URL.trimEnd('/')
            require(base.startsWith("https://")) { "Configure uma URL HTTPS para a API." }
            val connection = URL(base + path).openConnection() as HttpsURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.requestMethod = if (body == null) "GET" else "POST"
            connection.setRequestProperty("Accept", "application/json")
            token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            try {
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.outputStream.use {
                        it.write(body.toString().toByteArray(Charsets.UTF_8))
                    }
                }
                val status = connection.responseCode
                val raw =
                    (if (status in 200..299) connection.inputStream else connection.errorStream)
                        ?.bufferedReader(Charsets.UTF_8)
                        ?.use { it.readText() }
                        .orEmpty()
                val result = if (raw.isBlank()) JSONObject() else JSONObject(raw)
                if (status !in 200..299) {
                    val detail = result.opt("detail") as? String
                    error(detail ?: "Não foi possível concluir a operação (HTTP $status).")
                }
                result
            } finally {
                connection.disconnect()
            }
        }
}

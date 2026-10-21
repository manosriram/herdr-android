package dev.herdr.android

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts secrets (SSH key seed, passwords) with a non-exportable Android Keystore key. */
object Vault {
    private const val ALIAS = "herdr"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as SecretKey?)?.let { return it }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return g.generateKey()
    }

    fun seal(plain: ByteArray): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        return Base64.encodeToString(c.iv + c.doFinal(plain), Base64.NO_WRAP)
    }

    fun open(sealed: String): ByteArray {
        val d = Base64.decode(sealed, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, d, 0, 12))
        return c.doFinal(d, 12, d.size - 12)
    }
}

class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("herdr", Context.MODE_PRIVATE)

    fun machines(): List<Machine> {
        val a = JSONArray(p.getString("machines", "[]"))
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val pw = o.optString("password_sealed").takeIf { it.isNotEmpty() }?.let { Vault.open(it).decodeToString() }
            machineFromJson(o, pw)
        }
    }

    fun saveMachines(list: List<Machine>) {
        val a = JSONArray()
        list.forEach { m ->
            a.put(m.toJson().apply { m.password?.let { put("password_sealed", Vault.seal(it.encodeToByteArray())) } })
        }
        p.edit().putString("machines", a.toString()).apply()
    }

    fun hostKey(id: String) = p.getString("hostkey_$id", null)
    fun setHostKey(id: String, fp: String?) = p.edit().putString("hostkey_$id", fp).apply()

    var keySeed: ByteArray?
        get() = p.getString("key_seed", null)?.let { Vault.open(it) }
        set(v) = p.edit().putString("key_seed", v?.let { Vault.seal(it) }).apply()

    var theme: String
        get() = p.getString("theme", "system")!!
        set(v) = p.edit().putString("theme", v).apply()

    var onboarded: Boolean
        get() = p.getBoolean("onboarded", false)
        set(v) = p.edit().putBoolean("onboarded", v).apply()

    var notifyBlocked: Boolean
        get() = p.getBoolean("notify_blocked", true)
        set(v) = p.edit().putBoolean("notify_blocked", v).apply()

    var notifyDone: Boolean
        get() = p.getBoolean("notify_done", false)
        set(v) = p.edit().putBoolean("notify_done", v).apply()
}

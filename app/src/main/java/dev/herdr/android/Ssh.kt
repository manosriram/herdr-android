package dev.herdr.android

import android.util.Base64
import net.i2p.crypto.eddsa.EdDSAPrivateKey
import net.i2p.crypto.eddsa.EdDSAPublicKey
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.transport.TransportException
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.userauth.UserAuthException
import net.schmizz.sshj.userauth.keyprovider.KeyPairWrapper
import net.schmizz.sshj.userauth.method.AuthMethod
import net.schmizz.sshj.userauth.method.AuthNone
import net.schmizz.sshj.userauth.method.AuthPassword
import net.schmizz.sshj.userauth.method.AuthPublickey
import net.schmizz.sshj.userauth.password.PasswordFinder
import net.schmizz.sshj.userauth.password.Resource
import java.io.IOException
import java.security.KeyPair
import java.security.MessageDigest
import java.security.PublicKey
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

class AuthFailed : Exception("SSH login refused")
class HostKeyChanged : Exception("Host key changed. Remove and re-add this machine if that's expected.")
class HerdrError(val code: Int, msg: String) :
    Exception(if (code == 127) "herdr not found on this machine" else msg.ifBlank { "herdr exited with $code" })

/** The phone's Ed25519 identity. */
object PhoneKey {
    private val curve = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519)

    fun load(store: Store): KeyPair {
        val seed = store.keySeed ?: ByteArray(32).also { SecureRandom().nextBytes(it); store.keySeed = it }
        val priv = EdDSAPrivateKeySpec(seed, curve)
        return KeyPair(EdDSAPublicKey(EdDSAPublicKeySpec(priv.a, curve)), EdDSAPrivateKey(priv))
    }

    fun authorizedKeysLine(kp: KeyPair) =
        "ssh-ed25519 " + Base64.encodeToString(blob(kp.public), Base64.NO_WRAP) + " herdr-android"

    fun fingerprint(key: PublicKey) =
        "SHA256:" + Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(blob(key)), Base64.NO_WRAP or Base64.NO_PADDING)

    private fun blob(key: PublicKey) = Buffer.PlainBuffer().putPublicKey(key).compactData
}

/** One SSH connection to a machine; commands run sequentially on it. */
class Conn(private val m: Machine, private val store: Store, private val key: KeyPair) {
    private var ssh: SSHClient? = null

    /** stage(1) after TCP + host key, stage(2) after login. */
    @Synchronized
    fun open(stage: (Int) -> Unit = {}): SSHClient {
        ssh?.let { if (it.isConnected && it.isAuthenticated) return it }
        close()
        val c = SSHClient(DefaultConfig())
        var changed = false
        c.addHostKeyVerifier(object : HostKeyVerifier {
            // Trust on first use, then pin.
            override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
                val fp = PhoneKey.fingerprint(key)
                val known = store.hostKey(m.id) ?: return true.also { store.setHostKey(m.id, fp) }
                return (known == fp).also { changed = !it }
            }

            override fun findExistingAlgorithms(hostname: String, port: Int): List<String> = emptyList()
        })
        c.connectTimeout = 8000
        c.timeout = 20000
        try {
            c.connect(m.host, m.port)
        } catch (e: TransportException) {
            if (changed) throw HostKeyChanged()
            throw e
        }
        stage(1)
        // "none" first: Tailscale SSH accepts it without any key on the machine.
        val methods = mutableListOf<AuthMethod>(AuthNone(), AuthPublickey(KeyPairWrapper(key)))
        m.password?.let { pw ->
            methods += AuthPassword(object : PasswordFinder {
                override fun reqPassword(resource: Resource<*>?) = pw.toCharArray()
                override fun shouldRetry(resource: Resource<*>?) = false
            })
        }
        try {
            c.auth(m.user, methods)
        } catch (e: UserAuthException) {
            c.disconnect()
            throw AuthFailed()
        }
        stage(2)
        ssh = c
        return c
    }

    @Synchronized
    fun exec(command: String): String {
        val c = open()
        try {
            c.startSession().use { s ->
                val cmd = s.exec(command)
                // ponytail: reads stdout then stderr; fine for herdr's small outputs, would block on huge stderr.
                val out = cmd.inputStream.readBytes().decodeToString()
                val err = cmd.errorStream.readBytes().decodeToString()
                cmd.join(30, TimeUnit.SECONDS)
                val code = cmd.exitStatus ?: -1
                if (code != 0) throw HerdrError(code, err.ifBlank { out }.trim())
                return out
            }
        } catch (e: IOException) {
            close()
            throw e
        }
    }

    @Synchronized
    fun close() {
        runCatching { ssh?.disconnect() }
        ssh = null
    }
}

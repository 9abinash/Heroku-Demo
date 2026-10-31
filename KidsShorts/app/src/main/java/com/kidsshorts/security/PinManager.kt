package com.kidsshorts.security

import android.content.Context
import android.util.Base64
import com.kidsshorts.domain.PinAttemptState
import com.kidsshorts.domain.PinPolicy
import com.kidsshorts.domain.PinThrottle
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Parent PIN vault.
 *  - The PIN is never stored. We store PBKDF2-HMAC-SHA256(salt, PIN) and the whole credential
 *    record (hashes + throttle state) is encrypted with a non-exportable Android Keystore key.
 *  - A random one-time recovery code is generated at PIN creation and shown ONCE. Only its hash is
 *    kept, so the PIN itself can never be shown back. Forgetting both means clearing app data.
 *  - Wrong PIN and wrong recovery-code attempts share ONE persisted throttle counter.
 *
 * Honest limits: this raises the bar for a child; it is not tamper-proof (a rooted device, "clear
 * app data" or uninstalling bypass it) and it does not replace Android's own parental controls.
 */
class PinManager(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    sealed interface VerifyResult {
        data object Success : VerifyResult
        data class Wrong(val failures: Int) : VerifyResult
        data class Locked(val remainingMs: Long) : VerifyResult
        data object NotSet : VerifyResult
    }

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val cipher = KeystoreCipher(KEY_ALIAS)
    private val random = SecureRandom()

    @Synchronized fun isPinSet(): Boolean = load() != null

    /** Creates the PIN and returns the one-time recovery code (display once, never persisted in clear). */
    @Synchronized
    fun createPin(pin: String): String {
        require(PinPolicy.validate(pin) == null) { "PIN does not meet policy" }
        val recovery = newRecoveryCode()
        save(newRecord(pin, recovery, PinAttemptState(0, 0L, clock())))
        return recovery
    }

    @Synchronized
    fun verifyPin(pin: String): VerifyResult = checkSecret(pin, loadWith { it.pinSalt to it.pinHash })

    /** Verifies the recovery code (consumes a throttle attempt on failure). Does not change the PIN. */
    @Synchronized
    fun verifyRecoveryCode(code: String): VerifyResult = checkSecret(normalizeRecovery(code), loadWith { it.recSalt to it.recHash })

    /** Resets the PIN after a successful recovery-code check and returns a NEW recovery code. */
    @Synchronized
    fun resetPinWithRecovery(code: String, newPin: String): String? {
        require(PinPolicy.validate(newPin) == null) { "PIN does not meet policy" }
        if (verifyRecoveryCode(code) !is VerifyResult.Success) return null
        val newRecovery = newRecoveryCode()
        save(newRecord(newPin, newRecovery, PinAttemptState(0, 0L, clock())))
        return newRecovery
    }

    @Synchronized
    fun changePin(oldPin: String, newPin: String): VerifyResult {
        require(PinPolicy.validate(newPin) == null) { "PIN does not meet policy" }
        val result = verifyPin(oldPin)
        if (result is VerifyResult.Success) {
            val rec = load() ?: return VerifyResult.NotSet
            val salt = randomBytes(SALT_BYTES)
            save(rec.copy(pinSalt = salt, pinHash = hash(newPin, salt)))
        }
        return result
    }

    fun lockoutRemainingMs(): Long = synchronized(this) {
        val rec = load() ?: return 0L
        PinThrottle.remainingLockMs(rec.attempts, clock())
    }

    /** Removes every trace of the credential (used by "reset all data"). */
    @Synchronized fun wipe() {
        prefs.edit().clear().apply()
        cipher.deleteKey()
    }

    // ---------------------------------------------------------------- internals

    private class Record(
        val pinSalt: ByteArray, val pinHash: ByteArray,
        val recSalt: ByteArray, val recHash: ByteArray,
        val attempts: PinAttemptState,
    ) {
        fun copy(
            pinSalt: ByteArray = this.pinSalt, pinHash: ByteArray = this.pinHash,
            recSalt: ByteArray = this.recSalt, recHash: ByteArray = this.recHash,
            attempts: PinAttemptState = this.attempts,
        ) = Record(pinSalt, pinHash, recSalt, recHash, attempts)
    }

    private fun loadWith(select: (Record) -> Pair<ByteArray, ByteArray>): Pair<Record, Pair<ByteArray, ByteArray>>? {
        val rec = load() ?: return null
        return rec to select(rec)
    }

    private fun checkSecret(candidate: String, loaded: Pair<Record, Pair<ByteArray, ByteArray>>?): VerifyResult {
        if (loaded == null) return VerifyResult.NotSet
        val (rec, saltAndHash) = loaded
        val now = clock()
        if (PinThrottle.isLocked(rec.attempts, now)) {
            return VerifyResult.Locked(PinThrottle.remainingLockMs(rec.attempts, now))
        }
        val ok = MessageDigest.isEqual(hash(candidate, saltAndHash.first), saltAndHash.second)
        return if (ok) {
            save(rec.copy(attempts = PinThrottle.onSuccess(now, rec.attempts)))
            VerifyResult.Success
        } else {
            val next = PinThrottle.onFailure(rec.attempts, now)
            save(rec.copy(attempts = next))
            if (PinThrottle.isLocked(next, now)) VerifyResult.Locked(PinThrottle.remainingLockMs(next, now))
            else VerifyResult.Wrong(next.consecutiveFailures)
        }
    }

    private fun newRecord(pin: String, recovery: String, attempts: PinAttemptState): Record {
        val ps = randomBytes(SALT_BYTES)
        val rs = randomBytes(SALT_BYTES)
        return Record(ps, hash(pin, ps), rs, hash(normalizeRecovery(recovery), rs), attempts)
    }

    private fun hash(secret: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(secret.toCharArray(), salt, ITERATIONS, HASH_BITS)).encoded

    private fun randomBytes(n: Int) = ByteArray(n).also(random::nextBytes)

    private fun newRecoveryCode(): String {
        val chars = CharArray(RECOVERY_LENGTH) { RECOVERY_ALPHABET[random.nextInt(RECOVERY_ALPHABET.length)] }
        return String(chars).chunked(4).joinToString("-")
    }

    private fun normalizeRecovery(code: String) = code.uppercase().filter { it.isLetterOrDigit() }

    private fun load(): Record? {
        val blob = prefs.getString(KEY_BLOB, null) ?: return null
        val bytes = cipher.decrypt(blob) ?: return null // unreadable => treated as "not set"
        return try {
            val j = JSONObject(String(bytes, Charsets.UTF_8))
            Record(
                b64(j.getString("ps")), b64(j.getString("ph")), b64(j.getString("rs")), b64(j.getString("rh")),
                PinAttemptState(j.getInt("f"), j.getLong("lu"), j.getLong("ls")),
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun save(r: Record) {
        val j = JSONObject()
            .put("ps", b64(r.pinSalt)).put("ph", b64(r.pinHash))
            .put("rs", b64(r.recSalt)).put("rh", b64(r.recHash))
            .put("f", r.attempts.consecutiveFailures).put("lu", r.attempts.lockedUntilEpochMs).put("ls", r.attempts.lastSeenEpochMs)
        prefs.edit().putString(KEY_BLOB, cipher.encrypt(j.toString().toByteArray(Charsets.UTF_8))).apply()
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun b64(s: String) = Base64.decode(s, Base64.NO_WRAP)

    private companion object {
        const val PREFS = "parent_auth"
        const val KEY_BLOB = "credential"
        const val KEY_ALIAS = "kidsshorts_parent_auth_v1"
        const val SALT_BYTES = 16
        const val ITERATIONS = 210_000
        const val HASH_BITS = 256
        const val RECOVERY_LENGTH = 16
        /** No I, O, 0, 1: avoids look-alike characters when a parent copies the code by hand. */
        const val RECOVERY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    }
}

package com.kidsshorts.security

import android.content.Context

/**
 * Holds the family's own YouTube Data API key, encrypted at rest with a Keystore key.
 * Policy note: the Developer Policies forbid shipping API credentials in open-source code, so there
 * is no bundled key; each family supplies its own (or a developer injects one via local.properties
 * for personal builds only).
 */
class ApiKeyStore(context: Context, private val buildTimeKey: String) {
    private val prefs = context.applicationContext.getSharedPreferences("api_key", Context.MODE_PRIVATE)
    private val cipher = KeystoreCipher("kidsshorts_api_key_v1")

    /** Runtime-entered key wins over a build-time key. Returns "" when none is configured. */
    @Synchronized fun get(): String {
        val stored = prefs.getString("k", null)?.let { cipher.decrypt(it) }?.toString(Charsets.UTF_8)
        return (stored?.takeIf { it.isNotBlank() } ?: buildTimeKey).trim()
    }

    @Synchronized fun set(key: String) {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) prefs.edit().remove("k").apply()
        else prefs.edit().putString("k", cipher.encrypt(trimmed.toByteArray(Charsets.UTF_8))).apply()
    }

    @Synchronized fun hasRuntimeKey(): Boolean = prefs.contains("k")

    @Synchronized fun wipe() {
        prefs.edit().clear().apply()
        cipher.deleteKey()
    }
}

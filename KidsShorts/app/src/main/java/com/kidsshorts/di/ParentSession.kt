package com.kidsshorts.di

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-memory "parent area is unlocked" flag. Never persisted, so a process restart always re-locks. */
class ParentSession {
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()
    val isUnlocked: Boolean get() = _unlocked.value
    fun unlock() { _unlocked.value = true }
    fun lock() { _unlocked.value = false }
}

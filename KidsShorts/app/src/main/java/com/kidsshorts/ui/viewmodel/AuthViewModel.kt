package com.kidsshorts.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.kidsshorts.data.repo.AuditType
import com.kidsshorts.di.AppContainer
import com.kidsshorts.domain.PinPolicy
import com.kidsshorts.security.PinManager
import com.kidsshorts.security.PinManager.VerifyResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface CreatePinResult {
    data class Created(val recoveryCode: String) : CreatePinResult
    data class Invalid(val message: String) : CreatePinResult
}

/** Thin wrapper so UI never touches the PIN vault directly. PBKDF2 runs off the main thread. */
class AuthViewModel(private val c: AppContainer) : ViewModel() {

    fun isPinSet(): Boolean = c.pinManager.isPinSet()
    val unlocked get() = c.parentSession.unlocked

    suspend fun createPin(pin: String, confirm: String): CreatePinResult {
        PinPolicy.validate(pin)?.let { return CreatePinResult.Invalid(it) }
        if (pin != confirm) return CreatePinResult.Invalid("The two PINs don't match.")
        val code = withContext(Dispatchers.Default) { c.pinManager.createPin(pin) }
        c.parentSession.unlock()
        return CreatePinResult.Created(code)
    }

    suspend fun unlock(pin: String): VerifyResult {
        val result = withContext(Dispatchers.Default) { c.pinManager.verifyPin(pin) }
        if (result is VerifyResult.Success) c.parentSession.unlock()
        return result
    }

    /** Returns a NEW recovery code on success, or the failure result. */
    suspend fun recover(code: String, newPin: String, confirm: String): Pair<String?, String?> {
        PinPolicy.validate(newPin)?.let { return null to it }
        if (newPin != confirm) return null to "The two PINs don't match."
        val newCode = withContext(Dispatchers.Default) { c.pinManager.resetPinWithRecovery(code, newPin) }
            ?: return null to if (c.pinManager.lockoutRemainingMs() > 0) "Too many attempts. Try again later." else "That recovery code isn't right."
        c.auditRepository.log(AuditType.PIN_RESET_VIA_RECOVERY, "PIN reset with recovery code")
        c.parentSession.unlock()
        return newCode to null
    }

    suspend fun changePin(oldPin: String, newPin: String, confirm: String): String? {
        PinPolicy.validate(newPin)?.let { return it }
        if (newPin != confirm) return "The two PINs don't match."
        return when (val r = withContext(Dispatchers.Default) { c.pinManager.changePin(oldPin, newPin) }) {
            is VerifyResult.Success -> { c.auditRepository.log(AuditType.PIN_CHANGED, "PIN changed"); null }
            is VerifyResult.Locked -> "Too many attempts. Try again in ${r.remainingMs / 1000 + 1}s."
            is VerifyResult.Wrong -> "Current PIN is wrong."
            VerifyResult.NotSet -> "No PIN is set."
        }
    }

    fun lockoutRemainingMs(): Long = c.pinManager.lockoutRemainingMs()
    fun lock() = c.parentSession.lock()
}

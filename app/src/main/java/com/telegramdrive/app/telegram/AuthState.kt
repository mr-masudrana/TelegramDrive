package com.telegramdrive.app.telegram

/** Simplified view of TdApi.AuthorizationState for the UI layer to react to. */
sealed class AuthState {
    data object Loading : AuthState()
    data object WaitPhoneNumber : AuthState()
    data object WaitCode : AuthState()
    data object WaitPassword : AuthState()
    data object Ready : AuthState()
    data class Error(val message: String) : AuthState()
}

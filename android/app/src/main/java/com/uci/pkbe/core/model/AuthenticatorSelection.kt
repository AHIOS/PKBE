package com.uci.pkbe.core.model

import kotlinx.serialization.Serializable

@Serializable
data class AuthenticatorSelection(
    val authenticatorAttachment: String,
    val requireResidentKey: Boolean,
    val residentKey: String,
    val userVerification: String
)
package com.uci.pkbe.core.model

import kotlinx.serialization.Serializable

@Serializable
data class OptionResponse(
    val attestation: String,
    val authenticatorSelection: AuthenticatorSelection,
    val challenge: String,
    val excludeCredentials: List<String>,
    val extensions: Extensions,
    val hints: List<String>,
    val pubKeyCredParams: List<PubKeyCredParam>,
    val rp: Rp,
    val timeout: Int,
    val user: User
)
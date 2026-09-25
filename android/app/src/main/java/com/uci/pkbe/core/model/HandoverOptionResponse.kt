package com.uci.pkbe.core.model

import kotlinx.serialization.Serializable

@Serializable
data class HandoverOptionResponse(
    val allowCredentials: List<AllowCredential>,
    val challenge: String,
    val extensions: Extensions,
    val hints: List<String>,
    val rpId: String,
    val timeout: Int,
    val userVerification: String
)
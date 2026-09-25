package com.uci.pkbe.core.model

import kotlinx.serialization.Serializable

@Serializable
data class LoginResponse(val token: String, val username: String)


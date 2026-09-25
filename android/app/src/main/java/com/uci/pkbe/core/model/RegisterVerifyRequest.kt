package com.uci.pkbe.core.model

import com.google.gson.JsonElement

data class RegisterVerifyRequest(
    val deviceId: String,
    val credential: JsonElement?
)

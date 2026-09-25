package com.uci.pkbe.core.model

import kotlinx.serialization.Serializable

@Serializable
data class AllowCredential(
    val id: String,
    val type: String
)
package com.uci.pkbe.core.model

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val displayName: String,
    val id: String,
    val name: String
)
package com.uci.pkbe.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Rp(
    val id: String,
    val name: String
)
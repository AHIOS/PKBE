package com.uci.pkbe.core.model

import kotlinx.serialization.Serializable

@Serializable
data class PubKeyCredParam(
    val alg: Int,
    val type: String
)
package com.uci.pkbe.viewmodel

import com.uci.pkbe.MeResponse

data class NativeUiState(
    val username: String? = null,
    val token: String? = null,
    val me: MeResponse? = null
)

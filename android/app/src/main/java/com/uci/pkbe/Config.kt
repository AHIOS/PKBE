package com.uci.pkbe

object Config {
    val rpId: String = BuildConfig.PKBE_RP_ID
    val baseUrl: String = BuildConfig.PKBE_BASE_URL.trimEnd('/')
}

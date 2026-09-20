package com.uci.pkbe

import kotlinx.serialization.Serializable

@Serializable
data class LoginResponse(val token: String, val username: String)

@Serializable
data class MeResponse(
    val username: String,
    val thisDeviceId: String,
    val thisDeviceStatus: String,
    val activeDeviceId: String? = null,
    val pendingDeviceId: String? = null,
    val thisDeviceCredentialId: String? = null,
    val activeCredential: CredentialView? = null,
)

@Serializable
data class CredentialView(
    val credentialIdPrefix: String,
    val aaguid: String,
    val backupEligible: Boolean,
    val backupState: Boolean,
)

@Serializable
data class ApiErrorBody(val error: String = "ERROR", val message: String = "Request failed")

class ApiException(val code: String, override val message: String) : Exception("$code: $message")

@Serializable
data class PublicConfig(
    val rpId: String,
    val rpName: String = "",
    val publicBaseUrl: String = "",
    val origins: List<String> = emptyList(),
    val iosBundleId: String = "",
    val aasaApps: List<String> = emptyList(),
    val androidPackageName: String = "",
    val assetLinksConfigured: Boolean = false,
    val requireDeviceBound: Boolean = false,
    val challengeTtlSeconds: Long = 0,
)

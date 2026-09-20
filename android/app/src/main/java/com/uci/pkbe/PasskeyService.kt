package com.uci.pkbe

import android.app.Activity
import android.util.Base64
import android.util.Log
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import org.json.JSONArray
import org.json.JSONObject

class PasskeyService(private val activity: Activity) {
    private val manager = CredentialManager.create(activity)

    suspend fun createPasskey(optionsJson: String): String {
        val requestJson = ApiClient.publicKeyJson(optionsJson)
        Log.i(TAG, "createPasskey rpId=${Config.rpId} jsonLen=${requestJson.length}")
        val request = CreatePublicKeyCredentialRequest(
            requestJson = requestJson,
            preferImmediatelyAvailableCredentials = true,
        )
        val result = manager.createCredential(activity, request)
        val json = (result as? CreatePublicKeyCredentialResponse)?.registrationResponseJson
            ?: result.data.getString(REGISTRATION_BUNDLE_KEY)
            ?: throw ApiException("WEBAUTHN_FAILED", "Empty registration response")
        Log.i(TAG, "createPasskey success bytes=${json.length}")
        return json
    }

    suspend fun assertHandover(optionsJson: String): String {
        val requestJson = ApiClient.publicKeyJson(optionsJson)
        Log.i(TAG, "assertHandover rpId=${Config.rpId} jsonLen=${requestJson.length}")
        val option = GetPublicKeyCredentialOption(requestJson)
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .setPreferImmediatelyAvailableCredentials(false)
            .build()
        val result = manager.getCredential(activity, request)
        val cred = result.credential as? PublicKeyCredential
            ?: throw ApiException("WEBAUTHN_FAILED", "Unexpected credential type ${result.credential::class.java.simpleName}")
        Log.i(TAG, "assertHandover success")
        return cred.authenticationResponseJson
    }

    suspend fun localPasskeyPresence(credentialIdBase64Url: String): Boolean {
        val challenge = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val options = JSONObject()
            .put("challenge", b64url(challenge))
            .put("rpId", Config.rpId)
            .put("timeout", 30_000)
            .put("userVerification", "discouraged")
            .put(
                "allowCredentials",
                JSONArray().put(
                    JSONObject()
                        .put("type", "public-key")
                        .put("id", credentialIdBase64Url),
                ),
            )
        return try {
            val option = GetPublicKeyCredentialOption(options.toString())
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .setPreferImmediatelyAvailableCredentials(true)
                .build()
            val result = manager.getCredential(activity, request)
            val cred = result.credential as? PublicKeyCredential ?: return false
            val id = JSONObject(cred.authenticationResponseJson).optString("id")
            id == credentialIdBase64Url || id.isNotBlank()
        } catch (e: Exception) {
            Log.i(TAG, "localPasskeyPresence absent/failed: ${e.message}")
            false
        }
    }

    private fun b64url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)

    companion object {
        private const val TAG = "PKBE"
        private const val REGISTRATION_BUNDLE_KEY =
            "androidx.credentials.BUNDLE_KEY_REGISTRATION_RESPONSE_JSON"
    }
}

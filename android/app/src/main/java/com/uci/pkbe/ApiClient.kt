package com.uci.pkbe

import android.content.Context
import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ApiClient(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun login(username: String): LoginResponse {
        val body = JSONObject().put("username", username).toString()
        return post("/v1/login", body, authenticated = false)
    }

    suspend fun logout() {
        try {
            send("/v1/logout", "POST", null, authenticated = true)
        } catch (_: Exception) {
            // ignore
        }
        SessionStore.clear(context)
    }

    suspend fun me(): MeResponse = get("/v1/me")

    suspend fun unenroll(): MeResponse {
        val body = JSONObject().put("deviceId", DeviceIdentity.deviceId(context)).toString()
        return post("/v1/unenroll", body, authenticated = true)
    }

    suspend fun registerOptionsJson(): String {
        val body = JSONObject().put("deviceId", DeviceIdentity.deviceId(context)).toString()
        return send("/v1/register/options", "POST", body, authenticated = true)
    }

    suspend fun registerVerify(credentialJson: String): MeResponse {
        val payload =
            """{"deviceId":"${DeviceIdentity.deviceId(context)}","credential":$credentialJson}"""
        return post("/v1/register/verify", payload, authenticated = true)
    }

    suspend fun handoverOptionsJson(): String {
        val body = JSONObject().put("deviceId", DeviceIdentity.deviceId(context)).toString()
        return send("/v1/handover/options", "POST", body, authenticated = true)
    }

    suspend fun handoverVerify(credentialJson: String): MeResponse {
        val payload =
            """{"deviceId":"${DeviceIdentity.deviceId(context)}","credential":$credentialJson}"""
        return post("/v1/handover/verify", payload, authenticated = true)
    }

    suspend fun publicConfig(): PublicConfig = get("/v1/public-config", authenticated = false)

    suspend fun fetchText(path: String): Pair<Int, String> {
        val request = Request.Builder().url(Config.baseUrl + path).get().build()
        http.newCall(request).execute().use { response ->
            return response.code to (response.body?.string().orEmpty())
        }
    }

    private inline fun <reified T> get(path: String, authenticated: Boolean = true): T {
        val data = send(path, "GET", null, authenticated)
        return json.decodeFromString(data)
    }

    private inline fun <reified T> post(path: String, body: String, authenticated: Boolean): T {
        val data = send(path, "POST", body, authenticated)
        return json.decodeFromString(data)
    }

    private fun send(path: String, method: String, body: String?, authenticated: Boolean): String {
        val builder = Request.Builder()
            .url(Config.baseUrl + path)
            .header("Content-Type", "application/json")
            .header("X-Device-Id", DeviceIdentity.deviceId(context))
        if (authenticated) {
            val token = SessionStore.token(context)
            if (!token.isNullOrBlank()) {
                builder.header("Authorization", "Bearer $token")
            }
        }
        val requestBody = body?.toRequestBody("application/json".toMediaType())
        builder.method(method, if (method == "GET") null else requestBody)
        http.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (response.code == 204) return ""
            if (response.code >= 400) {
                val api = runCatching { json.decodeFromString<ApiErrorBody>(text) }.getOrNull()
                throw ApiException(
                    api?.error ?: "HTTP_${response.code}",
                    api?.message ?: text.ifBlank { "Request failed" },
                )
            }
            return text
        }
    }

    companion object {
        fun publicKeyJson(raw: String): String {
            return try {
                val element = Json.parseToJsonElement(raw)
                val obj = element.jsonObject
                obj["publicKey"]?.jsonObject?.toString() ?: raw
            } catch (e: Exception) {
                Log.w("PKBE", "options JSON parse: ${e.message}")
                raw
            }
        }
    }
}

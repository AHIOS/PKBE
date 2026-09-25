package com.uci.pkbe.feature.login.data

import android.content.Context
import com.google.gson.JsonElement
import com.uci.pkbe.DeviceIdentity
import com.uci.pkbe.MeResponse
import com.uci.pkbe.core.model.HandoverOptionResponse
import com.uci.pkbe.core.model.LoginRequest
import com.uci.pkbe.core.model.LoginResponse
import com.uci.pkbe.core.model.OptionResponse
import com.uci.pkbe.core.model.RegisterOptionRequest
import com.uci.pkbe.core.model.RegisterVerifyRequest
import com.uci.pkbe.core.network.NetworkService
import javax.inject.Inject

class LoginRepository @Inject constructor(
    val service: NetworkService
) {

    suspend fun login(username: String): LoginResponse {
        return service.login(LoginRequest(username))
    }

    suspend fun registerOptions(deviceId: String, token: String?): OptionResponse {
        return service.registerOptions(
            deviceId = RegisterOptionRequest(deviceId),
            token = "Bearer $token"
        )
    }

    suspend fun handoverOptions(deviceId: String, token: String?): HandoverOptionResponse {
        return service.handoverOptions(
            deviceId = RegisterOptionRequest(deviceId),
            token = "Bearer $token"
        )
    }

    suspend fun registerVerify(credentialJson: JsonElement?, token: String?, context: Context): MeResponse {
        return service.registerVerify(
            registerVerifyRequest = RegisterVerifyRequest(
                deviceId = DeviceIdentity.deviceId(context),
                credential = credentialJson
            ),
            token = "Bearer $token"
        )
    }

    suspend fun handoverVerify(credentialJson: JsonElement?, token: String?, context: Context): MeResponse {
        return service.handoverVerify(
            registerVerifyRequest = RegisterVerifyRequest(
                deviceId = DeviceIdentity.deviceId(context),
                credential = credentialJson
            ),
            token = "Bearer $token"
        )
    }

    suspend fun getMe(token: String?): MeResponse {
        return service.me("Bearer $token")
    }

}
package com.uci.pkbe.core.network

import com.uci.pkbe.MeResponse
import com.uci.pkbe.core.model.HandoverOptionResponse
import com.uci.pkbe.core.model.LoginRequest
import com.uci.pkbe.core.model.LoginResponse
import com.uci.pkbe.core.model.OptionResponse
import com.uci.pkbe.core.model.RegisterOptionRequest
import com.uci.pkbe.core.model.RegisterVerifyRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface NetworkService {

    @POST("/v1/login")
    suspend fun login(@Body username: LoginRequest): LoginResponse

    @POST("/v1/register/options")
    suspend fun registerOptions(
        @Body deviceId: RegisterOptionRequest,
        @Header("Authorization") token: String?
    ): OptionResponse

    @POST("/v1/handover/options")
    suspend fun handoverOptions(
        @Body deviceId: RegisterOptionRequest,
        @Header("Authorization") token: String?
    ): HandoverOptionResponse

    @POST("/v1/register/verify")
    suspend fun registerVerify(
        @Body registerVerifyRequest: RegisterVerifyRequest,
        @Header("Authorization") token: String?
    ): MeResponse

    @POST("/v1/handover/verify")
    suspend fun handoverVerify(
        @Body registerVerifyRequest: RegisterVerifyRequest,
        @Header("Authorization") token: String?
    ): MeResponse

    @GET("/v1/me")
    suspend fun me(
        @Header("Authorization") token: String?
    ): MeResponse

}
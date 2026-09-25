package com.uci.pkbe.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import com.uci.pkbe.SessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.uci.pkbe.DeviceIdentity
import com.uci.pkbe.PasskeyService
import com.uci.pkbe.feature.login.data.LoginRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class NativeViewModel @Inject constructor(
    val loginRepository: LoginRepository
): ViewModel() {

    private val _uiState = MutableStateFlow(NativeUiState())
    val uiState = _uiState.asStateFlow()

    fun login(username: String, context: Context) {
        viewModelScope.launch {
            val res = loginRepository.login(username.trim().lowercase())
            SessionStore.save(context, res.token, res.username)
            _uiState.update { state ->
                state.copy(
                    username = res.username,
                    token = res.token
                )
            }
        }
    }

    fun enrollDevice(context: Context, passkeys: PasskeyService) {
        viewModelScope.launch {
            val options = loginRepository.registerOptions(deviceId = DeviceIdentity.deviceId(context), token = uiState.value.token)
            val credential = passkeys.createPasskey(Gson().toJson(options))
            _uiState.update { state ->
                state.copy(
                    me = loginRepository.registerVerify(
                        credentialJson = JsonParser.parseString(credential),
                        token = uiState.value.token,
                        context = context
                    )
                )
            }
        }
    }

    fun handoverDevice(context: Context, passkeys: PasskeyService) {
        viewModelScope.launch {
            val assertionOptions = loginRepository.handoverOptions(deviceId = DeviceIdentity.deviceId(context), token = uiState.value.token)
            Log.i("MARK", "assetionOptions: $assertionOptions")
            val assertion = passkeys.assertHandover(Gson().toJson(assertionOptions))
            _uiState.update { state ->
                state.copy(
                    me = loginRepository.handoverVerify(
                        credentialJson = JsonParser.parseString(assertion),
                        token = uiState.value.token,
                        context = context
                    )
                )
            }
        }
    }

    fun getMe() {
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    me = loginRepository.getMe(uiState.value.token)
                )
            }
        }
    }

}
package com.bma.android.setup

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.bma.android.api.ApiClient
import com.bma.android.models.PairingData
import com.bma.android.models.Song
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SetupViewModel(application: Application) : AndroidViewModel(application) {
    private val _setupState = MutableLiveData<SetupState>()
    val setupState: LiveData<SetupState> = _setupState

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _loadingStatus = MutableLiveData<LoadingStatus>()
    val loadingStatus: LiveData<LoadingStatus> = _loadingStatus

    init {
        _setupState.value = SetupState.WELCOME
    }

    fun moveToNextState() {
        val currentState = _setupState.value ?: return
        _setupState.value = when (currentState) {
            SetupState.WELCOME -> SetupState.TAILSCALE_CHECK
            SetupState.TAILSCALE_CHECK -> SetupState.QR_SCAN
            SetupState.QR_SCAN -> SetupState.LOADING
            SetupState.LOADING -> SetupState.COMPLETE
            SetupState.COMPLETE -> SetupState.COMPLETE
        }
    }

    suspend fun processQRCode(qrContent: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // Parse QR code JSON
            val pairingData = Gson().fromJson(qrContent, PairingData::class.java)

            // Update API client
            ApiClient.setServerUrl(pairingData.serverUrl)
            ApiClient.setAuthToken(pairingData.token)

            // Save to preferences
            getApplication<Application>().getSharedPreferences("BMA", Context.MODE_PRIVATE)
                .edit()
                .putString("server_url", pairingData.serverUrl)
                .putString("auth_token", pairingData.token)
                .putString("token_expires_at", pairingData.expiresAt)
                .apply()

            true
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                setError("Invalid QR code format: ${e.message}")
            }
            false
        }
    }

    suspend fun loadLibrary(): Boolean = withContext(Dispatchers.IO) {
        try {
            updateLoadingStatus("Checking server connection...")
            
            // Check server health
            ApiClient.api.checkHealth()
            
            updateLoadingStatus("Fetching your music collection...")
            
            // Get songs list
            val songs = ApiClient.api.getSongs(ApiClient.getAuthHeader()!!)
            
            // Update loading status with song count
            updateLoadingStatus("Found ${songs.size} songs", songs.size)
            
            // Small delay to show the count
            kotlinx.coroutines.delay(1500)
            
            true
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                setError("Failed to load library: ${e.message}")
            }
            false
        }
    }

    private suspend fun updateLoadingStatus(message: String, count: Int = 0) {
        withContext(Dispatchers.Main) {
            _loadingStatus.value = LoadingStatus(message, count)
        }
    }

    fun setError(message: String?) {
        _error.value = message
    }

    fun clearError() {
        _error.value = null
    }
}

enum class SetupState {
    WELCOME,
    TAILSCALE_CHECK,
    QR_SCAN,
    LOADING,
    COMPLETE
}

data class LoadingStatus(
    val message: String,
    val count: Int = 0
) 
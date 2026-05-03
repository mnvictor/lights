package com.lifxcontrol.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lifxcontrol.data.api.LifxApiClient
import com.lifxcontrol.data.local.PreferencesManager
import com.lifxcontrol.data.model.BatchStatesRequest
import com.lifxcontrol.data.model.IndividualLightState
import com.lifxcontrol.data.model.Light
import com.lifxcontrol.data.model.LightLocation
import com.lifxcontrol.data.model.SavedLightState
import com.lifxcontrol.data.model.SetStateRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class AppScreen { HOME_SELECTION, CONTROL }

data class UiState(
    val apiToken: String = "",
    val locations: List<LightLocation> = emptyList(),
    val selectedLocationId: String? = null,
    val selectedLocationName: String? = null,
    val lights: List<Light> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isDiscoMode: Boolean = false,
    val anyLightsOn: Boolean = false,
    val currentScreen: AppScreen = AppScreen.HOME_SELECTION
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesManager(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var discoJob: Job? = null
    private var savedLightStates: List<SavedLightState> = emptyList()

    init {
        viewModelScope.launch {
            combine(
                prefs.apiToken,
                prefs.selectedLocationId,
                prefs.selectedLocationName
            ) { token, locationId, locationName ->
                Triple(token, locationId, locationName)
            }.collect { (token, locationId, locationName) ->
                val hasToken = !token.isNullOrBlank()
                val hasLocation = !locationId.isNullOrBlank()

                _uiState.update {
                    it.copy(
                        apiToken = token ?: "",
                        selectedLocationId = if (hasLocation) locationId else null,
                        selectedLocationName = if (hasLocation) locationName else null,
                        currentScreen = if (hasToken && hasLocation) AppScreen.CONTROL
                                        else AppScreen.HOME_SELECTION
                    )
                }

                if (hasToken) {
                    loadLocations(token!!)
                    if (hasLocation) {
                        loadLights(token, locationId!!)
                    }
                }
            }
        }
    }

    fun updateApiToken(token: String) {
        _uiState.update { it.copy(apiToken = token, error = null) }
    }

    fun connectWithToken() {
        val token = _uiState.value.apiToken.trim()
        if (token.isBlank()) return
        viewModelScope.launch {
            prefs.saveApiToken(token)
            loadLocations(token)
        }
    }

    private suspend fun loadLocations(token: String) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        try {
            val api = LifxApiClient.create(token)
            val response = api.getAllLights()
            if (response.isSuccessful) {
                val lights = response.body() ?: emptyList()
                val locations = lights.map { it.location }.distinctBy { it.id }
                _uiState.update { it.copy(locations = locations, isLoading = false) }
            } else {
                val msg = when (response.code()) {
                    401 -> "Invalid API token"
                    else -> "Error ${response.code()}"
                }
                _uiState.update { it.copy(error = msg, isLoading = false) }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "Network error: ${e.message}", isLoading = false) }
        }
    }

    fun selectLocation(locationId: String, locationName: String) {
        viewModelScope.launch {
            prefs.saveSelectedLocation(locationId, locationName)
            _uiState.update {
                it.copy(
                    selectedLocationId = locationId,
                    selectedLocationName = locationName,
                    currentScreen = AppScreen.CONTROL
                )
            }
            loadLights(_uiState.value.apiToken, locationId)
        }
    }

    fun navigateToHomeSelection() {
        if (_uiState.value.isDiscoMode) stopDiscoMode()
        _uiState.update { it.copy(currentScreen = AppScreen.HOME_SELECTION) }
    }

    private suspend fun loadLights(token: String, locationId: String) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        try {
            val api = LifxApiClient.create(token)
            val response = api.getLightsBySelector("location_id:$locationId")
            if (response.isSuccessful) {
                val lights = response.body() ?: emptyList()
                _uiState.update {
                    it.copy(
                        lights = lights,
                        anyLightsOn = lights.any { l -> l.power == "on" },
                        isLoading = false
                    )
                }
            } else {
                _uiState.update { it.copy(error = "Error ${response.code()}", isLoading = false) }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "Network error: ${e.message}", isLoading = false) }
        }
    }

    fun refreshLights() {
        val state = _uiState.value
        if (state.apiToken.isBlank() || state.selectedLocationId == null) return
        viewModelScope.launch {
            loadLights(state.apiToken, state.selectedLocationId)
        }
    }

    fun toggleLights() {
        val state = _uiState.value
        if (state.apiToken.isBlank() || state.selectedLocationId == null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val api = LifxApiClient.create(state.apiToken)
                val targetPower = if (state.anyLightsOn) "off" else "on"
                api.setState(
                    "location_id:${state.selectedLocationId}",
                    SetStateRequest(power = targetPower, duration = 0.5)
                )
                delay(600)
                loadLights(state.apiToken, state.selectedLocationId)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Toggle failed: ${e.message}", isLoading = false) }
            }
        }
    }

    fun startDiscoMode() {
        val state = _uiState.value
        if (state.apiToken.isBlank() || state.selectedLocationId == null) return

        savedLightStates = state.lights.map { light ->
            SavedLightState(
                id = light.id,
                power = light.power,
                hue = light.color.hue,
                saturation = light.color.saturation,
                kelvin = light.color.kelvin,
                brightness = light.brightness
            )
        }

        _uiState.update { it.copy(isDiscoMode = true) }

        discoJob = viewModelScope.launch {
            val api = LifxApiClient.create(state.apiToken)
            val lightIds = state.lights.map { it.id }

            while (isActive) {
                val discoStates = lightIds.map { id ->
                    IndividualLightState(
                        selector = "id:$id",
                        power = "on",
                        color = "hue:${(0..360).random()} saturation:1.0 brightness:1.0",
                        duration = 0.4
                    )
                }
                try {
                    api.setBatchStates(BatchStatesRequest(states = discoStates))
                } catch (_: Exception) { }
                delay(600)
            }
        }
    }

    fun stopDiscoMode() {
        discoJob?.cancel()
        discoJob = null
        _uiState.update { it.copy(isDiscoMode = false) }

        val state = _uiState.value
        if (state.apiToken.isBlank() || savedLightStates.isEmpty()) return

        viewModelScope.launch {
            val api = LifxApiClient.create(state.apiToken)
            val restorationStates = savedLightStates.map { saved ->
                if (saved.power == "off") {
                    IndividualLightState(
                        selector = "id:${saved.id}",
                        power = "off",
                        duration = 1.0
                    )
                } else {
                    IndividualLightState(
                        selector = "id:${saved.id}",
                        power = "on",
                        color = "hue:${saved.hue} saturation:${saved.saturation}" +
                                " brightness:${saved.brightness} kelvin:${saved.kelvin}",
                        duration = 1.0
                    )
                }
            }
            try {
                if (restorationStates.isNotEmpty()) {
                    api.setBatchStates(BatchStatesRequest(states = restorationStates))
                }
            } catch (_: Exception) { }
            delay(1200)
            loadLights(state.apiToken, state.selectedLocationId ?: return@launch)
        }
    }

    override fun onCleared() {
        super.onCleared()
        discoJob?.cancel()
    }
}

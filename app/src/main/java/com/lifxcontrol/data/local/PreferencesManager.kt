package com.lifxcontrol.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "lifx_prefs")

class PreferencesManager(private val context: Context) {

    companion object {
        val API_TOKEN = stringPreferencesKey("api_token")
        val SELECTED_LOCATION_ID = stringPreferencesKey("selected_location_id")
        val SELECTED_LOCATION_NAME = stringPreferencesKey("selected_location_name")
        val LIGHT_POWER_STATES_JSON = stringPreferencesKey("light_power_states_json")
    }

    val apiToken: Flow<String?> = context.dataStore.data.map { it[API_TOKEN] }
    val selectedLocationId: Flow<String?> = context.dataStore.data.map { it[SELECTED_LOCATION_ID] }
    val selectedLocationName: Flow<String?> = context.dataStore.data.map { it[SELECTED_LOCATION_NAME] }
    val lightPowerStatesJson: Flow<String?> = context.dataStore.data.map { it[LIGHT_POWER_STATES_JSON] }

    suspend fun saveApiToken(token: String) {
        context.dataStore.edit { it[API_TOKEN] = token }
    }

    suspend fun saveSelectedLocation(id: String, name: String) {
        context.dataStore.edit {
            it[SELECTED_LOCATION_ID] = id
            it[SELECTED_LOCATION_NAME] = name
        }
    }

    suspend fun saveLightPowerStatesJson(json: String) {
        context.dataStore.edit { it[LIGHT_POWER_STATES_JSON] = json }
    }
}

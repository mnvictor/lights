package com.lifxcontrol.data.model

import com.google.gson.annotations.SerializedName

data class Light(
    val id: String,
    val uuid: String,
    val label: String,
    val connected: Boolean,
    val power: String,
    val color: LightColor,
    val brightness: Double,
    val location: LightLocation,
    val group: LightGroup
)

data class LightColor(
    val hue: Double,
    val saturation: Double,
    val kelvin: Int
)

data class LightLocation(
    val id: String,
    val name: String
)

data class LightGroup(
    val id: String,
    val name: String
)

data class SetStateRequest(
    val power: String? = null,
    val color: String? = null,
    val brightness: Double? = null,
    val duration: Double? = null
)

data class IndividualLightState(
    val selector: String,
    val power: String? = null,
    val color: String? = null,
    val brightness: Double? = null,
    val duration: Double? = null
)

data class BatchStatesRequest(
    val states: List<IndividualLightState>
)

data class StateResponse(
    val results: List<StateResult>
)

data class StateResult(
    val id: String,
    val label: String,
    val status: String
)

data class SavedLightState(
    val id: String,
    val power: String,
    val hue: Double,
    val saturation: Double,
    val kelvin: Int,
    val brightness: Double
)

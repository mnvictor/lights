package com.lifxcontrol.data.api

import com.lifxcontrol.data.model.BatchStatesRequest
import com.lifxcontrol.data.model.Light
import com.lifxcontrol.data.model.SetStateRequest
import com.lifxcontrol.data.model.StateResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface LifxApi {

    @GET("lights/all")
    suspend fun getAllLights(): Response<List<Light>>

    @GET("lights/{selector}")
    suspend fun getLightsBySelector(
        @Path("selector", encoded = true) selector: String
    ): Response<List<Light>>

    @PUT("lights/{selector}/state")
    suspend fun setState(
        @Path("selector", encoded = true) selector: String,
        @Body request: SetStateRequest
    ): Response<StateResponse>

    @PUT("lights/states")
    suspend fun setBatchStates(
        @Body request: BatchStatesRequest
    ): Response<StateResponse>

    @POST("lights/{selector}/toggle")
    suspend fun toggleLights(
        @Path("selector", encoded = true) selector: String
    ): Response<StateResponse>
}

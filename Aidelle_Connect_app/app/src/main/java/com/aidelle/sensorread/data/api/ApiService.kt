package com.aidelle.sensorread.data.api

import com.aidelle.sensorread.data.model.HealthDataBatch
import com.aidelle.sensorread.data.model.ServerStatus
import com.aidelle.sensorread.data.model.SyncResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Retrofit API interface for communicating with the FastAPI backend.
 */
interface ApiService {

    /**
     * Send a batch of health records to the server.
     */
    @POST("/api/health-data")
    suspend fun sendHealthData(@Body batch: HealthDataBatch): Response<SyncResponse>

    /**
     * Check if the server is online and get its status.
     */
    @GET("/")
    suspend fun getServerStatus(): Response<ServerStatus>
}

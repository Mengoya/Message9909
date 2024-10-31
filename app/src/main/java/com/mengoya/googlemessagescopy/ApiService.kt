package com.mengoya.googlemessagescopy

import retrofit2.http.Body
import retrofit2.http.PUT
import retrofit2.http.Query

interface ApiService {
    @PUT("v1/external/customer/card/acquiring/qr/start")
    suspend fun startTransaction(
        @Query("cityId") cityId: Int,
        @Body request: TransactionRequest
    ): TransactionResponse
}

data class TransactionRequest(
    val pan: String,
    val terminal: String
)

data class TransactionResponse(
    val message: String,
    val success: Boolean,
    val result: Result?
)

data class Result(
    val data: Data?
)

data class Data(
    val terminal: Terminal?,
    val sessionId: String?,
    val agentTransactionId: String?,
    val timer: Int?,
    val serverDate: String?
)

data class Terminal(
    val tid: String?,
    val cityId: Int?,
    val conductor: String?,
    val type: String?,
    val route: String?,
    val cost: Int?,
    val owner: String?
)
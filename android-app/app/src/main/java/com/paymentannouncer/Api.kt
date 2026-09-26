package com.paymentannouncer

import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

// ---------- Request / response models ----------

data class RegisterRequest(val name: String, val phone: String, val plate: String?, val password: String)
data class LoginRequest(val phone: String, val password: String)
data class DepositInstructions(val amountEtb: Int, val accounts: Map<String, String>)
data class Driver(
    val id: Int,
    val name: String,
    val phone: String,
    val plate: String?,
    val status: String, // pending_deposit | pending_approval | approved | rejected
    val depositScreenshot: String?,
    val rejectionNote: String?,
)
data class AuthResponse(val token: String, val driver: Driver, val depositInstructions: DepositInstructions?)
data class MeResponse(val driver: Driver, val depositInstructions: DepositInstructions?)
data class PaymentLogRequest(val rawSms: String)
data class LoggedPayment(
    val id: Int,
    val amount: Double,
    val senderName: String,
    val maskedPhone: String?,
    val txnDate: String?,
    val txnId: String?,
)
data class PaymentLogResponse(val payment: LoggedPayment)
data class ErrorResponse(val error: String)

// ---------- Retrofit interface ----------

interface PaymentAnnouncerApi {

    @POST("/api/driver/register")
    suspend fun register(@Body body: RegisterRequest): Response<AuthResponse>

    @POST("/api/driver/login")
    suspend fun login(@Body body: LoginRequest): Response<AuthResponse>

    @GET("/api/driver/me")
    suspend fun me(@Header("Authorization") bearer: String): Response<MeResponse>

    @Multipart
    @POST("/api/driver/deposit")
    suspend fun uploadDeposit(
        @Header("Authorization") bearer: String,
        @Part screenshot: MultipartBody.Part,
    ): Response<MeResponse>

    @POST("/api/payments/log")
    suspend fun logPayment(
        @Header("Authorization") bearer: String,
        @Body body: PaymentLogRequest,
    ): Response<PaymentLogResponse>
}

object ApiClient {
    // BuildConfig.API_BASE_URL is set per build in app/build.gradle.
    // 10.0.2.2 is how the Android emulator reaches your machine's localhost.
    val api: PaymentAnnouncerApi by lazy {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        val client = OkHttpClient.Builder().addInterceptor(logging).build()
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PaymentAnnouncerApi::class.java)
    }
}

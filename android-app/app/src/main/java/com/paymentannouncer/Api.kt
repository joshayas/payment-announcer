package com.paymentannouncer

import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import java.util.concurrent.TimeUnit

data class RegisterRequest(
    val role: String,
    val name: String,
    val phone: String,
    val plate: String?,
    val shopName: String?,
    val password: String,
)
data class LoginRequest(val phone: String, val password: String)
data class DepositInstructions(val amountEtb: Int, val accounts: Map<String, String>)
data class TodayTotal(val date: String, val totalAmount: Double, val count: Int)
data class Driver(
    val id: Int,
    val role: String, // "driver" | "merchant"
    val name: String,
    val phone: String,
    val plate: String?,
    val shopName: String?,
    val status: String,
    val depositScreenshot: String?,
    val rejectionNote: String?,
    val today: TodayTotal?,
)
data class AuthResponse(val token: String, val driver: Driver, val depositInstructions: DepositInstructions?)
data class MeResponse(val driver: Driver, val depositInstructions: DepositInstructions?)
data class UpdateProfileRequest(val name: String?, val plate: String?, val shopName: String?)
data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)
data class OkResponse(val ok: Boolean)
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

interface PaymentAnnouncerApi {

    @POST("/api/driver/register")
    suspend fun register(@Body body: RegisterRequest): Response<AuthResponse>

    @POST("/api/driver/login")
    suspend fun login(@Body body: LoginRequest): Response<AuthResponse>

    @GET("/api/driver/me")
    suspend fun me(@Header("Authorization") bearer: String): Response<MeResponse>

    @PATCH("/api/driver/profile")
    suspend fun updateProfile(
        @Header("Authorization") bearer: String,
        @Body body: UpdateProfileRequest,
    ): Response<MeResponse>

    @POST("/api/driver/change-password")
    suspend fun changePassword(
        @Header("Authorization") bearer: String,
        @Body body: ChangePasswordRequest,
    ): Response<OkResponse>

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

    @GET("/api/driver/payments/today")
    suspend fun todayTotal(@Header("Authorization") bearer: String): Response<TodayTotal>
}

object ApiClient {
    val api: PaymentAnnouncerApi by lazy {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        val client = OkHttpClient.Builder()
            .connectTimeout(90, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PaymentAnnouncerApi::class.java)
    }
}
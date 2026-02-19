"""
Android Retrofit API Service Interface
Add this to your Android QuickFix app project
"""

// File: ApiService.kt (in your Android project)

import retrofit2.Call
import retrofit2.http.*
import com.google.gson.annotations.SerializedName

// Request Models
data class RegisterRequest(
    @SerializedName("full_name")
    val fullName: String,
    val email: String,
    val phone: String,
    val password: String
)

data class VerifyOtpRequest(
    @SerializedName("otp_id")
    val otpId: String,
    @SerializedName("otp_code")
    val otpCode: String
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class ForgotPasswordRequest(
    val email: String
)

data class ResetPasswordRequest(
    @SerializedName("otp_id")
    val otpId: String,
    @SerializedName("otp_code")
    val otpCode: String,
    @SerializedName("new_password")
    val newPassword: String
)

data class ResendOtpRequest(
    @SerializedName("otp_id")
    val otpId: String
)

data class UpdateProfileRequest(
    @SerializedName("full_name")
    val fullName: String? = null,
    val phone: String? = null
)

data class ChangePasswordRequest(
    @SerializedName("current_password")
    val currentPassword: String,
    @SerializedName("new_password")
    val newPassword: String
)

// Response Models
data class ApiResponse<T>(
    val status: String,
    val message: String,
    val data: T? = null
)

data class RegisterResponse(
    val userId: String,
    val email: String,
    val otpId: String,
    @SerializedName("expires_in")
    val expiresIn: Int
)

data class OtpResponse(
    val otpId: String,
    @SerializedName("expires_in")
    val expiresIn: Int
)

data class AuthResponse(
    @SerializedName("access_token")
    val accessToken: String,
    @SerializedName("token_type")
    val tokenType: String,
    val user: UserData
)

data class UserData(
    val id: String,
    @SerializedName("full_name")
    val fullName: String,
    val email: String,
    val phone: String,
    @SerializedName("email_verified")
    val emailVerified: Boolean,
    @SerializedName("created_at")
    val createdAt: String,
    @SerializedName("last_login")
    val lastLogin: String?
)

// API Service Interface
interface ApiService {

    // Authentication Endpoints
    @POST("auth/register")
    fun register(@Body request: RegisterRequest): Call<ApiResponse<RegisterResponse>>

    @POST("auth/verify-otp")
    fun verifyOtp(@Body request: VerifyOtpRequest): Call<ApiResponse<AuthResponse>>

    @POST("auth/resend-otp")
    fun resendOtp(@Body request: ResendOtpRequest): Call<ApiResponse<OtpResponse>>

    @POST("auth/login")
    fun login(@Body request: LoginRequest): Call<ApiResponse<AuthResponse>>

    @POST("auth/forgot-password")
    fun forgotPassword(@Body request: ForgotPasswordRequest): Call<ApiResponse<OtpResponse>>

    @POST("auth/reset-password")
    fun resetPassword(@Body request: ResetPasswordRequest): Call<ApiResponse<String>>

    @POST("auth/validate-token")
    fun validateToken(@Header("Authorization") token: String): Call<ApiResponse<UserData>>

    // User Endpoints
    @GET("user/profile")
    fun getProfile(@Header("Authorization") token: String): Call<ApiResponse<UserData>>

    @PUT("user/update-profile")
    fun updateProfile(
        @Header("Authorization") token: String,
        @Body request: UpdateProfileRequest
    ): Call<ApiResponse<UserData>>

    @POST("user/change-password")
    fun changePassword(
        @Header("Authorization") token: String,
        @Body request: ChangePasswordRequest
    ): Call<ApiResponse<String>>

    @DELETE("user/delete-account")
    fun deleteAccount(@Header("Authorization") token: String): Call<ApiResponse<String>>
}

// Network Configuration (add to your Android project)
// File: NetworkClient.kt

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.google.gson.Gson

object NetworkClient {

    private const val BASE_URL = "http://10.0.2.2:5000/api/" // Use 10.0.2.2 for emulator, actual IP for physical device

    fun getApiService(): ApiService {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create(Gson()))
            .build()

        return retrofit.create(ApiService::class.java)
    }
}

// Token Management (add to your Android project)
// File: TokenManager.kt

import android.content.Context
import android.content.SharedPreferences

class TokenManager(context: Context) {

    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        "quickfix_auth",
        Context.MODE_PRIVATE
    )

    fun saveToken(token: String) {
        sharedPreferences.edit().putString("access_token", token).apply()
    }

    fun getToken(): String? {
        return sharedPreferences.getString("access_token", null)
    }

    fun deleteToken() {
        sharedPreferences.edit().remove("access_token").apply()
    }

    fun hasToken(): Boolean {
        return getToken() != null
    }

    fun getAuthHeader(): String {
        val token = getToken() ?: return ""
        return "Bearer $token"
    }

    fun saveUserData(user: UserData) {
        sharedPreferences.edit()
            .putString("user_id", user.id)
            .putString("user_email", user.email)
            .putString("user_name", user.fullName)
            .apply()
    }

    fun getUserEmail(): String? {
        return sharedPreferences.getString("user_email", null)
    }

    fun clearAll() {
        sharedPreferences.edit().clear().apply()
    }
}

// Example Usage in Activity/Fragment

/*
class RegisterActivity : AppCompatActivity() {

    private val apiService = NetworkClient.getApiService()
    private lateinit var tokenManager: TokenManager

    private fun registerUser() {
        val request = RegisterRequest(
            fullName = "John Doe",
            email = "john@example.com",
            phone = "1234567890",
            password = "password123"
        )

        apiService.register(request).enqueue(object : Callback<ApiResponse<RegisterResponse>> {
            override fun onResponse(
                call: Call<ApiResponse<RegisterResponse>>,
                response: Response<ApiResponse<RegisterResponse>>
            ) {
                if (response.isSuccessful) {
                    val registerResponse = response.body()?.data
                    if (registerResponse != null) {
                        // Save OTP ID and navigate to OTP verification screen
                        navigateToOtpVerification(registerResponse.otpId)
                    }
                } else {
                    showError("Registration failed: " + response.message())
                }
            }

            override fun onFailure(call: Call<ApiResponse<RegisterResponse>>, t: Throwable) {
                showError("Error: " + t.message)
            }
        })
    }

    private fun verifyOtp(otpId: String, otpCode: String) {
        val request = VerifyOtpRequest(otpId, otpCode)

        apiService.verifyOtp(request).enqueue(object : Callback<ApiResponse<AuthResponse>> {
            override fun onResponse(
                call: Call<ApiResponse<AuthResponse>>,
                response: Response<ApiResponse<AuthResponse>>
            ) {
                if (response.isSuccessful) {
                    val authResponse = response.body()?.data
                    if (authResponse != null) {
                        // Save token and user data
                        tokenManager.saveToken(authResponse.accessToken)
                        tokenManager.saveUserData(authResponse.user)
                        navigateToHome()
                    }
                } else {
                    showError("OTP verification failed")
                }
            }

            override fun onFailure(call: Call<ApiResponse<AuthResponse>>, t: Throwable) {
                showError("Error: " + t.message)
            }
        })
    }

    private fun login(email: String, password: String) {
        val request = LoginRequest(email, password)

        apiService.login(request).enqueue(object : Callback<ApiResponse<AuthResponse>> {
            override fun onResponse(
                call: Call<ApiResponse<AuthResponse>>,
                response: Response<ApiResponse<AuthResponse>>
            ) {
                if (response.isSuccessful) {
                    val authResponse = response.body()?.data
                    if (authResponse != null) {
                        tokenManager.saveToken(authResponse.accessToken)
                        tokenManager.saveUserData(authResponse.user)
                        navigateToHome()
                    }
                } else {
                    showError("Invalid email or password")
                }
            }

            override fun onFailure(call: Call<ApiResponse<AuthResponse>>, t: Throwable) {
                showError("Error: " + t.message)
            }
        })
    }

    private fun getProfile() {
        val authHeader = tokenManager.getAuthHeader()
        apiService.getProfile(authHeader).enqueue(object : Callback<ApiResponse<UserData>> {
            override fun onResponse(
                call: Call<ApiResponse<UserData>>,
                response: Response<ApiResponse<UserData>>
            ) {
                if (response.isSuccessful) {
                    val user = response.body()?.data
                    // Update UI with user data
                }
            }

            override fun onFailure(call: Call<ApiResponse<UserData>>, t: Throwable) {
                showError("Failed to load profile")
            }
        })
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun navigateToHome() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun navigateToOtpVerification(otpId: String) {
        val intent = Intent(this, OtpVerificationActivity::class.java)
        intent.putExtra("otp_id", otpId)
        startActivity(intent)
    }
}
*/


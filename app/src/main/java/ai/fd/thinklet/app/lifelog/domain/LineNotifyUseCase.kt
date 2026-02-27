package ai.fd.thinklet.app.lifelog.domain

import ai.fd.thinklet.app.lifelog.BuildConfig
import android.util.Log
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject

@Serializable
private data class LinePushRequest(
    val to: String,
    val messages: List<MessageObject>
)

@Serializable
private data class MessageObject(
    val type: String,
    val text: String
)

class LineNotifyUseCase @Inject constructor() {
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30000
        }
    }

    suspend operator fun invoke(message: String): Result<Unit> {
        val channelAccessToken = BuildConfig.LINE_CHANNEL_ACCESS_TOKEN
        val userId = BuildConfig.LINE_USER_ID

        if (channelAccessToken.isEmpty() || userId.isEmpty()) {
            Log.w(TAG, "LINE credentials are not set in BuildConfig.")
            return Result.failure(Exception("LINE credentials are not set."))
        }

        return try {
            // トークンの先頭数文字をログに出して、正しく読み込まれているか確認（デバッグ用）
            Log.d(TAG, "Using token starting with: ${channelAccessToken.take(10)}...")
            
            val response = client.post("https://api.line.me/v2/bot/message/push") {
                header(HttpHeaders.Authorization, "Bearer $channelAccessToken")
                contentType(ContentType.Application.Json)
                setBody(LinePushRequest(
                    to = userId,
                    messages = listOf(MessageObject(type = "text", text = message))
                ))
            }

            if (response.status.isSuccess()) {
                Log.i(TAG, "Successfully sent LINE message.")
                Result.success(Unit)
            } else {
                val errorBody = response.bodyAsText()
                Log.e(TAG, "Failed to send LINE message: ${response.status}. Body: $errorBody")
                Result.failure(Exception("HTTP error: ${response.status}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send LINE message.", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "LineNotifyUseCase"
    }
}

package ai.fd.thinklet.app.lifelog.domain

import ai.fd.thinklet.app.lifelog.BuildConfig
import android.util.Log
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
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
    val type: String = "text",
    val text: String
)

class LineNotifyUseCase @Inject constructor() {
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
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
            val response = client.post("https://api.line.me/v2/bot/message/push") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer \$channelAccessToken")
                    contentType(ContentType.Application.Json)
                }
                setBody(LinePushRequest(
                    to = userId,
                    messages = listOf(MessageObject(text = message))
                ))
            }

            if (response.status.isSuccess()) {
                Log.i(TAG, "Successfully sent LINE message.")
                Result.success(Unit)
            } else {
                Log.e(TAG, "Failed to send LINE message: \${response.status}")
                Result.failure(Exception("HTTP error: \${response.status}"))
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

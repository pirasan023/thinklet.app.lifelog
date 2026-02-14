package ai.fd.thinklet.app.lifelog.domain

import ai.fd.thinklet.app.lifelog.BuildConfig
import android.util.Log
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject

@Serializable
data class SpreadsheetLogRequest(
    val timestamp: String,
    val analysis: String,
    val imagePath: String
)

class SpreadsheetLogUseCase @Inject constructor() {
    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30000
        }
        followRedirects = true
    }

    suspend operator fun invoke(timestamp: String, analysis: String, imagePath: String): Result<Unit> {
        val url = BuildConfig.SPREADSHEET_WEBHOOK_URL
        if (url.isEmpty()) {
            Log.w(TAG, "Spreadsheet webhook URL is empty")
            return Result.success(Unit) // Skip if not configured
        }

        return try {
            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(SpreadsheetLogRequest(timestamp, analysis, imagePath))
            }

            if (response.status.isSuccess()) {
                Log.i(TAG, "Successfully logged to spreadsheet")
                Result.success(Unit)
            } else {
                Log.e(TAG, "Failed to log to spreadsheet: ${response.status}")
                Result.failure(Exception("HTTP error: ${response.status}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Spreadsheet logging failed", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "SpreadsheetLogUseCase"
    }
}

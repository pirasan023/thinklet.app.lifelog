package ai.fd.thinklet.app.lifelog.domain

import ai.fd.thinklet.app.lifelog.BuildConfig
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * MacのFlaskサーバーからスクリーンショットを取得するUseCase。
 * Macが近くにない（サーバーに到達できない）場合はnullを返す。
 */
class MacScreenshotUseCase @Inject constructor() {

    private val client = HttpClient(Android) {
        install(HttpTimeout) {
            requestTimeoutMillis = 5000   // 5秒でタイムアウト（Macが見つからない場合に早く諦める）
            connectTimeoutMillis = 3000
        }
    }

    /**
     * Macのスクリーンショットを取得する。
     * @return Bitmap if Mac is reachable, null otherwise
     */
    suspend operator fun invoke(): Bitmap? = withContext(Dispatchers.IO) {
        val url = BuildConfig.MAC_SCREENSHOT_URL
        if (url.isEmpty()) {
            Log.d(TAG, "Mac screenshot URL is not configured, skipping")
            return@withContext null
        }

        try {
            val response = client.get(url)
            if (response.status.value in 200..299) {
                val bytes = response.readBytes()
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bitmap != null) {
                    Log.i(TAG, "Mac screenshot fetched successfully (${bitmap.width}x${bitmap.height})")
                } else {
                    Log.w(TAG, "Failed to decode Mac screenshot bytes")
                }
                bitmap
            } else {
                Log.w(TAG, "Mac screenshot server returned: ${response.status}")
                null
            }
        } catch (e: Exception) {
            Log.d(TAG, "Mac not reachable: ${e.message}")
            null
        }
    }

    companion object {
        private const val TAG = "MacScreenshotUseCase"
    }
}

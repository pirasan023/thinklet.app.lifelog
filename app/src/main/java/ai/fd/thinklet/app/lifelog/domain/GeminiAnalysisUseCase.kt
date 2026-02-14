package ai.fd.thinklet.app.lifelog.domain

import ai.fd.thinklet.app.lifelog.BuildConfig
import android.graphics.BitmapFactory
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class GeminiAnalysisUseCase @Inject constructor() {
    private val model by lazy {
        GenerativeModel(
            modelName = "gemini-2.0-flash",
            apiKey = BuildConfig.GEMINI_API_KEY
        )
    }

    suspend operator fun invoke(imagePath: String, timestamp: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val file = File(imagePath)
            if (!file.exists()) return@withContext Result.failure(Exception("File not found: $imagePath"))

            val bitmap = BitmapFactory.decodeFile(imagePath) ?: return@withContext Result.failure(Exception("Failed to decode bitmap"))
            
            val response = model.generateContent(
                content {
                    image(bitmap)
                    text(
                        """
                        撮影日時: $timestamp
                        
                        thinkletで撮影したこの画像に写っている状況をできるだけ詳しく説明してください。
                        - 出力内容
                            - ユーザーはthinkletを装着しているか
                            - ユーザーは何をしているか、できるだけ詳細に説明
                            - もしpcを使用しているのであれば、何のソフトを開いてどのような作業をしているのかを説明し、遊んでいるか、ちゃんと作業をしているかを判断してください。
                            - もしスマホを使用しているのであれば、何のアプリを開いていて、何をしているのか、遊んでいるのかを判断してください。
                        """.trimIndent()
                    )
                }
            )

            val text = response.text
            if (text != null) {
                Result.success(text)
            } else {
                Result.failure(Exception("Gemini returned empty response"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gemini analysis failed", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "GeminiAnalysisUseCase"
    }
}

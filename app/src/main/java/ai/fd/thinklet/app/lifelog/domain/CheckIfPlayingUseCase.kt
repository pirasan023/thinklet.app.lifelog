package ai.fd.thinklet.app.lifelog.domain

import ai.fd.thinklet.app.lifelog.BuildConfig
import android.os.Environment
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class CheckIfPlayingUseCase @Inject constructor(
    private val lineNotifyUseCase: LineNotifyUseCase
) {
    // Geminiモデルを初期化
    private val model by lazy {
        GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = BuildConfig.GEMINI_API_KEY
        )
    }

    // ログが保存されているディレクトリ
    private val logDirectory =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "lifelog")

    suspend operator fun invoke() = withContext(Dispatchers.IO) {
        try {
            // 1. 最新10件のログ内容を取得
            val logContent = getLatestLogContent(10)

            if (logContent.isEmpty()) {
                Log.i(TAG, "No log files found to analyze.")
                return@withContext
            }

            // 2. Geminiに判定を依頼
            val prompt = buildPrompt(logContent)
            val response = model.generateContent(content { text(prompt) })

            val responseText = response.text?.trim()
            if (responseText.isNullOrEmpty()) {
                Log.w(TAG, "Gemini returned an empty response.")
                return@withContext
            }

            // 3. 結果に応じてLINE通知
            if (responseText != "__NO__") {
                Log.i(TAG, "User is determined to be playing. Sending LINE notification.")
                lineNotifyUseCase(responseText).onSuccess {
                    Log.i(TAG, "LINE notification sent successfully.")
                }.onFailure {
                    Log.e(TAG, "Failed to send LINE notification.", it)
                }
            } else {
                Log.i(TAG, "User is determined to be working. No notification will be sent.")
            }

        } catch (e: Exception) {
            Log.e(TAG, "An error occurred in CheckIfPlayingUseCase.", e)
        }
    }

    /**
     * ログディレクトリから最新のn件の.txtファイルの内容を読み込み、結合して返す
     */
    private fun getLatestLogContent(count: Int): String {
        if (!logDirectory.exists() || !logDirectory.isDirectory) {
            Log.w(TAG, "Log directory does not exist: ${logDirectory.absolutePath}")
            return ""
        }

        val logFiles = logDirectory.listFiles { _, name -> name.endsWith(".txt") }
            ?.sortedByDescending { it.lastModified() }
            ?.take(count)
            ?: emptyList()

        if (logFiles.isEmpty()) {
            return ""
        }

        // ファイル内容を結合
        return logFiles.joinToString("\n\n---\n\n") { file ->
            try {
                file.readText()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read log file: ${file.name}", e)
                "" // 読み込みに失敗したファイルは空文字として扱う
            }
        }
    }

    /**
     * Geminiに送信するプロンプトを構築する
     */
    private fun buildPrompt(logContent: String): String {
        return """
            # あなたの役割
            あなたは、ユーザーの行動を分析し、生産性向上をサポートするAIアシスタントです。

            # コンテキスト
            以下のテキストは、あるユーザーの直近10分間（1分ごと）の行動を記録したものです。Thinkletという首掛けデバイスで撮影した画像から、AIが状況をテキストで説明しています。

            ---
            $logContent
            ---

            # あなたへの指示
            上記の行動記録を分析し、ユーザーが集中して作業や開発などをしているか、それとも遊んでいる（SNS、動画視聴、休憩など）かを判断してください。
            そして、以下のルールに従って応答を生成してください。

            1.  **もしユーザーが「遊んでいる」と判断した場合**:
                *   ユーザー本人にLINEで通知する、少しユーモラスで気づきを与えるような短いメッセージを**1つだけ**生成してください。
                *   メッセージは、ユーザーを責めるのではなく、「おや、一息ついていますか？」「集中力が切れてきた頃かもしれませんね？」のように、あくまで客観的で、少し皮肉が効いているくらいの温度感が望ましいです。
                *   生成するのはメッセージ本文のみとし、前置きや解説は一切含めないでください。

            2.  **もしユーザーが「集中して作業している」と判断した場合**:
                *   他の文字列は一切含めず、`__NO__` という文字列だけを返してください。

            # 出力例
            (遊んでいると判断した場合)
            > おや、Discordの通知が気になる時間ですか？

            (集中していると判断した場合)
            > __NO__
        """.trimIndent()
    }

    companion object {
        private const val TAG = "CheckIfPlayingUseCase"
    }
}

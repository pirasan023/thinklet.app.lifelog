package ai.fd.thinklet.app.lifelog.domain

import ai.fd.thinklet.app.lifelog.BuildConfig
import android.graphics.Bitmap
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
            modelName = "gemini-2.5-flash",
            apiKey = BuildConfig.GEMINI_API_KEY
        )
    }

    suspend operator fun invoke(
        imagePath: String,
        timestamp: String,
        macScreenshot: Bitmap? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val file = File(imagePath)
            if (!file.exists()) return@withContext Result.failure(Exception("File not found: $imagePath"))

            val thinkletBitmap = BitmapFactory.decodeFile(imagePath) ?: return@withContext Result.failure(Exception("Failed to decode bitmap"))

            val hasMacScreen = macScreenshot != null

            val response = model.generateContent(
                content {
                    image(thinkletBitmap)
                    if (macScreenshot != null) {
                        image(macScreenshot)
                    }
                    text(buildPrompt(timestamp, hasMacScreen))
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

    private fun buildPrompt(timestamp: String, hasMacScreen: Boolean): String {
        val imageDescription = if (hasMacScreen) {
            """
            - 1枚目: Thinklet（首掛け型デバイス）で自動撮影された現実の画像
            - 2枚目: 同時刻にMacのスクリーンショットとして取得されたPC画面の画像
            """.trimIndent()
        } else {
            """
            - Thinklet（首掛け型デバイス）で自動撮影された現実の画像のみです。
            - Macのスクリーンショットは取得できませんでした（Macがオフ、接続失敗、または外出中の可能性）。
            """.trimIndent()
        }

        return """
            撮影日時: $timestamp

            # 画像について
            $imageDescription

            # 説明
            - 1分間隔でthinkletによって自動撮影された画像（と、あればmacのスクリーンショット）に写っている状況をできるだけ詳しく説明してください。
            - これはNeurecorderと言うプロジェクトの一環として行われているもの
                - Neurecorderは、人間のありとあらゆる情報を収集・集約し、AIによる分析を行うプラットフォームのようなものである。
                - DM履歴や日記など、さまざまな情報を他にも集めており、これはそのうちのひとつ。
                - ユーザーが常にThinkletを装着し、1分間隔で撮影された画像とこのプロンプトをGemini2.5-flashで分析する

            # 出力内容
            - ユーザーはthinkletを装着しているか
                - thinkletは首掛け型のデバイスです
            - ユーザーは何をしているか、できるだけ詳細に説明
            - もしpcを使用しており、スクリーンショットを取得できているのならば、何のソフトを開いてどのような作業をしているのかを詳細に説明し、遊んでいるか、ちゃんと作業をしているかを判断してください。
                - よく使用するソフト（この情報に引っ張られすぎないこと）
                    - VScode
                    - Android Studio
                    - Figma
                    - Zen browser
                    - Line
                    - Discord(右のサブディスプレイで常時開いていることが多い)
                    - Obsidian
                    - mattermost
            - もしスマホを使用しているのであれば、何のアプリを開いていて、何をしているのか、遊んでいるのか（SNSをチェックしているかどうかなど）を判断してください。

            # ユーザー大平直輝（Ohira Naoki）の情報
            - 情報は書いてあるが、これに引っ張られすぎないこと。
            - 属性
                - 広島学院高校の学生
                - 2025年度未踏ジュニア採択生（修了済み）（スーパークリエイター）
            - 現在の活動
                - Neurecorder（本プロジェクト）の開発
                - Coolest Projects Japan (CPJ) 運営
                - 生徒会支援（デザイン制作）
                - 物理部・登山部

            # 注意点・その他の指示
            - もしユーザーがpcを使用していない、スマホを使用していない場合は、pcやスマホに関する記述は不要。
                - 具体的には、「pcは画面に映っていないので判断できません」のような記述は絶対にするな。
            - md記法で出力すること
                - *で強調はするな
                - 箇条書きの時はこのプロンプトのように - で記述しろ
            - 分析内容のみを記述すること
                - 一番最初の、「はい、かしこまりました」などは絶対に記述するな。
            - 出力は日本語で行うこと
            - 言い切りの形にすること
        """.trimIndent()
    }

    companion object {
        private const val TAG = "GeminiAnalysisUseCase"
    }
}

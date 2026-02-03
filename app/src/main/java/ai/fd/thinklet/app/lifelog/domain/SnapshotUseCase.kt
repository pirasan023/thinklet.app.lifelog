package ai.fd.thinklet.app.lifelog.domain

import ai.fd.thinklet.library.lifelog.data.file.FileSelectorRepository
import ai.fd.thinklet.library.lifelog.data.snapshot.SnapShotRepository
import ai.fd.thinklet.library.lifelog.data.timer.TimerRepository
import android.graphics.Bitmap
import android.util.Log
import android.util.Size
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import java.io.File
import java.io.FileOutputStream

/**
 * 定期的に写真撮影をして画像として保存するUseCase
 */
class SnapshotUseCase @Inject constructor(
    private val timerRepository: TimerRepository,
    private val snapShotRepository: SnapShotRepository,
    private val fileSelectorRepository: FileSelectorRepository
) {
    suspend operator fun invoke(size: Size, intervalSeconds: Int) {
        coroutineScope {
            snapshot(size, intervalSeconds)
        }
    }

    private suspend fun CoroutineScope.snapshot(size: Size, intervalSeconds: Int) {
        timerRepository.tickerFlow(intervalSeconds.seconds).collect {
            snapShotRepository.takePhoto(size)
                .onSuccess { bitmap ->
                    Log.v(TAG, "snapshot success")
                    // バックグラウンドで保存処理を実行
                    launch {
                        saveBitmapAsJpg(bitmap)
                    }
                }
                .onFailure { Log.e(TAG, "Failed to snapshot", it) }
        }
    }

    private suspend fun saveBitmapAsJpg(bitmap: Bitmap) {
        try {
            val jpgFile = fileSelectorRepository.jpgPath() ?: return
            FileOutputStream(jpgFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos)
            }
            fileSelectorRepository.deploy(jpgFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save bitmap as jpg", e)
        }
    }

    private companion object {
        const val TAG = "SnapshotUseCase"
    }
}

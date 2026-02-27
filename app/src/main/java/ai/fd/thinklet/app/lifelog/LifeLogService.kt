package ai.fd.thinklet.app.lifelog

import ai.fd.thinklet.app.lifelog.data.LifeLogStatusRepository
import ai.fd.thinklet.app.lifelog.domain.CheckIfPlayingUseCase
import ai.fd.thinklet.app.lifelog.domain.GeminiAnalysisUseCase
import ai.fd.thinklet.app.lifelog.domain.MacScreenshotUseCase
import ai.fd.thinklet.app.lifelog.domain.MicRecordUseCase
import ai.fd.thinklet.app.lifelog.domain.SnapshotUseCase
import ai.fd.thinklet.app.lifelog.domain.SpreadsheetLogUseCase
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class LifeLogService : LifecycleService() {

    @Inject
    lateinit var snapshotUseCase: SnapshotUseCase

    @Inject
    lateinit var geminiAnalysisUseCase: GeminiAnalysisUseCase

    @Inject
    lateinit var macScreenshotUseCase: MacScreenshotUseCase

    @Inject
    lateinit var spreadsheetLogUseCase: SpreadsheetLogUseCase

    @Inject
    lateinit var fileSelectorRepository: ai.fd.thinklet.library.lifelog.data.file.FileSelectorRepository

    @Inject
    lateinit var micRecordUseCase: MicRecordUseCase

    @Inject
    lateinit var statusRepository: LifeLogStatusRepository

    @Inject
    lateinit var checkIfPlayingUseCase: CheckIfPlayingUseCase

    private var captureJob: Job? = null
    private var recordJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var analysisCounter = 0

    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_START -> {
                val args = LifeLogArgs.get(intent.extras)
                startLifeLog(args)
            }
            ACTION_STOP -> {
                stopLifeLog()
                stopSelf()
            }
            ACTION_ANALYZE_NOW -> {
                triggerManualAnalysis()
            }
        }
        return START_STICKY
    }

    private var currentArgs: LifeLogArgs? = null

    private fun triggerManualAnalysis() {
        val args = currentArgs ?: return
        lifecycleScope.launch {
            statusRepository.updateStatus { it.copy(isAnalyzing = true) }
            runAnalysisCycle(args, forceCheckPlaying = true)
            statusRepository.updateStatus { it.copy(isAnalyzing = false) }
        }
    }

    private fun startLifeLog(args: LifeLogArgs) {
        if (statusRepository.status.value.isRunning) return
        currentArgs = args
        Log.i(TAG, "Starting LifeLog Service with args: $args")
        persistSettings(args)

        // WakeLock to keep CPU running
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
            newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LifeLog::CaptureWakeLock").apply {
                acquire()
            }
        }

        statusRepository.setRunning(true)
        persistEnabledState(true)

        val notification = createNotification("LifeLog is active")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Snapshot loop
        captureJob = lifecycleScope.launch {
            val intervalMillis = args.intervalSeconds * 1000L

            while (isActive) {
                val nextTime = SystemClock.elapsedRealtime() + intervalMillis

                runAnalysisCycle(args)

                // Countdown loop (every second)
                while (SystemClock.elapsedRealtime() < nextTime && isActive) {
                    val remaining = ((nextTime - SystemClock.elapsedRealtime()) / 1000).toInt()
                    statusRepository.updateStatus {
                        it.copy(
                            nextCaptureTime = System.currentTimeMillis() + (nextTime - SystemClock.elapsedRealtime()),
                            remainingSeconds = remaining
                        )
                    }
                    delay(1000)
                }
            }
        }

        // Recording
        if (args.enabledMic) {
            recordJob = lifecycleScope.launch {
                micRecordUseCase()
            }
        }
    }

    private suspend fun runAnalysisCycle(args: LifeLogArgs, forceCheckPlaying: Boolean = false) {
        Log.d(TAG, "Triggering analysis cycle...")
        val result = snapshotUseCase.takeSingleSnapshot(args.size)

        val lastSuccessTime = System.currentTimeMillis()
        val lastPath = result.getOrNull()
        val error = result.exceptionOrNull()?.message

        if (lastPath == null) {
            Log.e(TAG, "Snapshot failed: $error")
            statusRepository.updateStatus { it.copy(error = error) }
            return
        }

        Log.i(TAG, "Snapshot saved to: $lastPath. Starting analysis...")
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(lastSuccessTime))

        // Macのスクリーンショット取得
        val macScreenshot = try {
            macScreenshotUseCase()
        } catch (e: Exception) {
            Log.d(TAG, "Mac screenshot fetch failed", e)
            null
        }

        if (macScreenshot != null) {
            try {
                val macFile = File(File(lastPath).parentFile, "${File(lastPath).nameWithoutExtension}_mac.png")
                FileOutputStream(macFile).use { fos -> macScreenshot.compress(Bitmap.CompressFormat.PNG, 100, fos) }
                fileSelectorRepository.deploy(macFile)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save Mac screenshot", e)
            }
        }

        geminiAnalysisUseCase(lastPath, timestamp, macScreenshot).onSuccess { text ->
            Log.i(TAG, "Analysis success: $text")

            // Save locally
            try {
                val txtFile = fileSelectorRepository.txtPath(File(lastPath))
                txtFile.writeText(text)
                fileSelectorRepository.deploy(txtFile)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save analysis text", e)
            }

            // Log to Spreadsheet
            spreadsheetLogUseCase(timestamp, text, lastPath)

            // 遊びをチェックする (定期実行または強制実行時)
            analysisCounter++
            if (forceCheckPlaying || analysisCounter % args.analysisInterval == 0) {
                Log.i(TAG, "Checking if user is playing (interval: ${args.analysisInterval})...")
                val playingResult = checkIfPlayingUseCase(text, args.analysisInterval)
                statusRepository.updateStatus {
                    it.copy(
                        lastAnalysisResult = playingResult.getOrNull()
                    )
                }
            }

            statusRepository.updateStatus {
                it.copy(
                    lastCaptureTime = lastSuccessTime,
                    lastCapturePath = lastPath,
                    error = null
                )
            }
        }.onFailure { throwable ->
            Log.e(TAG, "Analysis failed", throwable)
            statusRepository.updateStatus { it.copy(error = throwable.message) }
        }
    }

    private fun stopLifeLog() {
        Log.i(TAG, "Stopping LifeLog Service")
        captureJob?.cancel()
        recordJob?.cancel()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
        statusRepository.setRunning(false)
        statusRepository.setCaptureInfo(null, null, null, 0)
        persistEnabledState(false)
    }

    private fun persistEnabledState(enabled: Boolean) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(PREF_IS_ENABLED, enabled).apply()
    }

    private fun persistSettings(args: LifeLogArgs) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("intervalSeconds", args.intervalSeconds)
            .putInt("analysisInterval", args.analysisInterval)
            .putBoolean("enabledMic", args.enabledMic)
            .apply()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "LifeLog Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String): Notification {
        val stopIntent = Intent(this, LifeLogService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val mainIntent = Intent(this, MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LifeLog")
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(mainPendingIntent)
            .addAction(R.mipmap.ic_launcher, "Stop", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        stopLifeLog()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "LifeLogService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "lifelog_service"

        const val ACTION_START = "ai.fd.thinklet.app.lifelog.ACTION_START"
        const val ACTION_STOP = "ai.fd.thinklet.app.lifelog.ACTION_STOP"
        const val ACTION_ANALYZE_NOW = "ai.fd.thinklet.app.lifelog.ACTION_ANALYZE_NOW"

        const val PREFS_NAME = "lifelog_prefs"
        const val PREF_IS_ENABLED = "is_enabled"

        fun start(context: Context, args: LifeLogArgs) {
            val intent = Intent(context, LifeLogService::class.java).apply {
                action = ACTION_START
                // Reconstruct bundle from args
                putExtra("longSide", args.longSide.toString())
                putExtra("shortSide", args.shortSide.toString())
                putExtra("intervalSeconds", args.intervalSeconds.toString())
                putExtra("analysisInterval", args.analysisInterval.toString())
                putExtra("enabledMic", args.enabledMic.toString())
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, LifeLogService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun analyzeNow(context: Context) {
            val intent = Intent(context, LifeLogService::class.java).apply {
                action = ACTION_ANALYZE_NOW
            }
            context.startService(intent)
        }
    }
}
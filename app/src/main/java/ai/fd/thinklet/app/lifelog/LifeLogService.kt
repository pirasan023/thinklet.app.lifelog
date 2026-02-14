package ai.fd.thinklet.app.lifelog

import ai.fd.thinklet.app.lifelog.data.LifeLogStatusRepository
import ai.fd.thinklet.app.lifelog.domain.GeminiAnalysisUseCase
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
    lateinit var spreadsheetLogUseCase: SpreadsheetLogUseCase

    @Inject
    lateinit var fileSelectorRepository: ai.fd.thinklet.library.lifelog.data.file.FileSelectorRepository

    @Inject
    lateinit var micRecordUseCase: MicRecordUseCase

    @Inject
    lateinit var statusRepository: LifeLogStatusRepository

    private var captureJob: Job? = null
    private var recordJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

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
        }
        return START_STICKY
    }

    private fun startLifeLog(args: LifeLogArgs) {
        if (statusRepository.status.value.isRunning) return

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
                // Trigger snapshot
                Log.d(TAG, "Triggering snapshot...")
                val result = snapshotUseCase.takeSingleSnapshot(args.size)
                
                val lastSuccessTime = System.currentTimeMillis()
                val lastPath = result.getOrNull()
                val error = result.exceptionOrNull()?.message
                
                if (lastPath == null) {
                    Log.e(TAG, "Snapshot failed: $error")
                } else {
                    Log.i(TAG, "Snapshot saved to: $lastPath. Starting analysis...")
                    
                    val currentPath = lastPath
                    val currentTime = lastSuccessTime
                    
                    lifecycleScope.launch {
                        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(currentTime))
                        geminiAnalysisUseCase(currentPath, timestamp).onSuccess { text ->
                            Log.i(TAG, "Analysis success: $text")
                            
                            // Save locally as .txt
                            try {
                                val txtFile = fileSelectorRepository.txtPath(File(currentPath))
                                txtFile.writeText(text)
                                fileSelectorRepository.deploy(txtFile)
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to save analysis text locally", e)
                            }
                            
                            // Log to Spreadsheet
                            spreadsheetLogUseCase(timestamp, text, currentPath).onFailure {
                                Log.e(TAG, "Spreadsheet logging failed inside service", it)
                            }.onSuccess {
                                Log.i(TAG, "Spreadsheet logging success")
                            }
                        }.onFailure {
                            Log.e(TAG, "Analysis failed", it)
                        }
                    }
                }
                
                val nextTime = SystemClock.elapsedRealtime() + intervalMillis
                
                // Countdown loop (every second)
                while (SystemClock.elapsedRealtime() < nextTime && isActive) {
                    val remaining = ((nextTime - SystemClock.elapsedRealtime()) / 1000).toInt()
                    statusRepository.updateStatus { 
                        it.copy(
                            lastCaptureTime = if (lastPath != null) lastSuccessTime else it.lastCaptureTime,
                            lastCapturePath = lastPath ?: it.lastCapturePath,
                            nextCaptureTime = System.currentTimeMillis() + (nextTime - SystemClock.elapsedRealtime()),
                            remainingSeconds = remaining,
                            error = error
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
        
        const val PREFS_NAME = "lifelog_prefs"
        const val PREF_IS_ENABLED = "is_enabled"

        fun start(context: Context, args: LifeLogArgs) {
            val intent = Intent(context, LifeLogService::class.java).apply {
                action = ACTION_START
                // Reconstruct bundle from args
                putExtra("longSide", args.longSide.toString())
                putExtra("shortSide", args.shortSide.toString())
                putExtra("intervalSeconds", args.intervalSeconds.toString())
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
    }
}

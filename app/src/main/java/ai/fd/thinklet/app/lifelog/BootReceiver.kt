package ai.fd.thinklet.app.lifelog

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            
            Log.i("BootReceiver", "Device booted. Checking if LifeLog should start.")
            
            val prefs = context.getSharedPreferences(LifeLogService.PREFS_NAME, Context.MODE_PRIVATE)
            val isEnabled = prefs.getBoolean(LifeLogService.PREF_IS_ENABLED, false)
            
            if (isEnabled) {
                Log.i("BootReceiver", "LifeLog was enabled. Starting service...")
                
                val interval = prefs.getInt("intervalSeconds", 60)
                val mic = prefs.getBoolean("enabledMic", false)
                
                // Use default args with persisted values
                val args = LifeLogArgs.get(null).copy(
                    intervalSeconds = interval,
                    enabledMic = mic
                )
                LifeLogService.start(context, args)
            }
        }
    }
}

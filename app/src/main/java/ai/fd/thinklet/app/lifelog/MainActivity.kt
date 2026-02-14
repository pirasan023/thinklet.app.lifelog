package ai.fd.thinklet.app.lifelog

import ai.fd.thinklet.app.lifelog.ui.theme.LifelogTheme
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            LifelogTheme {
                Scaffold { padding ->
                    Column(
                        modifier = Modifier
                            .padding(padding)
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        LifeLogDashboard()
                    }
                }
            }
        }
    }
}

@Composable
fun LifeLogDashboard(vm: LifeLogViewModel = viewModel()) {
    val context = LocalContext.current
    val status by vm.status.collectAsState()
    
    val prefs = remember { context.getSharedPreferences(LifeLogService.PREFS_NAME, Context.MODE_PRIVATE) }
    var micEnabled by remember { mutableStateOf(prefs.getBoolean("enabledMic", false)) }
    var intervalSeconds by remember { mutableFloatStateOf(prefs.getInt("intervalSeconds", 60).toFloat()) }
    
    val permissions = mutableListOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
            add(Manifest.permission.READ_MEDIA_IMAGES)
            add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            // Permissions granted
        }
    }

    LaunchedEffect(Unit) {
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            launcher.launch(missing.toTypedArray())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "LifeLog Dashboard",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        StatusCard(status)

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Settings", style = MaterialTheme.typography.titleMedium)
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Mic Setting
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (micEnabled) Icons.Filled.Mic else Icons.Filled.MicOff,
                            contentDescription = null,
                            tint = if (micEnabled) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(text = "Audio Recording")
                    }
                    Switch(
                        checked = micEnabled,
                        onCheckedChange = { micEnabled = it },
                        enabled = !status.isRunning
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(16.dp))

                // Interval Setting
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Interval: ${intervalSeconds.roundToInt()} seconds")
                    }
                    Slider(
                        value = intervalSeconds,
                        onValueChange = { intervalSeconds = it },
                        valueRange = 10f..600f,
                        enabled = !status.isRunning
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (!status.isRunning) {
            Button(
                onClick = {
                    val args = LifeLogArgs.get(null).copy(
                        enabledMic = micEnabled,
                        intervalSeconds = intervalSeconds.roundToInt()
                    )
                    LifeLogService.start(context, args)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(text = "START LOGGING", fontWeight = FontWeight.Bold)
            }
        } else {
            Button(
                onClick = { LifeLogService.stop(context) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Filled.Stop, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(text = "STOP LOGGING", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun StatusCard(status: ai.fd.thinklet.app.lifelog.data.LifeLogStatus) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (status.isRunning) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Status: ",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = if (status.isRunning) "ACTIVE" else "INACTIVE",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (status.isRunning) Color(0xFF2E7D32) else Color.Red
                )
            }

            status.error?.let { err ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Error: $err",
                    color = Color.Red,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (status.isRunning) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Last Capture: ${status.lastCaptureTime?.let { timeFormat.format(Date(it)) } ?: "Waiting..."}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "Next Capture in: ",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "${status.remainingSeconds}s",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )

                status.lastCapturePath?.let { path ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Path: $path",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "Last Image:", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    AsyncImage(
                        model = File(path),
                        contentDescription = "Last captured image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(MaterialTheme.shapes.medium),
                        contentScale = ContentScale.Crop
                    )
                }
            } else if (status.lastCapturePath != null) {
                // Show last image even if stopped
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Final Image:", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                AsyncImage(
                    model = File(status.lastCapturePath),
                    contentDescription = "Final captured image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(MaterialTheme.shapes.medium),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

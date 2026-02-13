package ai.fd.thinklet.app.lifelog.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class LifeLogStatus(
    val isRunning: Boolean = false,
    val lastCaptureTime: Long? = null,
    val lastCapturePath: String? = null,
    val nextCaptureTime: Long? = null,
    val remainingSeconds: Int = 0,
    val error: String? = null
)

@Singleton
class LifeLogStatusRepository @Inject constructor() {
    private val _status = MutableStateFlow(LifeLogStatus())
    val status: StateFlow<LifeLogStatus> = _status.asStateFlow()

    fun updateStatus(update: (LifeLogStatus) -> LifeLogStatus) {
        _status.update(update)
    }

    fun setRunning(isRunning: Boolean) {
        _status.update { it.copy(isRunning = isRunning) }
    }

    fun setCaptureInfo(last: Long?, lastPath: String?, next: Long?, remaining: Int) {
        _status.update { 
            it.copy(
                lastCaptureTime = last ?: it.lastCaptureTime, 
                lastCapturePath = lastPath ?: it.lastCapturePath,
                nextCaptureTime = next, 
                remainingSeconds = remaining
            ) 
        }
    }
}

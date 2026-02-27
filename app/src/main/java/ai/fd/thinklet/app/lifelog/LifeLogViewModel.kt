package ai.fd.thinklet.app.lifelog

import ai.fd.thinklet.app.lifelog.data.LifeLogStatus
import ai.fd.thinklet.app.lifelog.data.LifeLogStatusRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class LifeLogViewModel @Inject constructor(
    private val repository: LifeLogStatusRepository
) : ViewModel() {

    val status: StateFlow<LifeLogStatus> = repository.status

    fun triggerManualAnalysis(context: android.content.Context) {
        LifeLogService.analyzeNow(context)
    }
}

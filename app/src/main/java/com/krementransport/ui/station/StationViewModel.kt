package com.krementransport.ui.station

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.krementransport.AppContainer
import com.krementransport.data.repo.PredictionRepository
import com.krementransport.util.poll
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Duration.Companion.seconds

/**
 * One stop's arrivals, polled only while its sheet is open. Scoping the poll to the sheet is the
 * point — it can never outlive the UI that asked for it.
 */
class StationViewModel(private val repository: PredictionRepository) : ViewModel() {

    private val _state = MutableStateFlow(PredictionRepository.Snapshot())
    val state: StateFlow<PredictionRepository.Snapshot> = _state.asStateFlow()

    private var job: Job? = null

    fun open(sid: Int) {
        job?.cancel()
        _state.value = PredictionRepository.Snapshot()
        job = viewModelScope.poll(every = PollInterval) {
            _state.value = repository.load(sid, _state.value)
        }
    }

    fun close() {
        job?.cancel()
        job = null
    }

    override fun onCleared() {
        close()
    }

    companion object {
        /** Matches the backend's own cadence; the sheet is the only thing polling this often. */
        private val PollInterval = 5.seconds

        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    StationViewModel(container.predictions) as T
            }
    }
}

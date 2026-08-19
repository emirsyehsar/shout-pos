package com.emirst.shoutpos

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SpeechViewModel(
    private val speechApi: SpeechAPI = SpeechAPI()
) : ViewModel() {

    private val _speechText = MutableLiveData("")
    val speechText: LiveData<String> = _speechText

    private var tickJob: Job? = null

    /** Button went down: restart the count from zero and tick once per second. */
    fun onPressStart() {
        tickJob?.cancel()
        speechApi.reset()
        _speechText.value = speechApi.counter.toString()
        tickJob = viewModelScope.launch {
            while (isActive) {
                delay(TICK_INTERVAL_MS)
                _speechText.value = speechApi.increment().toString()
            }
        }
    }

    /** Button came up: stop ticking, leave the last value on screen. */
    fun onPressEnd() {
        tickJob?.cancel()
        tickJob = null
    }

    companion object {
        private const val TICK_INTERVAL_MS = 1_000L
    }
}

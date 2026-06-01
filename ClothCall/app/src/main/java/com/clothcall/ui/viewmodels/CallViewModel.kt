package com.clothcall.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.clothcall.api.GeminiApiService
import com.clothcall.utils.AudioRouter
import com.clothcall.utils.PreferencesManager
import com.clothcall.utils.ScanResultHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class CallPhase {
    object Ringing : CallPhase()
    object Speaking : CallPhase()
    object Listening : CallPhase()
    object FetchingDetail : CallPhase()
    data class Dismissed(val warm: Boolean) : CallPhase()
    data class Error(val msg: String) : CallPhase()
}

class CallViewModel(
    private val apiService: GeminiApiService,
    private val prefs: PreferencesManager,
    private val audioRouter: AudioRouter
) : ViewModel() {

    private val _phase = MutableStateFlow<CallPhase>(CallPhase.Ringing)
    val phase: StateFlow<CallPhase> = _phase

    private val _listeningKey = MutableStateFlow(0)
    val listeningKey: StateFlow<Int> = _listeningKey

    val responseText: String get() = ScanResultHolder.response
    val caregiverName: String get() = "ClothCall"

    fun reset() { _phase.value = CallPhase.Ringing }

    fun answer() { transitionToSpeaking() }

    fun onTtsDone() { _phase.value = CallPhase.Listening }

    fun handleVoiceCommand(words: String) {
        val lower = words.lowercase()
        when {
            "yes" in lower || "i'll wear" in lower || "wear it" in lower ->
                _phase.value = CallPhase.Dismissed(warm = true)
            "no" in lower || "i'll change" in lower || "change" in lower ->
                _phase.value = CallPhase.Dismissed(warm = false)
            "again" in lower || "repeat" in lower ->
                transitionToSpeaking()
            "more detail" in lower || "more" in lower || "detail" in lower ->
                fetchMoreDetail()
            "already know" in lower || "already" in lower -> {
                ScanResultHolder.response = "Got it."
                _phase.value = CallPhase.Dismissed(warm = true)
            }
            else -> retryListening()
        }
    }

    fun retryListening() {
        if (_phase.value is CallPhase.Listening) {
            _listeningKey.value++
        } else {
            _phase.value = CallPhase.Listening
        }
    }

    private fun transitionToSpeaking() {
        if (prefs.isOutMode) audioRouter.routeToEarpiece() else audioRouter.routeToSpeaker()
        _phase.value = CallPhase.Speaking
    }

    private fun fetchMoreDetail() {
        _phase.value = CallPhase.FetchingDetail
        viewModelScope.launch {
            val result = apiService.requestMoreDetail(
                apiKey = prefs.apiKey,
                base64Image = ScanResultHolder.base64Image,
                baselineBase64 = ScanResultHolder.baselineBase64,
                firstResponseText = ScanResultHolder.response,
                caregiverName = ScanResultHolder.caregiverName,
                fadeThreshold = ScanResultHolder.fadeThreshold
            )
            result.onSuccess { text ->
                ScanResultHolder.response = text
                transitionToSpeaking()
            }.onFailure { e ->
                _phase.value = CallPhase.Error(e.message ?: "Network error")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioRouter.resetRouting()
    }

    companion object {
        fun factory(api: GeminiApiService, prefs: PreferencesManager): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                    CallViewModel(api, prefs, AudioRouter(app))
                }
            }
    }
}

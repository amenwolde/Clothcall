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
    data class Clarifying(val message: String) : CallPhase()
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
    val caregiverName: String get() = ScanResultHolder.caregiverName ?: "ClothCall"

    fun reset() { _phase.value = CallPhase.Ringing }

    /** Home mode: skip Ringing entirely and go straight to TTS. */
    fun startSpeaking() { transitionToSpeaking() }

    fun answer() { transitionToSpeaking() }

    fun onTtsDone() { _phase.value = CallPhase.Listening }

    fun handleVoiceCommand(words: String) {
        val trimmed = words.trim()
        if (trimmed.length < 2) {
            _phase.value = CallPhase.Clarifying(CLARIFY_MESSAGE)
            return
        }
        viewModelScope.launch {
            val intent = apiService.classifyIntent(
                apiKey = prefs.apiKey,
                rawText = trimmed,
                currentResponse = ScanResultHolder.response
            ).getOrNull()?.trim()?.lowercase()

            when (intent) {
                "yes" -> _phase.value = CallPhase.Dismissed(warm = true)

                "no" -> _phase.value = CallPhase.Dismissed(warm = false)

                "repeat" -> transitionToSpeaking()

                "already_know" -> {
                    val firstSentence = ScanResultHolder.response
                        .split(Regex("(?<=[.!?])\\s+"))
                        .firstOrNull()
                        ?.trim()
                        .orEmpty()
                    if (firstSentence.isNotBlank()) {
                        ScanResultHolder.reportedStains.add(firstSentence.take(60).trim())
                    }
                    _phase.value = CallPhase.Dismissed(warm = true)
                }

                "more_detail" -> fetchMoreDetail(trimmed)

                "question" -> handleFollowUpQuestion(trimmed)

                else -> _phase.value = CallPhase.Clarifying(CLARIFY_MESSAGE)
            }
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

    private fun fetchMoreDetail(followUpQuery: String) {
        _phase.value = CallPhase.FetchingDetail
        viewModelScope.launch {
            val followUpText = followUpQuery.trim().ifBlank {
                "Please provide more specific detail — focus on any areas that were only briefly mentioned."
            }
            val result = apiService.requestMoreDetail(
                apiKey = prefs.apiKey,
                base64Image = ScanResultHolder.base64Image,
                baselineBase64 = ScanResultHolder.baselineBase64,
                followUpText = followUpText,
                firstResponseText = ScanResultHolder.response,
                caregiverName = ScanResultHolder.caregiverName,
                fadeThreshold = ScanResultHolder.fadeThreshold
            )
            result.onSuccess { text ->
                ScanResultHolder.response = text
                ScanResultHolder.conversationHistory.add("user" to followUpText)
                ScanResultHolder.conversationHistory.add("assistant" to text)
                transitionToSpeaking()
            }.onFailure { e ->
                _phase.value = CallPhase.Error(e.message ?: "Network error")
            }
        }
    }

    private fun handleFollowUpQuestion(questionText: String) {
        _phase.value = CallPhase.FetchingDetail
        viewModelScope.launch {
            val result = apiService.requestMoreDetail(
                apiKey = prefs.apiKey,
                base64Image = ScanResultHolder.base64Image,
                baselineBase64 = ScanResultHolder.baselineBase64,
                followUpText = questionText,
                firstResponseText = ScanResultHolder.response,
                caregiverName = ScanResultHolder.caregiverName,
                fadeThreshold = ScanResultHolder.fadeThreshold
            )
            result.onSuccess { text ->
                ScanResultHolder.response = text
                ScanResultHolder.conversationHistory.add("user" to questionText)
                ScanResultHolder.conversationHistory.add("assistant" to text)
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
        private const val CLARIFY_MESSAGE = "Sorry, I did not catch that. Say yes, no, or ask me anything."

        fun factory(api: GeminiApiService, prefs: PreferencesManager): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                    CallViewModel(api, prefs, AudioRouter(app))
                }
            }
    }
}

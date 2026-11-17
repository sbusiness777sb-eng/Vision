package com.example.core.services

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

data class FocusRuntimeSnapshot(
    val isRunning: Boolean = false,
    val remainingSeconds: Int = 0,
    val totalSeconds: Int = 0,
    val isStudyMode: Boolean = false,
    val activeWarningAppName: String? = null,
    val graceRemainingSeconds: Int = 0
)

object AlfredEventBus {
    private val _announcements = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val announcements: SharedFlow<String> = _announcements.asSharedFlow()

    private val _focusRuntime = MutableStateFlow(FocusRuntimeSnapshot())
    val focusRuntime: StateFlow<FocusRuntimeSnapshot> = _focusRuntime.asStateFlow()

    fun emitAnnouncement(message: String) {
        _announcements.tryEmit(message)
    }

    fun updateFocusRuntime(snapshot: FocusRuntimeSnapshot) {
        _focusRuntime.value = snapshot
    }
}

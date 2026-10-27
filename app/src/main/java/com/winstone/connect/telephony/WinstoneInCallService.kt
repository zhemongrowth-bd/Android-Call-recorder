package com.winstone.connect.telephony

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import com.winstone.connect.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SimControlState(
    val available: Boolean = false,
    val muted: Boolean = false,
    val speaker: Boolean = false,
    val speakerSupported: Boolean = false,
    val ringing: Boolean = false,
    val phone: String = "",
)

/** Device-local Telecom binding only. Never writes CRM call state or reports. */
class WinstoneInCallService : InCallService() {
    private val managedCalls = mutableListOf<Call>()
    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) { refresh() }
        override fun onDetailsChanged(call: Call, details: Call.Details) { refresh() }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        instance = this
        managedCalls.add(call)
        call.registerCallback(callback)
        refresh()
        startActivity(Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }

    override fun onCallRemoved(call: Call) {
        call.unregisterCallback(callback)
        managedCalls.remove(call)
        refresh()
        super.onCallRemoved(call)
    }

    @Suppress("DEPRECATION")
    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        refresh()
    }

    @Suppress("DEPRECATION")
    private fun refresh() {
        val call = target()
        val audio = callAudioState
        state.value = SimControlState(
            available = call != null,
            muted = audio?.isMuted ?: false,
            speaker = audio?.route == CallAudioState.ROUTE_SPEAKER,
            speakerSupported = audio?.supportedRouteMask?.and(CallAudioState.ROUTE_SPEAKER) != 0 && audio != null,
            ringing = call?.state == Call.STATE_RINGING,
            phone = call?.details?.handle?.schemeSpecificPart.orEmpty(),
        )
    }

    @Suppress("DEPRECATION")
    private fun target(): Call? = managedCalls.lastOrNull { it.state == Call.STATE_RINGING }
        ?: managedCalls.lastOrNull { it.state != Call.STATE_DISCONNECTED && it.state != Call.STATE_DISCONNECTING }

    override fun onDestroy() {
        managedCalls.forEach { it.unregisterCallback(callback) }
        managedCalls.clear()
        if (instance === this) { instance = null; state.value = SimControlState() }
        super.onDestroy()
    }

    companion object {
        private var instance: WinstoneInCallService? = null
        private val state = MutableStateFlow(SimControlState())
        val controls = state.asStateFlow()

        fun mute(value: Boolean): Boolean = runCatching {
            val service = instance ?: return false
            if (service.target() == null) return false
            service.setMuted(value)
            true
        }.getOrDefault(false)

        @Suppress("DEPRECATION")
        fun speaker(value: Boolean): Boolean = runCatching {
            val service = instance ?: return false
            val supported = service.callAudioState?.supportedRouteMask ?: return false
            val route = if (value) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_WIRED_OR_EARPIECE
            if (supported.and(route) == 0) return false
            service.setAudioRoute(route)
            true
        }.getOrDefault(false)

        fun disconnect(): Boolean = runCatching {
            val call = instance?.target() ?: return false
            call.disconnect()
            true
        }.getOrDefault(false)

        fun answer(): Boolean = runCatching {
            val call = instance?.target() ?: return false
            call.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY)
            true
        }.getOrDefault(false)
    }
}
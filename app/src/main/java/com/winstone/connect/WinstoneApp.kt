package com.winstone.connect

import android.app.Application
import com.winstone.connect.data.AgentSession
import com.winstone.connect.data.sync.CallLogSweepWorker
import com.winstone.connect.data.sync.ReminderWorker
import com.winstone.connect.telephony.CallLogSweeper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WinstoneApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            AgentSession.load(this@WinstoneApp)
            // Catch up on any call the app could not observe live (app killed,
            // call placed from another app) before showing the desk.
            runCatching { CallLogSweeper.sweep(this@WinstoneApp) }
        }
        CallLogSweepWorker.schedule(this)
        ReminderWorker.schedule(this)
    }
}

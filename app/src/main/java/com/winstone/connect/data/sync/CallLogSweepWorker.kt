package com.winstone.connect.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.winstone.connect.data.AgentSession
import com.winstone.connect.telephony.CallLogSweeper
import java.util.concurrent.TimeUnit

/**
 * Runs the call-log reconciliation in the background every 15 minutes, so a call
 * the agent made from WhatsApp or the phone dialer while the app was closed still
 * turns into a lead update and a mandatory report within the same shift.
 */
class CallLogSweepWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Nobody signed in: nothing to attribute the calls to, try again later.
        AgentSession.employeeIdNow(applicationContext) ?: return Result.success()
        CallLogSweeper.sweep(applicationContext)
        return Result.success()
    }

    companion object {
        private const val UNIQUE = "call_log_sweep"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<CallLogSweepWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}

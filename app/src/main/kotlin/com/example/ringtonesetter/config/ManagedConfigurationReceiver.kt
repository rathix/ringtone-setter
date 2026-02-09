package com.example.ringtonesetter.config

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.ringtonesetter.RingtoneSetterApplication
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.example.ringtonesetter.monitor.RingtoneConfigurationWorker
import java.util.concurrent.TimeUnit

class ManagedConfigurationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED) {
            val workRequest = OneTimeWorkRequestBuilder<RingtoneConfigurationWorker>()
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "RingtoneConfigUpdate",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }
    }
}

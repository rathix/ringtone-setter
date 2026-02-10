package com.kennyandries.ringtonesetter.monitor

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kennyandries.ringtonesetter.RingtoneSetterApplication
import com.kennyandries.ringtonesetter.config.ManagedConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RingtoneConfigurationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as RingtoneSetterApplication
        val configReader = app.configReader
        val downloader = app.downloader
        val registrar = app.registrar
        val assigner = app.assigner

        // Check if config is valid
        val configResult = configReader.read()
        if (configResult !is ManagedConfig.Result.Valid) {
            return Result.failure()
        }
        val config = configResult.config

        return withContext(Dispatchers.IO) {
            try {
                // 1. Prepare/Download ringtone
                // Note: We're reusing the logic conceptually similar to ViewModel but adapted for Worker
                val prepared = registrar.prepare(config.ringtoneDisplayName, "audio/mpeg")

                try {
                    prepared.outputStream.use { outputStream ->
                        downloader.download(config.ringtoneSasUrl, outputStream)
                    }

                    // 2. Finalize
                    registrar.finalize(prepared.uri)

                    // 3. Assign
                    assigner.assign(config.contactPhoneNumbers, prepared.uri)

                    Result.success()
                } catch (e: Exception) {
                    registrar.cleanup(prepared.uri)
                    throw e
                }
            } catch (e: Exception) {
                if (runAttemptCount < 3) {
                    Result.retry()
                } else {
                    Result.failure()
                }
            }
        }
    }
}

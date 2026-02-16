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

    companion object {
        const val MAX_RETRY_ATTEMPTS = 3
    }

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
                // 1. Prepare and download ringtone
                val prepared = registrar.prepare(config.ringtoneDisplayName, "audio/mpeg")

                try {
                    val downloadResult = prepared.outputStream.use { outputStream ->
                        downloader.download(config.ringtoneSasUrl, outputStream)
                    }

                    // 2. Finalize the MediaStore entry first, then update MIME type
                    registrar.finalize(prepared.uri)
                    registrar.updateMimeType(prepared.uri, downloadResult.mimeType)

                    // 3. Assign to contacts, retrying only failed numbers
                    var remainingNumbers = config.contactPhoneNumbers
                    var lastResults = assigner.assign(remainingNumbers, prepared.uri)

                    var retries = 0
                    while (retries < MAX_RETRY_ATTEMPTS) {
                        val failedNumbers = lastResults
                            .filter { !it.success }
                            .map { it.phoneNumber }

                        if (failedNumbers.isEmpty()) break

                        retries++
                        remainingNumbers = failedNumbers
                        lastResults = assigner.assign(remainingNumbers, prepared.uri)
                    }

                    val allFailed = lastResults.any { !it.success }
                    if (allFailed) Result.failure() else Result.success()
                } catch (e: Exception) {
                    registrar.cleanup(prepared.uri)
                    throw e
                }
            } catch (e: Exception) {
                if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
                    Result.retry()
                } else {
                    Result.failure()
                }
            }
        }
    }
}

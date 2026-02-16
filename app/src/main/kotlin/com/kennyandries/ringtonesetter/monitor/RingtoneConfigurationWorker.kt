package com.kennyandries.ringtonesetter.monitor

import android.content.Context
import android.util.Log
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
        private const val TAG = "RingtoneConfigWorker"
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
            Log.w(TAG, "Invalid configuration, aborting")
            return Result.failure()
        }
        val config = configResult.config

        return withContext(Dispatchers.IO) {
            try {
                // Check disk space before starting
                registrar.checkAvailableDiskSpace()

                // 1. Prepare and download ringtone
                val prepared = registrar.prepare(config.ringtoneDisplayName, "audio/mpeg")

                try {
                    Log.d(TAG, "Starting download for '${config.ringtoneDisplayName}'")

                    val downloadResult = prepared.outputStream.use { outputStream ->
                        downloader.download(
                            url = config.ringtoneSasUrl,
                            outputStream = outputStream,
                            isCancelled = { isStopped },
                        )
                    }

                    if (isStopped) {
                        Log.w(TAG, "Worker stopped during download, cleaning up")
                        registrar.cleanup(prepared.uri)
                        return@withContext Result.failure()
                    }

                    // 2. Finalize the MediaStore entry first, then update MIME type
                    registrar.finalize(prepared.uri)
                    registrar.updateMimeType(prepared.uri, downloadResult.mimeType)

                    Log.d(TAG, "Download complete: ${downloadResult.bytesWritten} bytes, type=${downloadResult.mimeType}")

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
                        Log.d(TAG, "Retrying ${failedNumbers.size} failed assignments (attempt $retries)")
                        remainingNumbers = failedNumbers
                        lastResults = assigner.assign(remainingNumbers, prepared.uri)
                    }

                    val anyFailed = lastResults.any { !it.success }
                    if (anyFailed) {
                        Log.w(TAG, "Some contact assignments still failed after retries")
                        Result.failure()
                    } else {
                        Log.d(TAG, "All contacts assigned successfully")
                        Result.success()
                    }
                } catch (e: Exception) {
                    registrar.cleanup(prepared.uri)
                    throw e
                }
            } catch (e: Exception) {
                Log.e(TAG, "Worker failed (attempt $runAttemptCount)", e)
                if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
                    Result.retry()
                } else {
                    Result.failure()
                }
            }
        }
    }
}

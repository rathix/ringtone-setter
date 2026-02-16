package com.kennyandries.ringtonesetter.ringtone

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.util.Log
import java.io.IOException
import java.io.OutputStream

class RingtoneRegistrar(private val context: Context) {

    companion object {
        private const val TAG = "RingtoneRegistrar"
        private const val MIN_FREE_SPACE_BYTES = 50L * 1024 * 1024 // 50 MB safety margin
    }

    data class PreparedRingtone(
        val uri: Uri,
        val outputStream: OutputStream,
    )

    /**
     * Checks that the device has enough free disk space for a ringtone download.
     * Throws [IOException] if insufficient space is available.
     */
    fun checkAvailableDiskSpace() {
        val stat = StatFs(Environment.getExternalStorageDirectory().path)
        val availableBytes = stat.availableBytes
        if (availableBytes < MIN_FREE_SPACE_BYTES) {
            throw IOException(
                "Insufficient disk space: ${availableBytes / 1024 / 1024}MB available, " +
                    "need at least ${MIN_FREE_SPACE_BYTES / 1024 / 1024}MB"
            )
        }
    }

    /**
     * Prepares a MediaStore entry for the ringtone.
     * Deletes any existing entry with the same display name first (idempotency).
     * Returns a URI and OutputStream to stream the download into.
     */
    fun prepare(displayName: String, mimeType: String): PreparedRingtone {
        deleteExisting(displayName)

        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
            put(MediaStore.Audio.Media.IS_RINGTONE, true)
            put(MediaStore.Audio.Media.IS_NOTIFICATION, false)
            put(MediaStore.Audio.Media.IS_ALARM, false)
            put(MediaStore.Audio.Media.IS_MUSIC, false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, "Ringtones/")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val uri = context.contentResolver.insert(collection, values)
            ?: throw IllegalStateException("Failed to create MediaStore entry")

        val outputStream = context.contentResolver.openOutputStream(uri)
            ?: throw IllegalStateException("Failed to open OutputStream for $uri")

        return PreparedRingtone(uri, outputStream)
    }

    /**
     * Updates the ringtone MIME type after download when the server reports a more accurate type.
     */
    fun updateMimeType(uri: Uri, mimeType: String) {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
        }
        context.contentResolver.update(uri, values, null, null)
    }

    /**
     * Finalizes the ringtone entry by clearing IS_PENDING (API 29+).
     * Call this after the download stream has been fully written and closed.
     */
    fun finalize(uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.IS_PENDING, 0)
            }
            context.contentResolver.update(uri, values, null, null)
        }
    }

    /**
     * Cleans up a partial MediaStore entry on failure.
     */
    fun cleanup(uri: Uri) {
        try {
            context.contentResolver.delete(uri, null, null)
        } catch (e: Exception) {
            Log.w(TAG, "Best-effort cleanup failed for $uri", e)
        }
    }

    private fun deleteExisting(displayName: String) {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val selection = "${MediaStore.Audio.Media.DISPLAY_NAME} = ?"
        val selectionArgs = arrayOf(displayName)

        context.contentResolver.delete(collection, selection, selectionArgs)
    }
}

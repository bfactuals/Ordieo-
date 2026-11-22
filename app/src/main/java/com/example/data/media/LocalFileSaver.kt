package com.example.data.media

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object LocalFileSaver {

    /**
     * Saves a text payload (.txt, .srt, .json) directly into the device's Downloads folder
     * without requiring broad storage permissions (MediaStore.Downloads on Android 10+).
     */
    suspend fun saveTextToDownloads(
        context: Context,
        fileName: String,
        mimeType: String,
        content: String
    ): Result<String> = withContext(Dispatchers.IO) {
        saveBytesToDownloads(context, fileName, mimeType, content.toByteArray(Charsets.UTF_8))
    }

    /**
     * Copies a local audio file directly into the device's Downloads folder.
     */
    suspend fun saveFileToDownloads(
        context: Context,
        fileName: String,
        mimeType: String,
        sourceFile: File
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (!sourceFile.exists()) error("Audio file no longer exists on device.")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ordieo")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("Could not create Downloads entry")
                resolver.openOutputStream(uri)?.use { out ->
                    sourceFile.inputStream().use { input -> input.copyTo(out) }
                } ?: error("Could not open Downloads output stream")
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                "Saved to Downloads/ordieo/$fileName"
            } else {
                val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "ordieo")
                    .apply { if (!exists()) mkdirs() }
                val dest = File(dir, fileName)
                sourceFile.copyTo(dest, overwrite = true)
                "Saved to Downloads/$fileName"
            }
        }
    }

    private fun saveBytesToDownloads(
        context: Context,
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ): Result<String> = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ordieo")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Could not create Downloads entry")
            resolver.openOutputStream(uri)?.use { out ->
                out.write(bytes)
            } ?: error("Could not open Downloads output stream")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            "Saved to Downloads/ordieo/$fileName"
        } else {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "ordieo")
                .apply { if (!exists()) mkdirs() }
            val dest = File(dir, fileName)
            dest.writeBytes(bytes)
            "Saved to Downloads/$fileName"
        }
    }

    /**
     * Opens the native Android Share Sheet for a single or multiple local audio files via FileProvider.
     */
    fun shareFiles(
        context: Context,
        files: List<File>,
        mimeType: String = "audio/*",
        chooserTitle: String = "Share Audio"
    ): Result<Unit> = runCatching {
        val validFiles = files.filter { it.exists() }
        if (validFiles.isEmpty()) error("No files available to share.")
        val authority = "${context.packageName}.fileprovider"
        val uris = ArrayList<Uri>(validFiles.map { file ->
            FileProvider.getUriForFile(context, authority, file)
        })

        val shareIntent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uris.first())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = mimeType
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

        val chooser = Intent.createChooser(shareIntent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    /**
     * Creates a temporary export file in cacheDir/exports/ and launches the native Share Sheet.
     */
    fun shareTextFile(
        context: Context,
        fileName: String,
        mimeType: String,
        content: String,
        chooserTitle: String
    ): Result<Unit> = runCatching {
        val exportDir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
        val file = File(exportDir, fileName)
        file.writeText(content, Charsets.UTF_8)
        shareFiles(context, listOf(file), mimeType, chooserTitle).getOrThrow()
    }
}
